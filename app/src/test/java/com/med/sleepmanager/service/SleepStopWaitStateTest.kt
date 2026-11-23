package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStopWaitStateTest {
    @Test
    fun begin_resetsTransientVerificationStateAndRecoversElapsedWait() {
        val state = SleepStopWaitState().apply {
            pendingSyncthing = true
            syncthingProbeInFlight = true
            syncthingConfirmed = true
        }

        state.begin(
            nowElapsed = 10_000L,
            elapsedBeforeRecoveryMs = 2_000L,
            timeoutMs = 5_000L
        )

        assertEquals(1L, state.generation)
        assertEquals(8_000L, state.startedAtElapsed)
        assertFalse(state.syncthingProbeInFlight)
        assertFalse(state.syncthingConfirmed)
    }

    @Test
    fun begin_withoutPendingSyncthingStartsAlreadyConfirmed() {
        val state = SleepStopWaitState()

        state.begin(
            nowElapsed = 10_000L,
            elapsedBeforeRecoveryMs = 0L,
            timeoutMs = 5_000L
        )

        assertTrue(state.syncthingConfirmed)
    }

    @Test
    fun begin_clampsRecoveredElapsedToTimeout() {
        val state = SleepStopWaitState()

        state.begin(
            nowElapsed = 10_000L,
            elapsedBeforeRecoveryMs = 9_000L,
            timeoutMs = 5_000L
        )

        assertEquals(5_000L, state.startedAtElapsed)
    }

    @Test
    fun clear_resetsGateStateAndInvalidatesAsyncVerification() {
        val state = SleepStopWaitState().apply {
            pendingWifi = true
            pendingBluetooth = true
            pendingSyncthing = true
            pendingPostStopActions = true
            startedAtElapsed = 9_000L
            syncthingProbeInFlight = true
            syncthingConfirmed = true
            generation = 7L
        }

        state.clear()

        assertEquals(8L, state.generation)
        assertFalse(state.pendingWifi)
        assertFalse(state.pendingBluetooth)
        assertFalse(state.pendingSyncthing)
        assertFalse(state.pendingPostStopActions)
        assertEquals(0L, state.startedAtElapsed)
        assertFalse(state.syncthingProbeInFlight)
        assertFalse(state.syncthingConfirmed)
    }

    @Test
    fun clearPendingActions_preservesVerificationTimingAndGeneration() {
        val state = SleepStopWaitState().apply {
            pendingWifi = true
            pendingBluetooth = true
            pendingSyncthing = true
            pendingPostStopActions = true
            startedAtElapsed = 8_000L
            syncthingProbeInFlight = true
            syncthingConfirmed = true
            generation = 4L
        }

        state.clearPendingActions()

        assertFalse(state.pendingWifi)
        assertFalse(state.pendingBluetooth)
        assertFalse(state.pendingSyncthing)
        assertFalse(state.pendingPostStopActions)
        assertEquals(8_000L, state.startedAtElapsed)
        assertTrue(state.syncthingProbeInFlight)
        assertTrue(state.syncthingConfirmed)
        assertEquals(4L, state.generation)
    }
}
