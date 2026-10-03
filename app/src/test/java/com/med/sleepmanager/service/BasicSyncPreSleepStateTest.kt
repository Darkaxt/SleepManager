package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicSyncPreSleepStateTest {
    @Test
    fun beginActiveWait_recordsStartAndClearsSyncedStabilityWindow() {
        val state = BasicSyncPreSleepState().apply {
            syncedSinceElapsed = 4_000L
            freshProbeGeneration = 3L
        }

        state.beginActiveWait(nowElapsed = 10_000L)

        assertTrue(state.waitingForActiveSync)
        assertEquals(10_000L, state.waitStartedAtElapsed)
        assertEquals(0L, state.syncedSinceElapsed)
        assertEquals(3L, state.freshProbeGeneration)
    }

    @Test
    fun cancelActiveWait_resetsWaitAndInvalidatesFreshProbeCallback() {
        val state = BasicSyncPreSleepState().apply {
            waitingForActiveSync = true
            waitStartedAtElapsed = 10_000L
            syncedSinceElapsed = 11_000L
            freshProbeGeneration = 7L
        }

        state.cancelActiveWait()

        assertFalse(state.waitingForActiveSync)
        assertEquals(0L, state.waitStartedAtElapsed)
        assertEquals(0L, state.syncedSinceElapsed)
        assertEquals(8L, state.freshProbeGeneration)
    }
}
