package com.med.sleepmanager.service

import com.med.sleepmanager.data.RecoveryBehaviour
import com.med.sleepmanager.data.RecoveryEvent
import com.med.sleepmanager.data.RestoreParticipant
import com.med.sleepmanager.data.RestoreParticipants
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector

/**
 * Service operations used by process-death recovery. Every action keeps its
 * own internal guards (for example the RAOfflineProxy restore only runs while
 * its integration is unmanaged); [ServiceRecoveryCoordinator] only decides
 * which actions run, from the behaviours declared in the registry.
 */
internal interface ServiceRecoveryHost {
    fun isRealWake(): Boolean
    fun isEffectivelySleeping(): Boolean
    fun isPending(participantId: String): Boolean

    /** Restores the RAOfflineProxy connector when its integration is no longer managed. */
    fun restoreRaOfflineProxyIfUnmanaged()

    /** Owned Battery Saver: restore on a real wake or re-apply while asleep, per BatterySaverPolicy. */
    fun recoverBatterySaver()

    /** Owned Charging Separation: restore or re-apply for the closed lid, per ChargingSeparationPolicy. */
    fun recoverChargingSeparation()

    fun resumeRaOfflineProxySleepGate()
    fun reapplyDirectRadioSleepState(event: RecoveryEvent)
    fun reapplyHelperSleepWifi(event: RecoveryEvent)

    /** Replays the current screen state through the regular screen-on / screen-off flows. */
    fun replayScreenState()
}

/**
 * Runs service process-death recovery from the participant registry: each
 * participant declares its [RecoveryBehaviour] per [RecoveryEvent] in
 * [RestoreParticipants], and [RestoreParticipants.recoveryOrder] fixes the
 * execution order as data.
 *
 * Construction fails unless the order lists exactly the participants that
 * declare each event, and every declared step has exactly one action, so a
 * participant cannot be recovered on one path and silently skipped on another.
 */
internal class ServiceRecoveryCoordinator(
    private val participants: List<RestoreParticipant>,
    private val order: Map<RecoveryEvent, List<String>>,
    private val actions: Map<RecoveryActionKey, () -> Unit>,
    private val host: ServiceRecoveryHost
) {
    init {
        val byId = participants.associateBy { it.id }
        require(byId.size == participants.size) { "Duplicate recovery participants" }
        val declaredActions = mutableSetOf<RecoveryActionKey>()
        RecoveryEvent.entries.forEach { event ->
            val declared =
                participants.filter { it.recoveryAt(event) != null }.map { it.id }
            if (event == RecoveryEvent.SCREEN_STATE_REPLAY) {
                require(event !in order) { "The screen replay is one shared action and has no order" }
                return@forEach
            }
            val ordered = order[event].orEmpty()
            require(ordered.toSet().size == ordered.size) { "Duplicate ids in $event order: $ordered" }
            require(ordered.toSet() == declared.toSet()) {
                "$event order ${ordered.sorted()} does not match declared participants ${declared.sorted()}"
            }
            val reapply = ordered.indices.filter {
                byId.getValue(ordered[it]).recoveryAt(event)?.behaviour ==
                    RecoveryBehaviour.REAPPLY_WHILE_SLEEPING
            }
            require(reapply.isEmpty() || reapply.last() - reapply.first() == reapply.size - 1) {
                "$event re-apply participants must be contiguous (one first-match group)"
            }
            ordered.forEach { declaredActions += RecoveryActionKey(it, event) }
        }
        require(actions.keys == declaredActions) {
            "Recovery actions ${actions.keys.map { it.toString() }.sorted()} do not match " +
                "declared steps ${declaredActions.map { it.toString() }.sorted()}"
        }
    }

    private val byId = participants.associateBy { it.id }

    /** Runs [event]'s recovery and returns the participant ids whose actions ran. */
    fun run(event: RecoveryEvent): List<String> {
        if (event == RecoveryEvent.SCREEN_STATE_REPLAY) {
            val replayed = participants.filter { it.recoveryAt(event) != null }.map { it.id }
            if (replayed.isNotEmpty()) host.replayScreenState()
            return replayed
        }
        val ran = mutableListOf<String>()
        val sleeping by lazy(LazyThreadSafetyMode.NONE) { host.isEffectivelySleeping() }
        var reapplied = false
        order[event].orEmpty().forEach { id ->
            val behaviour = byId.getValue(id).recoveryAt(event)!!.behaviour
            val runIt =
                when (behaviour) {
                    RecoveryBehaviour.RESTORE_OR_REAPPLY -> true

                    RecoveryBehaviour.RESTORE_ON_REAL_WAKE -> host.isRealWake()

                    RecoveryBehaviour.REAPPLY_WHILE_SLEEPING ->
                        !reapplied && sleeping && host.isPending(id)

                    RecoveryBehaviour.REPLAY_SCREEN_STATE -> false
                }
            if (runIt) {
                actions.getValue(RecoveryActionKey(id, event))()
                if (behaviour == RecoveryBehaviour.REAPPLY_WHILE_SLEEPING) reapplied = true
                ran += id
            }
        }
        return ran
    }

    companion object {
        fun actions(host: ServiceRecoveryHost): Map<RecoveryActionKey, () -> Unit> =
            mapOf(
                RecoveryActionKey(RaOfflineProxyConnector.id, RecoveryEvent.CREATE) to
                    host::restoreRaOfflineProxyIfUnmanaged,
                RecoveryActionKey(RestoreParticipants.BATTERY_SAVER, RecoveryEvent.CREATE_ENABLED) to
                    host::recoverBatterySaver,
                RecoveryActionKey(RestoreParticipants.CHARGING_SEPARATION, RecoveryEvent.CREATE_ENABLED) to
                    host::recoverChargingSeparation,
                RecoveryActionKey(RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE, RecoveryEvent.CREATE_ENABLED) to
                    host::resumeRaOfflineProxySleepGate,
                RecoveryActionKey(RestoreParticipants.DIRECT_RADIO, RecoveryEvent.CREATE_ENABLED) to
                    { host.reapplyDirectRadioSleepState(RecoveryEvent.CREATE_ENABLED) },
                RecoveryActionKey(RestoreParticipants.HELPER, RecoveryEvent.CREATE_ENABLED) to
                    { host.reapplyHelperSleepWifi(RecoveryEvent.CREATE_ENABLED) },
                RecoveryActionKey(RestoreParticipants.DIRECT_RADIO, RecoveryEvent.DESTROY_ENABLED) to
                    { host.reapplyDirectRadioSleepState(RecoveryEvent.DESTROY_ENABLED) },
                RecoveryActionKey(RestoreParticipants.HELPER, RecoveryEvent.DESTROY_ENABLED) to
                    { host.reapplyHelperSleepWifi(RecoveryEvent.DESTROY_ENABLED) }
            )

        fun create(host: ServiceRecoveryHost): ServiceRecoveryCoordinator =
            ServiceRecoveryCoordinator(
                participants = RestoreParticipants.all,
                order = RestoreParticipants.recoveryOrder,
                actions = actions(host),
                host = host
            )
    }
}

internal data class RecoveryActionKey(val participantId: String, val event: RecoveryEvent) {
    override fun toString(): String = "$participantId@$event"
}
