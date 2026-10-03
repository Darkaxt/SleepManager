package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceControlRestoreRetryStateTest {
    @Test
    fun beginCompletesImmediatelyWhenBothControlsRestore() {
        val state = DeviceControlRestoreRetryState()

        val decision = state.begin(restored = true)

        assertEquals(
            DeviceControlRestoreRetryDecision.COMPLETE,
            decision
        )
        assertFalse(state.isPending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun beginFailedRestoreArmsFirstRetryAttempt() {
        val state = DeviceControlRestoreRetryState()

        val decision = state.begin(restored = false)

        assertEquals(
            DeviceControlRestoreRetryDecision.RETRY,
            decision
        )
        assertTrue(state.isPending)
        assertEquals(1, state.attempts)
    }

    @Test
    fun retryCompletesAndResetsStateWhenRestoreSucceeds() {
        val state = DeviceControlRestoreRetryState()
        state.begin(restored = false)

        val decision =
            state.onRetryResult(
                restored = true,
                maxAttempts = 6
            )

        assertEquals(
            DeviceControlRestoreRetryDecision.COMPLETE,
            decision
        )
        assertFalse(state.isPending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun retryExhaustsAtConfiguredAttemptLimit() {
        val state = DeviceControlRestoreRetryState()
        state.begin(restored = false)

        repeat(4) {
            assertEquals(
                DeviceControlRestoreRetryDecision.RETRY,
                state.onRetryResult(
                    restored = false,
                    maxAttempts = 6
                )
            )
        }

        val finalDecision =
            state.onRetryResult(
                restored = false,
                maxAttempts = 6
            )

        assertEquals(
            DeviceControlRestoreRetryDecision.EXHAUSTED,
            finalDecision
        )
        assertFalse(state.isPending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun cancelResetsPendingAndAttempts() {
        val state = DeviceControlRestoreRetryState()
        state.begin(restored = false)

        state.cancel()

        assertFalse(state.isPending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun clearPendingPreservesAttemptCounterForDestroySemantics() {
        val state = DeviceControlRestoreRetryState()
        state.begin(restored = false)

        state.clearPending()

        assertFalse(state.isPending)
        assertEquals(1, state.attempts)
    }
}
