package com.med.sleepmanager.service

import com.med.sleepmanager.data.RestoreParticipant
import com.med.sleepmanager.data.RestoreParticipants
import com.med.sleepmanager.data.RestoreRoute
import com.med.sleepmanager.integration.KeyMapperController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector

internal enum class RestoreReason(val label: String) {
    WAKE("Wake"),
    DISABLE("Disable")
}

internal enum class RestoreStepOutcome {
    /** Nothing was owned before the step. */
    NOTHING_PENDING,

    /** Owned before the step and no longer pending after it. */
    RESTORED,

    /** Still owned after the step: failed, or confirmation continues asynchronously. */
    STILL_PENDING
}

/**
 * Service operations for the IMMEDIATE restore participants, one per
 * participant. Each keeps its existing per-participant timing and retry
 * behaviour; the coordinator only owns which ones run and in what order.
 */
internal interface ImmediateRestoreHost {
    fun restoreJamesDsp(reason: RestoreReason)
    fun restoreKeyMapper(reason: RestoreReason)
    fun restoreRaOfflineProxy(reason: RestoreReason)
    fun restoreBasicSync(reason: RestoreReason)
    fun restoreDirectRadios(reason: RestoreReason)
}

/**
 * Runs the shared immediate restore step of a real wake and of a disable.
 *
 * Construction fails unless every IMMEDIATE participant registered in
 * [RestoreParticipants] has exactly one action, so a newly registered
 * participant cannot be silently skipped by either path. Network-dependent
 * participants (AFTER_NETWORK), the Helper protocol and owned-state retries
 * keep their dedicated flows in the service.
 */
internal class RestoreCoordinator(
    private val participants: List<RestoreParticipant>,
    private val actions: Map<String, (RestoreReason) -> Unit>,
    private val isPending: (RestoreParticipant) -> Boolean
) {
    init {
        val expected = participants.map { it.id }
        require(expected.toSet().size == expected.size) {
            "Duplicate immediate restore participants: $expected"
        }
        require(actions.keys == expected.toSet()) {
            "Immediate restore actions ${actions.keys.sorted()} " +
                "do not match registered participants ${expected.sorted()}"
        }
    }

    /**
     * Runs every action in registration order. Actions always run, even when
     * nothing is pending, because some also reset per-participant runtime state.
     */
    fun restoreImmediate(reason: RestoreReason): Map<String, RestoreStepOutcome> {
        val outcomes = LinkedHashMap<String, RestoreStepOutcome>()
        participants.forEach { participant ->
            val pendingBefore = isPending(participant)
            actions.getValue(participant.id)(reason)
            outcomes[participant.id] =
                outcome(pendingBefore = pendingBefore, pendingAfter = isPending(participant))
        }
        return outcomes
    }

    companion object {
        fun outcome(pendingBefore: Boolean, pendingAfter: Boolean): RestoreStepOutcome =
            when {
                pendingAfter -> RestoreStepOutcome.STILL_PENDING
                pendingBefore -> RestoreStepOutcome.RESTORED
                else -> RestoreStepOutcome.NOTHING_PENDING
            }

        fun immediateActions(
            host: ImmediateRestoreHost
        ): Map<String, (RestoreReason) -> Unit> =
            mapOf(
                JamesDspConnector.id to host::restoreJamesDsp,
                KeyMapperController.CONNECTOR_ID to host::restoreKeyMapper,
                RaOfflineProxyConnector.id to host::restoreRaOfflineProxy,
                BasicSyncConnector.id to host::restoreBasicSync,
                RestoreParticipants.DIRECT_RADIO to host::restoreDirectRadios
            )

        fun create(
            host: ImmediateRestoreHost,
            isPending: (RestoreParticipant) -> Boolean
        ): RestoreCoordinator =
            RestoreCoordinator(
                participants = RestoreParticipants.withRoute(RestoreRoute.IMMEDIATE),
                actions = immediateActions(host),
                isPending = isPending
            )
    }
}
