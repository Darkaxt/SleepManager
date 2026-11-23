package com.med.sleepmanager.data

import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import com.med.sleepmanager.service.ImmediateRestoreHost
import com.med.sleepmanager.service.RestoreCoordinator
import com.med.sleepmanager.service.RestoreReason
import com.med.sleepmanager.service.RestoreStepOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An upgrade from 0.8.0.13 can leave sleep_cycle_state entries for the removed
 * JamesDSP, Key Mapper and BasicSync connectors. They must not block cycle
 * completion or the restore of the remaining participants.
 */
class RetiredConnectorUpgradeTest {
    /** sleep_cycle_state as 0.8.0.13 persisted it mid-sleep. */
    private val persisted: Map<String, Any?> = mapOf(
        "active" to true,
        "cycle_id" to 1_700_000_000_000L,
        "helper_expected" to false,
        "connector.jamesdsp.changed" to true,
        "connector.jamesdsp.token" to "ON",
        "connector.keymapper.changed" to true,
        "connector.keymapper.token" to "RESUME",
        "connector.basicsync.changed" to true,
        "connector.basicsync.token" to "AUTO_MODE",
        "connector.raofflineproxy.changed" to true,
        "connector.raofflineproxy.token" to RaOfflineProxyConnector.TOKEN_RESTART,
        "connector.tailscale.changed" to true,
        "connector.tailscale.token" to "com.tailscale.ipn"
    )

    @Test
    fun retiredConnectorRecordsAreIgnoredAndRemainingOnesStillPending() {
        assertEquals(
            setOf(RaOfflineProxyConnector.id, TailscaleConnector.id),
            SleepCycleStore.pendingConnectorIds(persisted)
        )
    }

    @Test
    fun onlyRetiredConnectorKeysAreDropped() {
        assertEquals(
            setOf(
                "connector.jamesdsp.changed",
                "connector.jamesdsp.token",
                "connector.keymapper.changed",
                "connector.keymapper.token",
                "connector.basicsync.changed",
                "connector.basicsync.token"
            ),
            SleepCycleStore.retiredConnectorKeys(persisted.keys)
        )
    }

    @Test
    fun cycleWithOnlyRetiredConnectorsCanComplete() {
        val onlyRetired =
            persisted.filterKeys {
                !it.startsWith("connector.raofflineproxy.") &&
                    !it.startsWith("connector.tailscale.")
            }
        val anyConnectorChangeRecorded =
            SleepCycleStore.pendingConnectorIds(onlyRetired).isNotEmpty()
        assertFalse(anyConnectorChangeRecorded)
        assertTrue(
            PendingRestorePolicy.cycleRestored(
                blockingParticipantPending = false,
                anyConnectorChangeRecorded = anyConnectorChangeRecorded
            )
        )
    }

    @Test
    fun remainingImmediateParticipantsRestoreFromTheUpgradedState() {
        val pending = SleepCycleStore.pendingConnectorIds(persisted).toMutableSet()
        val restored = mutableListOf<String>()
        val host = object : ImmediateRestoreHost {
            override fun restoreRaOfflineProxy(reason: RestoreReason) {
                restored += RaOfflineProxyConnector.id
                pending -= RaOfflineProxyConnector.id
            }

            override fun restoreDirectRadios(reason: RestoreReason) {
                restored += RestoreParticipants.DIRECT_RADIO
            }
        }

        val outcomes =
            RestoreCoordinator.create(host) { it.id in pending }
                .restoreImmediate(RestoreReason.WAKE)

        assertEquals(
            listOf(RaOfflineProxyConnector.id, RestoreParticipants.DIRECT_RADIO),
            restored
        )
        assertEquals(RestoreStepOutcome.RESTORED, outcomes[RaOfflineProxyConnector.id])
        assertTrue(outcomes.keys.none { it in RestoreParticipants.RETIRED_CONNECTOR_IDS })
        // Tailscale stays pending for its after-network restore.
        assertEquals(setOf(TailscaleConnector.id), pending)
    }
}
