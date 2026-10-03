package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeTransitionSyncStateTest {
    @Test
    fun arm_requiresStoredWakeArmAndAvailableSyncThenStop() {
        val state = WakeTransitionSyncState()

        state.arm(
            wakeSyncWasArmed = true,
            syncThenStopAvailable = true
        )

        assertTrue(state.isPending)

        state.arm(
            wakeSyncWasArmed = true,
            syncThenStopAvailable = false
        )

        assertFalse(state.isPending)

        state.arm(
            wakeSyncWasArmed = false,
            syncThenStopAvailable = true
        )

        assertFalse(state.isPending)
    }

    @Test
    fun clear_cancelsPendingWakeTransitionSync() {
        val state = WakeTransitionSyncState()
        state.arm(
            wakeSyncWasArmed = true,
            syncThenStopAvailable = true
        )

        state.clear()

        assertFalse(state.isPending)
    }
}
