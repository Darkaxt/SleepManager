package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepDelayStateTest {
    @Test
    fun markPending_marksDelayAsPending() {
        val state = SleepDelayState()

        state.markPending()

        assertTrue(state.isPending)
    }

    @Test
    fun clear_removesPendingDelay() {
        val state = SleepDelayState()
        state.markPending()

        state.clear()

        assertFalse(state.isPending)
    }

    @Test
    fun clear_isIdempotent() {
        val state = SleepDelayState()

        state.clear()
        state.clear()

        assertFalse(state.isPending)
    }
}
