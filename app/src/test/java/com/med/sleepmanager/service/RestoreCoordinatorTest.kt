package com.med.sleepmanager.service

import com.med.sleepmanager.data.RestoreParticipants
import com.med.sleepmanager.data.RestoreRoute
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreCoordinatorTest {
    private class RecordingHost : ImmediateRestoreHost {
        val calls = mutableListOf<String>()
        override fun restoreRaOfflineProxy(reason: RestoreReason) { calls += "${RaOfflineProxyConnector.id}:$reason" }
        override fun restoreDirectRadios(reason: RestoreReason) { calls += "${RestoreParticipants.DIRECT_RADIO}:$reason" }
    }

    @Test
    fun serviceActionsCoverEveryImmediateParticipant() {
        // create() throws if a registered IMMEDIATE participant has no action.
        RestoreCoordinator.create(RecordingHost()) { false }
        assertEquals(
            RestoreParticipants.withRoute(RestoreRoute.IMMEDIATE).map { it.id }.toSet(),
            RestoreCoordinator.immediateActions(RecordingHost()).keys
        )
    }

    @Test
    fun wakeAndDisableRunTheSameParticipantsInRegistrationOrder() {
        RestoreReason.entries.forEach { reason ->
            val host = RecordingHost()
            RestoreCoordinator.create(host) { false }.restoreImmediate(reason)
            assertEquals(
                listOf(
                    RaOfflineProxyConnector.id,
                    RestoreParticipants.DIRECT_RADIO
                ).map { "$it:$reason" },
                host.calls
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingActionIsRejected() {
        RestoreCoordinator(
            participants = RestoreParticipants.withRoute(RestoreRoute.IMMEDIATE),
            actions = RestoreCoordinator.immediateActions(RecordingHost()) - RaOfflineProxyConnector.id,
            isPending = { false }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unregisteredActionIsRejected() {
        val extra: (RestoreReason) -> Unit = {}
        RestoreCoordinator(
            participants = RestoreParticipants.withRoute(RestoreRoute.IMMEDIATE),
            actions = RestoreCoordinator.immediateActions(RecordingHost()) + ("unknown" to extra),
            isPending = { false }
        )
    }

    @Test
    fun outcomesReportPerParticipantResult() {
        val pending = mutableSetOf(RaOfflineProxyConnector.id, RestoreParticipants.DIRECT_RADIO)
        val host = object : ImmediateRestoreHost {
            override fun restoreRaOfflineProxy(reason: RestoreReason) { pending -= RaOfflineProxyConnector.id }
            override fun restoreDirectRadios(reason: RestoreReason) = Unit
        }
        val outcomes =
            RestoreCoordinator.create(host) { it.id in pending }
                .restoreImmediate(RestoreReason.WAKE)
        assertEquals(RestoreStepOutcome.RESTORED, outcomes[RaOfflineProxyConnector.id])
        assertEquals(RestoreStepOutcome.STILL_PENDING, outcomes[RestoreParticipants.DIRECT_RADIO])

        pending.clear()
        assertEquals(
            RestoreStepOutcome.NOTHING_PENDING,
            RestoreCoordinator.create(host) { it.id in pending }
                .restoreImmediate(RestoreReason.WAKE)[RaOfflineProxyConnector.id]
        )
    }
}
