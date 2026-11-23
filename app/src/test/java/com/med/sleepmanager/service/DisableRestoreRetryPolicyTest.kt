package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisableRestoreRetryPolicyTest {
    @Test
    fun delayDoublesFromOneMinute() {
        assertEquals(60_000L, DisableRestoreRetryPolicy.delayMs(0))
        assertEquals(120_000L, DisableRestoreRetryPolicy.delayMs(1))
        assertEquals(240_000L, DisableRestoreRetryPolicy.delayMs(2))
        assertEquals(32 * 60_000L, DisableRestoreRetryPolicy.delayMs(5))
    }

    @Test
    fun delayIsCappedAtOneHour() {
        assertEquals(60 * 60_000L, DisableRestoreRetryPolicy.delayMs(6))
        assertEquals(60 * 60_000L, DisableRestoreRetryPolicy.delayMs(29))
        assertEquals(60 * 60_000L, DisableRestoreRetryPolicy.delayMs(Int.MAX_VALUE))
    }

    @Test
    fun retriesAreBounded() {
        assertTrue(DisableRestoreRetryPolicy.shouldRetry(0))
        assertTrue(
            DisableRestoreRetryPolicy.shouldRetry(DisableRestoreRetryPolicy.MAX_ATTEMPTS - 1)
        )
        assertFalse(
            DisableRestoreRetryPolicy.shouldRetry(DisableRestoreRetryPolicy.MAX_ATTEMPTS)
        )
    }
}
