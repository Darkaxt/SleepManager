package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicSyncRestoreRetryStateTest {
    @Test
    fun startedAttemptMarksRestorePendingWithoutResettingAttempts() {
        val state = BasicSyncRestoreRetryState()

        state.markAttemptStarted()
        assertEquals(
            BasicSyncRestoreRetryDecision.RETRY,
            state.onRestoreResult(
                retryable = true,
                maxAttempts = 8
            )
        )
        assertEquals(1, state.attempts)

        state.markAttemptStarted()

        assertTrue(state.isPending)
        assertEquals(1, state.attempts)
    }

    @Test
    fun completedRestoreClearsPendingAndAttempts() {
        val state = BasicSyncRestoreRetryState()
        state.markAttemptStarted()
        state.onRestoreResult(
            retryable = true,
            maxAttempts = 8
        )
        state.markAttemptStarted()

        val decision =
            state.onRestoreResult(
                retryable = false,
                maxAttempts = 8
            )

        assertEquals(
            BasicSyncRestoreRetryDecision.COMPLETE,
            decision
        )
        assertFalse(state.isPending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun retryableRestoreExhaustsAtConfiguredLimit() {
        val state = BasicSyncRestoreRetryState()

        repeat(7) {
            state.markAttemptStarted()
            assertEquals(
                BasicSyncRestoreRetryDecision.RETRY,
                state.onRestoreResult(
                    retryable = true,
                    maxAttempts = 8
                )
            )
        }

        state.markAttemptStarted()
        val finalDecision =
            state.onRestoreResult(
                retryable = true,
                maxAttempts = 8
            )

        assertEquals(
            BasicSyncRestoreRetryDecision.EXHAUSTED,
            finalDecision
        )
        assertFalse(state.isPending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun resetClearsPendingAndAttemptsWhenRestoreNoLongerNeeded() {
        val state = BasicSyncRestoreRetryState()
        state.markAttemptStarted()
        state.onRestoreResult(
            retryable = true,
            maxAttempts = 8
        )

        state.reset()

        assertFalse(state.isPending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun clearPendingPreservesAttemptCounterForDestroySemantics() {
        val state = BasicSyncRestoreRetryState()
        state.markAttemptStarted()
        state.onRestoreResult(
            retryable = true,
            maxAttempts = 8
        )

        state.clearPending()

        assertFalse(state.isPending)
        assertEquals(1, state.attempts)
    }
}
