package com.med.sleepmanager.service

import com.med.sleepmanager.data.RestoreParticipants
import com.med.sleepmanager.data.RestoreRoute
import com.med.sleepmanager.integration.KeyMapperController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreCoordinatorTest {
    private class RecordingHost : ImmediateRestoreHost {
        val calls = mutableListOf<String>()
        override fun restoreJamesDsp(reason: RestoreReason) { calls += "${JamesDspConnector.id}:$reason" }
        override fun restoreKeyMapper(reason: RestoreReason) { calls += "${KeyMapperController.CONNECTOR_ID}:$reason" }
        override fun restoreRaOfflineProxy(reason: RestoreReason) { calls += "${RaOfflineProxyConnector.id}:$reason" }
        override fun restoreBasicSync(reason: RestoreReason) { calls += "${BasicSyncConnector.id}:$reason" }
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
                    JamesDspConnector.id,
                    KeyMapperController.CONNECTOR_ID,
                    RaOfflineProxyConnector.id,
                    BasicSyncConnector.id,
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
            actions = RestoreCoordinator.immediateActions(RecordingHost()) - BasicSyncConnector.id,
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
        val pending = mutableSetOf(JamesDspConnector.id, BasicSyncConnector.id)
        val host = object : ImmediateRestoreHost {
            override fun restoreJamesDsp(reason: RestoreReason) { pending -= JamesDspConnector.id }
            override fun restoreKeyMapper(reason: RestoreReason) = Unit
            override fun restoreRaOfflineProxy(reason: RestoreReason) = Unit
            override fun restoreBasicSync(reason: RestoreReason) = Unit
            override fun restoreDirectRadios(reason: RestoreReason) = Unit
        }
        val outcomes =
            RestoreCoordinator.create(host) { it.id in pending }
                .restoreImmediate(RestoreReason.WAKE)
        assertEquals(RestoreStepOutcome.RESTORED, outcomes[JamesDspConnector.id])
        assertEquals(RestoreStepOutcome.STILL_PENDING, outcomes[BasicSyncConnector.id])
        assertEquals(
            RestoreStepOutcome.NOTHING_PENDING,
            outcomes[KeyMapperController.CONNECTOR_ID]
        )
    }
}
