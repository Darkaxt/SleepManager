package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaOfflineProxyPreSleepStateTest {
    @Test
    fun staleCallbacksAreRejectedAcrossPhaseChanges() {
        val state = RaOfflineProxyPreSleepState()
        val queueGeneration = state.waitForQueue(cycleId = 42L)

        assertTrue(state.accepts(42L, queueGeneration))

        val stopGeneration =
            state.waitForStopConfirmation(cycleId = 42L)

        assertFalse(state.accepts(42L, queueGeneration))
        assertTrue(state.accepts(42L, stopGeneration))
        assertEquals(
            RaOfflineProxyPreSleepPhase.WAITING_FOR_STOP_CONFIRMATION,
            state.phase
        )
    }

    @Test
    fun clearInvalidatesPendingCallback() {
        val state = RaOfflineProxyPreSleepState()
        val generation = state.waitForQueue(cycleId = 99L)

        state.clear()

        assertFalse(state.accepts(99L, generation))
        assertEquals(RaOfflineProxyPreSleepPhase.NONE, state.phase)
        assertEquals(0L, state.cycleId)
    }
}
