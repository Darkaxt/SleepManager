package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperWakeResultStateTest {
    @Test
    fun recordStoresCompleteHelperWakeResult() {
        val state = HelperWakeResultState()

        state.record(
            wifiManaged = true,
            wifiChanged = true,
            wifiAttempted = true,
            wifiToggleSuccess = false,
            wifiAirplaneMode = true,
            bluetoothManaged = true,
            bluetoothChanged = true
        )

        assertTrue(state.wifiManaged)
        assertTrue(state.wifiChanged)
        assertTrue(state.wifiAttempted)
        assertFalse(state.wifiToggleSuccess)
        assertTrue(state.wifiAirplaneMode)
        assertTrue(state.bluetoothManaged)
        assertTrue(state.bluetoothChanged)
    }

    @Test
    fun beginWakeResetsManagedAndChangedFlagsButPreservesWifiDiagnostics() {
        val state = HelperWakeResultState()
        state.record(
            wifiManaged = true,
            wifiChanged = true,
            wifiAttempted = true,
            wifiToggleSuccess = false,
            wifiAirplaneMode = true,
            bluetoothManaged = true,
            bluetoothChanged = true
        )

        state.beginWake()

        assertFalse(state.wifiManaged)
        assertFalse(state.wifiChanged)
        assertTrue(state.wifiAttempted)
        assertFalse(state.wifiToggleSuccess)
        assertTrue(state.wifiAirplaneMode)
        assertFalse(state.bluetoothManaged)
        assertFalse(state.bluetoothChanged)
    }

    @Test
    fun helperUnavailableClearsOnlyManagedFlags() {
        val state = HelperWakeResultState()
        state.record(
            wifiManaged = true,
            wifiChanged = true,
            wifiAttempted = true,
            wifiToggleSuccess = false,
            wifiAirplaneMode = true,
            bluetoothManaged = true,
            bluetoothChanged = true
        )

        state.markHelperUnavailable()

        assertFalse(state.wifiManaged)
        assertTrue(state.wifiChanged)
        assertTrue(state.wifiAttempted)
        assertFalse(state.wifiToggleSuccess)
        assertTrue(state.wifiAirplaneMode)
        assertFalse(state.bluetoothManaged)
        assertTrue(state.bluetoothChanged)
    }
}
