package com.med.sleepmanager.service

import com.med.sleepmanager.data.RecoveryBehaviour
import com.med.sleepmanager.data.RecoveryBehaviour.REAPPLY_WHILE_SLEEPING
import com.med.sleepmanager.data.RecoveryBehaviour.REPLAY_SCREEN_STATE
import com.med.sleepmanager.data.RecoveryBehaviour.RESTORE_ON_REAL_WAKE
import com.med.sleepmanager.data.RecoveryBehaviour.RESTORE_OR_REAPPLY
import com.med.sleepmanager.data.RecoveryEvent
import com.med.sleepmanager.data.RecoveryEvent.CREATE
import com.med.sleepmanager.data.RecoveryEvent.CREATE_ENABLED
import com.med.sleepmanager.data.RecoveryEvent.DESTROY_ENABLED
import com.med.sleepmanager.data.RecoveryEvent.SCREEN_STATE_REPLAY
import com.med.sleepmanager.data.RestoreParticipants
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of service process-death recovery. The expected effects
 * come from the hand-wired recovery of 0.8.0.12 (SleepManagerService.onCreate,
 * recoverOwnedDeviceControls, recoverInterruptedSleepWifiMaintenance and
 * onDestroy), minus the BasicSync and Key Mapper steps of the integrations
 * removed after 0.8.0.13; [previousHandWiredRecovery] restates that code over
 * the same inputs and every combination is compared against production.
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

        override fun replayScreenState() {
            effects += "replay-screen-state"
        }
    }

    private fun production(event: RecoveryEvent, scenario: Scenario): List<String> {
        val host = RecordingHost(scenario)
        ServiceRecoveryCoordinator.create(host).run(event)
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
                // maybeRestoreDisabledRaOfflineProxyState()
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
        assertEquals(emptyList<String>(), production(RecoveryEvent.CREATE, asleep))
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
            listOf("${RaOfflineProxyConnector.id}:restore-if-unmanaged"),
            production(RecoveryEvent.CREATE, awake)
        )
        assertEquals(
            listOf(
                "${RestoreParticipants.BATTERY_SAVER}:recover",
                "${RestoreParticipants.CHARGING_SEPARATION}:recover"
            ),
            production(RecoveryEvent.CREATE_ENABLED, awake)
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

    /**
     * Every participant's declared recovery, as the 0.8.0.12 service wired it:
     * CREATE = onCreate before the enabled check, CREATE_ENABLED =
     * recoverOwnedDeviceControls + recoverInterruptedSleepWifiMaintenance,
     * SCREEN_STATE_REPLAY =
     * applyCurrentScreenState (onScreenOn restores / onScreenOff resumes),
     * DESTROY_ENABLED = onDestroy's sleep re-assert.
     */
    private val expectedDeclarations: Map<String, Map<RecoveryEvent, RecoveryBehaviour>> = mapOf(
        RestoreParticipants.HELPER to mapOf(
            SCREEN_STATE_REPLAY to REPLAY_SCREEN_STATE,
            CREATE_ENABLED to REAPPLY_WHILE_SLEEPING,
            DESTROY_ENABLED to REAPPLY_WHILE_SLEEPING
        ),
        RaOfflineProxyConnector.id to mapOf(
            CREATE to RESTORE_ON_REAL_WAKE,
            SCREEN_STATE_REPLAY to REPLAY_SCREEN_STATE
        ),
        SyncthingConnector.id to mapOf(SCREEN_STATE_REPLAY to REPLAY_SCREEN_STATE),
        TailscaleConnector.id to mapOf(SCREEN_STATE_REPLAY to REPLAY_SCREEN_STATE),
        RestoreParticipants.SLEEP_CYCLE_RECORD to mapOf(SCREEN_STATE_REPLAY to REPLAY_SCREEN_STATE),
        RestoreParticipants.DIRECT_RADIO to mapOf(
            SCREEN_STATE_REPLAY to REPLAY_SCREEN_STATE,
            CREATE_ENABLED to REAPPLY_WHILE_SLEEPING,
            DESTROY_ENABLED to REAPPLY_WHILE_SLEEPING
        ),
        RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE to mapOf(
            SCREEN_STATE_REPLAY to REPLAY_SCREEN_STATE,
            CREATE_ENABLED to REAPPLY_WHILE_SLEEPING
        ),
        RestoreParticipants.BATTERY_SAVER to mapOf(
            CREATE_ENABLED to RESTORE_OR_REAPPLY,
            SCREEN_STATE_REPLAY to REPLAY_SCREEN_STATE
        ),
        RestoreParticipants.CHARGING_SEPARATION to mapOf(CREATE_ENABLED to RESTORE_OR_REAPPLY)
    )

    @Test
    fun everyParticipantDeclaresItsPreviousRecoveryBehaviour() {
        assertEquals(
            "every registered participant needs a recovery declaration",
            RestoreParticipants.all.map { it.id }.toSet(),
            expectedDeclarations.keys
        )
        RestoreParticipants.all.forEach { participant ->
            assertEquals(
                participant.id,
                expectedDeclarations.getValue(participant.id),
                participant.recovery.associate { it.event to it.behaviour }
            )
        }
    }

    @Test
    fun recoveryOrderIsExplicitData() {
        assertEquals(
            mapOf(
                CREATE to listOf(
                    RaOfflineProxyConnector.id
                ),
                // Device controls first, then the first-match sleep re-apply
                // group: RAOfflineProxy gate, Direct radios, Helper.
                CREATE_ENABLED to listOf(
                    RestoreParticipants.BATTERY_SAVER,
                    RestoreParticipants.CHARGING_SEPARATION,
                    RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE,
                    RestoreParticipants.DIRECT_RADIO,
                    RestoreParticipants.HELPER
                ),
                DESTROY_ENABLED to listOf(
                    RestoreParticipants.DIRECT_RADIO,
                    RestoreParticipants.HELPER
                )
            ),
            RestoreParticipants.recoveryOrder
        )
    }

    @Test
    fun screenReplayRunsOnceForAllReplayedParticipants() {
        val host = RecordingHost(Scenario(true, false, emptySet(), true))
        val replayed = ServiceRecoveryCoordinator.create(host).run(SCREEN_STATE_REPLAY)
        assertEquals(listOf("replay-screen-state"), host.effects)
        assertEquals(
            RestoreParticipants.all.filter { it.recoveryAt(SCREEN_STATE_REPLAY) != null }.map { it.id },
            replayed
        )
        assertTrue(RestoreParticipants.DIRECT_RADIO in replayed)
    }

    private val noopHost = RecordingHost(Scenario(false, false, emptySet(), false))

    @Test(expected = IllegalArgumentException::class)
    fun missingRecoveryActionIsRejected() {
        ServiceRecoveryCoordinator(
            participants = RestoreParticipants.all,
            order = RestoreParticipants.recoveryOrder,
            actions = ServiceRecoveryCoordinator.actions(noopHost) -
                RecoveryActionKey(RestoreParticipants.HELPER, DESTROY_ENABLED),
            host = noopHost
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun orderMissingADeclaredParticipantIsRejected() {
        ServiceRecoveryCoordinator(
            participants = RestoreParticipants.all,
            order = RestoreParticipants.recoveryOrder +
                (CREATE to emptyList()),
            actions = ServiceRecoveryCoordinator.actions(noopHost),
            host = noopHost
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun splitFirstMatchGroupIsRejected() {
        ServiceRecoveryCoordinator(
            participants = RestoreParticipants.all,
            order = RestoreParticipants.recoveryOrder +
                (
                    CREATE_ENABLED to listOf(
                        RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE,
                        RestoreParticipants.BATTERY_SAVER,
                        RestoreParticipants.CHARGING_SEPARATION,
                        RestoreParticipants.DIRECT_RADIO,
                        RestoreParticipants.HELPER
                    )
                    ),
            actions = ServiceRecoveryCoordinator.actions(noopHost),
            host = noopHost
        )
    }
}
