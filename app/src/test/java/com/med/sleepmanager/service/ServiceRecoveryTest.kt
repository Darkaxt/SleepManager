package com.med.sleepmanager.service

import com.med.sleepmanager.data.RecoveryEvent
import com.med.sleepmanager.data.RestoreParticipants
import com.med.sleepmanager.integration.KeyMapperController
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization of service process-death recovery. The expected effects
 * come from the hand-wired recovery of 0.8.0.12 (SleepManagerService.onCreate,
 * recoverOwnedDeviceControls, recoverInterruptedSleepWifiMaintenance,
 * onStartCommand and onDestroy); [previousHandWiredRecovery] restates that code
 * over the same inputs and every combination is compared against production.
 */
class ServiceRecoveryTest {
    private data class Scenario(
        val realWake: Boolean,
        val sleeping: Boolean,
        val pending: Set<String>,
        val helperWifiManagedAndInstalled: Boolean
    )

    /**
     * Records the effective result of each action. Actions with internal guards
     * model them exactly like the service implementation, so recovery that
     * skips a call and recovery that makes a guarded no-op call compare equal.
     */
    private class RecordingHost(private val scenario: Scenario) : ServiceRecoveryHost {
        val effects = mutableListOf<String>()

        override fun isRealWake() = scenario.realWake
        override fun isEffectivelySleeping() = scenario.sleeping
        override fun isPending(participantId: String) = participantId in scenario.pending

        override fun restoreOwnedBasicSyncIfUnmanaged() {
            effects += "${RestoreParticipants.BASIC_SYNC_STOP_OWNERSHIP}:restore-if-unmanaged"
        }

        override fun restoreRaOfflineProxyIfUnmanaged() {
            // restorePendingRaOfflineProxyImmediately: no disable is requested
            // during onCreate, so it only restores on a real wake.
            if (scenario.realWake) effects += "${RaOfflineProxyConnector.id}:restore-if-unmanaged"
        }

        override fun recoverBatterySaver() {
            effects += "${RestoreParticipants.BATTERY_SAVER}:recover"
        }

        override fun recoverChargingSeparation() {
            effects += "${RestoreParticipants.CHARGING_SEPARATION}:recover"
        }

        override fun resumeRaOfflineProxySleepGate() {
            effects += "${RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE}:resume"
        }

        override fun reapplyDirectRadioSleepState(event: RecoveryEvent) {
            effects += "${RestoreParticipants.DIRECT_RADIO}:reapply@$event"
        }

        override fun reapplyHelperSleepWifi(event: RecoveryEvent) {
            if (event == RecoveryEvent.CREATE_ENABLED && !scenario.helperWifiManagedAndInstalled) return
            effects += "${RestoreParticipants.HELPER}:reapply@$event"
        }

        override fun restoreKeyMapper() {
            effects += "${KeyMapperController.CONNECTOR_ID}:restore"
        }

        override fun replayScreenState() {
            effects += "replay-screen-state"
        }
    }

    private fun production(event: RecoveryEvent, scenario: Scenario): List<String> {
        val host = RecordingHost(scenario)
        LegacyServiceRecovery.run(event, host)
        return host.effects
    }

    /** The 0.8.0.12 hand-wired recovery, restated over the scenario inputs. */
    private fun previousHandWiredRecovery(event: RecoveryEvent, s: Scenario): List<String> {
        val out = mutableListOf<String>()
        val raGate = RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE in s.pending
        val directRadio = RestoreParticipants.DIRECT_RADIO in s.pending
        val helper = RestoreParticipants.HELPER in s.pending
        when (event) {
            RecoveryEvent.CREATE -> {
                // maybeRestoreOwnedBasicSyncState(); maybeRestoreDisabledRaOfflineProxyState()
                out += "${RestoreParticipants.BASIC_SYNC_STOP_OWNERSHIP}:restore-if-unmanaged"
                if (s.realWake) out += "${RaOfflineProxyConnector.id}:restore-if-unmanaged"
            }

            RecoveryEvent.CREATE_ENABLED -> {
                // recoverOwnedDeviceControls(); recoverInterruptedSleepWifiMaintenance()
                out += "${RestoreParticipants.BATTERY_SAVER}:recover"
                out += "${RestoreParticipants.CHARGING_SEPARATION}:recover"
                if (s.sleeping) {
                    when {
                        raGate -> out += "${RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE}:resume"
                        directRadio -> out += "${RestoreParticipants.DIRECT_RADIO}:reapply@$event"
                        helper && s.helperWifiManagedAndInstalled ->
                            out += "${RestoreParticipants.HELPER}:reapply@$event"
                    }
                }
            }

            RecoveryEvent.START_ENABLED -> {
                // maybeRestoreOwnedBasicSyncState(); if (isRealWakeNow()) restorePendingKeyMapper()
                out += "${RestoreParticipants.BASIC_SYNC_STOP_OWNERSHIP}:restore-if-unmanaged"
                if (s.realWake) out += "${KeyMapperController.CONNECTOR_ID}:restore"
            }

            // applyCurrentScreenState() on the first enabled start.
            RecoveryEvent.SCREEN_STATE_REPLAY -> out += "replay-screen-state"

            RecoveryEvent.DESTROY_ENABLED -> {
                if (s.sleeping) {
                    when {
                        directRadio -> out += "${RestoreParticipants.DIRECT_RADIO}:reapply@$event"
                        helper -> out += "${RestoreParticipants.HELPER}:reapply@$event"
                    }
                }
            }
        }
        return out
    }

    private val sleepReapplyIds = listOf(
        RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE,
        RestoreParticipants.DIRECT_RADIO,
        RestoreParticipants.HELPER
    )

    private fun allScenarios(): List<Scenario> {
        val subsets = (0 until (1 shl sleepReapplyIds.size)).map { mask ->
            sleepReapplyIds.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
        }
        val out = mutableListOf<Scenario>()
        for (realWake in listOf(false, true))
            for (sleeping in listOf(false, true))
                for (pending in subsets)
                    for (helperWifi in listOf(false, true))
                        out += Scenario(realWake, sleeping, pending, helperWifi)
        return out
    }

    @Test
    fun everyEventAndScenarioMatchesThePreviousHandWiredRecovery() {
        RecoveryEvent.entries.forEach { event ->
            allScenarios().forEach { scenario ->
                assertEquals(
                    "$event $scenario",
                    previousHandWiredRecovery(event, scenario),
                    production(event, scenario)
                )
            }
        }
    }

    @Test
    fun killedMidSleepRestartReappliesOnlyTheFirstPendingSleepState() {
        val asleep = Scenario(
            realWake = false,
            sleeping = true,
            pending = sleepReapplyIds.toSet(),
            helperWifiManagedAndInstalled = true
        )
        assertEquals(
            listOf(
                "${RestoreParticipants.BATTERY_SAVER}:recover",
                "${RestoreParticipants.CHARGING_SEPARATION}:recover",
                "${RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE}:resume"
            ),
            production(RecoveryEvent.CREATE_ENABLED, asleep)
        )
        // Nothing is restored while still asleep.
        assertEquals(
            listOf("${RestoreParticipants.BASIC_SYNC_STOP_OWNERSHIP}:restore-if-unmanaged"),
            production(RecoveryEvent.CREATE, asleep)
        )
        assertEquals(
            listOf("${RestoreParticipants.BASIC_SYNC_STOP_OWNERSHIP}:restore-if-unmanaged"),
            production(RecoveryEvent.START_ENABLED, asleep)
        )
        // onDestroy never resumes the RAOfflineProxy gate: Direct radio wins.
        assertEquals(
            listOf("${RestoreParticipants.DIRECT_RADIO}:reapply@${RecoveryEvent.DESTROY_ENABLED}"),
            production(RecoveryEvent.DESTROY_ENABLED, asleep)
        )
    }

    @Test
    fun restartAfterRealWakeRestoresAndReappliesNothing() {
        val awake = Scenario(
            realWake = true,
            sleeping = false,
            pending = sleepReapplyIds.toSet(),
            helperWifiManagedAndInstalled = true
        )
        assertEquals(
            listOf(
                "${RestoreParticipants.BASIC_SYNC_STOP_OWNERSHIP}:restore-if-unmanaged",
                "${RaOfflineProxyConnector.id}:restore-if-unmanaged"
            ),
            production(RecoveryEvent.CREATE, awake)
        )
        assertEquals(
            listOf(
                "${RestoreParticipants.BATTERY_SAVER}:recover",
                "${RestoreParticipants.CHARGING_SEPARATION}:recover"
            ),
            production(RecoveryEvent.CREATE_ENABLED, awake)
        )
        assertEquals(
            listOf(
                "${RestoreParticipants.BASIC_SYNC_STOP_OWNERSHIP}:restore-if-unmanaged",
                "${KeyMapperController.CONNECTOR_ID}:restore"
            ),
            production(RecoveryEvent.START_ENABLED, awake)
        )
        assertEquals(listOf("replay-screen-state"), production(RecoveryEvent.SCREEN_STATE_REPLAY, awake))
        assertEquals(emptyList<String>(), production(RecoveryEvent.DESTROY_ENABLED, awake))
    }

    @Test
    fun helperReapplyOnCreateRequiresManagedWifiAndInstalledHelper() {
        val helperOnly = Scenario(
            realWake = false,
            sleeping = true,
            pending = setOf(RestoreParticipants.HELPER),
            helperWifiManagedAndInstalled = false
        )
        assertEquals(
            listOf(
                "${RestoreParticipants.BATTERY_SAVER}:recover",
                "${RestoreParticipants.CHARGING_SEPARATION}:recover"
            ),
            production(RecoveryEvent.CREATE_ENABLED, helperOnly)
        )
        // onDestroy re-asserts without that extra guard.
        assertEquals(
            listOf("${RestoreParticipants.HELPER}:reapply@${RecoveryEvent.DESTROY_ENABLED}"),
            production(RecoveryEvent.DESTROY_ENABLED, helperOnly)
        )
    }
}
