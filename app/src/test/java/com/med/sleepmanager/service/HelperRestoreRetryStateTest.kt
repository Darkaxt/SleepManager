package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperRestoreRetryStateTest {
    @Test
    fun sameCycleRetriesAreBounded() {
        val state = HelperRestoreRetryState()
        state.begin(cycleId = 42L, requestSent = true)

        assertTrue(state.pending)
        assertEquals(1, state.attempts)
        assertEquals(
            HelperRestoreRetryDecision.RETRY,
            state.decision(
                activeCycleId = 42L,
                stillNeedsRestore = true,
                maxAttempts = 3
            )
        )

        state.recordRetrySent()
        assertEquals(
            HelperRestoreRetryDecision.RETRY,
            state.decision(
                activeCycleId = 42L,
                stillNeedsRestore = true,
                maxAttempts = 3
            )
        )

        state.recordRetrySent()
        assertEquals(
            HelperRestoreRetryDecision.EXHAUSTED,
            state.decision(
                activeCycleId = 42L,
                stillNeedsRestore = true,
                maxAttempts = 3
            )
        )
    }

    @Test
    fun sameCycleAcknowledgementClearsRetry() {
        val state = HelperRestoreRetryState()
        state.begin(cycleId = 77L, requestSent = true)
        state.acknowledge(resultCycleId = 77L)

        assertFalse(state.pending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun staleCycleNeverReplays() {
        val state = HelperRestoreRetryState()
        state.begin(cycleId = 9L, requestSent = true)

        assertEquals(
            HelperRestoreRetryDecision.STALE,
            state.decision(
                activeCycleId = 10L,
                stillNeedsRestore = true,
                maxAttempts = 3
            )
        )
    }

    @Test
    fun alreadyRestoredStateStopsReplay() {
        val state = HelperRestoreRetryState()
        state.begin(cycleId = 9L, requestSent = true)

        assertEquals(
            HelperRestoreRetryDecision.STALE,
            state.decision(
                activeCycleId = 9L,
                stillNeedsRestore = false,
                maxAttempts = 3
            )
        )
    }
}
