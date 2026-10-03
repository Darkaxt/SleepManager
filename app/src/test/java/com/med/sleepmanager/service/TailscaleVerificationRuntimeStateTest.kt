package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleVerificationRuntimeStateTest {
    @Test
    fun sleepAttemptCounterIncrementsAndCanBeResetIndependently() {
        val state = TailscaleVerificationRuntimeState()

        assertEquals(1, state.incrementSleepAttempts())
        assertEquals(2, state.incrementSleepAttempts())

        state.incrementWakeAttempts()
        state.markWakeRestoreNeeded()
        state.resetSleepAttempts()

        assertEquals(0, state.sleepAttempts)
        assertEquals(1, state.wakeAttempts)
        assertTrue(state.needsWakeRestore)
    }

    @Test
    fun wakeAttemptCounterIncrementsAndCanBeResetIndependently() {
        val state = TailscaleVerificationRuntimeState()

        assertEquals(1, state.incrementWakeAttempts())
        assertEquals(2, state.incrementWakeAttempts())

        state.incrementSleepAttempts()
        state.markWakeRestoreNeeded()
        state.resetWakeAttempts()

        assertEquals(0, state.wakeAttempts)
        assertEquals(1, state.sleepAttempts)
        assertTrue(state.needsWakeRestore)
    }

    @Test
    fun wakeRestoreNeedIsConsumedExactlyOnce() {
        val state = TailscaleVerificationRuntimeState()
        state.markWakeRestoreNeeded()

        assertTrue(state.consumeWakeRestoreNeeded())
        assertFalse(state.consumeWakeRestoreNeeded())
        assertFalse(state.needsWakeRestore)
    }

    @Test
    fun resetClearsBothCountersAndWakeRestoreNeed() {
        val state = TailscaleVerificationRuntimeState()
        state.incrementSleepAttempts()
        state.incrementWakeAttempts()
        state.markWakeRestoreNeeded()

        state.reset()

        assertEquals(0, state.sleepAttempts)
        assertEquals(0, state.wakeAttempts)
        assertFalse(state.needsWakeRestore)
    }
}
