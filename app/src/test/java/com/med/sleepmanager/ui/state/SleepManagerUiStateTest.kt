package com.med.sleepmanager.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SleepManagerUiStateTest {
    @Test
    fun defaultsMatchExistingActivityState() {
        val state = SleepManagerUiState()

        assertEquals(0, state.activityRefreshToken)
        assertFalse(state.managerEnabled)
        assertFalse(state.manageWifiEnabled)
        assertFalse(state.manageBluetoothEnabled)
        assertFalse(state.manageBatterySaverEnabled)
        assertFalse(state.chargingSeparationWithLidEnabled)
        assertFalse(state.manageSyncthingEnabled)
        assertFalse(state.manageTailscaleEnabled)
        assertFalse(state.manageJamesDspEnabled)
        assertFalse(state.manageBasicSyncEnabled)
        assertFalse(state.closedLidProtectionEnabled)
        assertFalse(state.dockDisconnectSleeps)
        assertFalse(state.closedLidPowerSleeps)
        assertFalse(state.periodicSyncWhileSleeping)
        assertFalse(state.syncThenStopOnSleepWake)
        assertEquals(0L, state.sleepGraceMs)
        assertFalse(state.customDelayEnabled)
        assertEquals(60_000L, state.customDelayMs)
        assertFalse(state.batteryConditionEnabled)
        assertEquals(30, state.batteryBelowPercent)
        assertFalse(state.notChargingOnly)
        assertEquals("ignore", state.batterySaverMode)
        assertFalse(state.scheduleEnabled)
        assertEquals(23 * 60, state.scheduleStartMinutes)
        assertEquals(7 * 60, state.scheduleEndMinutes)
        assertEquals(true, state.automaticUpdateChecks)
        assertNull(state.currentWifiState)
        assertNull(state.currentBluetoothState)
        assertNull(state.currentSyncthingState)
        assertNull(state.currentTailscaleConnected)
        assertNull(state.currentBasicSyncState)
        assertNull(state.currentBackgroundReliability)
        assertNull(state.currentDeviceControlCapabilities)
        assertFalse(state.currentBatterySaverState)
        assertEquals(0, state.installerReturnToken)
    }

    @Test
    fun copyUpdatesOneFieldWithoutChangingOthers() {
        val updated =
            SleepManagerUiState()
                .copy(
                    activityRefreshToken = 3,
                    managerEnabled = true,
                    manageWifiEnabled = true,
                    manageBluetoothEnabled = true,
                    manageBatterySaverEnabled = true,
                    chargingSeparationWithLidEnabled = true,
                    manageSyncthingEnabled = true,
                    manageTailscaleEnabled = true,
                    manageJamesDspEnabled = true,
                    manageBasicSyncEnabled = true,
                    closedLidProtectionEnabled = true,
                    dockDisconnectSleeps = true,
                    closedLidPowerSleeps = true,
                    periodicSyncWhileSleeping = true,
                    syncThenStopOnSleepWake = true,
                    sleepGraceMs = 10_000L,
                    customDelayEnabled = true,
                    customDelayMs = 300_000L,
                    batteryConditionEnabled = true,
                    batteryBelowPercent = 25,
                    notChargingOnly = true,
                    batterySaverMode = "on",
                    scheduleEnabled = true,
                    scheduleStartMinutes = 22 * 60,
                    scheduleEndMinutes = 6 * 60,
                    automaticUpdateChecks = false,
                    currentWifiState = true
                )

        assertEquals(3, updated.activityRefreshToken)
        assertEquals(true, updated.managerEnabled)
        assertEquals(true, updated.manageWifiEnabled)
        assertEquals(true, updated.manageBluetoothEnabled)
        assertEquals(true, updated.manageBatterySaverEnabled)
        assertEquals(true, updated.chargingSeparationWithLidEnabled)
        assertEquals(true, updated.manageSyncthingEnabled)
        assertEquals(true, updated.manageTailscaleEnabled)
        assertEquals(true, updated.manageJamesDspEnabled)
        assertEquals(true, updated.manageBasicSyncEnabled)
        assertEquals(true, updated.closedLidProtectionEnabled)
        assertEquals(true, updated.dockDisconnectSleeps)
        assertEquals(true, updated.closedLidPowerSleeps)
        assertEquals(true, updated.periodicSyncWhileSleeping)
        assertEquals(true, updated.syncThenStopOnSleepWake)
        assertEquals(10_000L, updated.sleepGraceMs)
        assertEquals(true, updated.customDelayEnabled)
        assertEquals(300_000L, updated.customDelayMs)
        assertEquals(true, updated.batteryConditionEnabled)
        assertEquals(25, updated.batteryBelowPercent)
        assertEquals(true, updated.notChargingOnly)
        assertEquals("on", updated.batterySaverMode)
        assertEquals(true, updated.scheduleEnabled)
        assertEquals(22 * 60, updated.scheduleStartMinutes)
        assertEquals(6 * 60, updated.scheduleEndMinutes)
        assertEquals(false, updated.automaticUpdateChecks)
        assertEquals(true, updated.currentWifiState)
        assertNull(updated.currentBluetoothState)
        assertFalse(updated.currentBatterySaverState)
        assertEquals(0, updated.installerReturnToken)
    }
}
