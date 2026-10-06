package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaOfflineProxyRestoreRetryStateTest {
    @Test
    fun retryIsBounded() {
        val state = RaOfflineProxyRestoreRetryState()
        state.markAttemptStarted()

        assertEquals(
            RaOfflineProxyRestoreRetryDecision.RETRY,
            state.onRestoreResult(
                confirmed = false,
                retryable = true,
                maxAttempts = 2
            )
        )
        assertTrue(state.isPending)

        assertEquals(
            RaOfflineProxyRestoreRetryDecision.EXHAUSTED,
            state.onRestoreResult(
                confirmed = false,
                retryable = true,
                maxAttempts = 2
            )
        )
        assertFalse(state.isPending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun confirmedRestoreClearsState() {
        val state = RaOfflineProxyRestoreRetryState()
        state.markAttemptStarted()

        assertEquals(
            RaOfflineProxyRestoreRetryDecision.COMPLETE,
            state.onRestoreResult(
                confirmed = true,
                retryable = false,
                maxAttempts = 3
            )
        )
        assertFalse(state.isPending)
    }
}
