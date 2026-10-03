package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosedLidRuntimeStateTest {
    @Test
    fun closedLidFlags_canBeChangedIndependently() {
        val state = ClosedLidRuntimeState()

        state.markAwakeOverride()
        state.markSleepIntent()
        state.markSleepRequestPending()

        assertTrue(state.awakeOverride)
        assertTrue(state.sleepIntent)
        assertTrue(state.sleepRequestPending)

        state.clearAwakeOverride()
        assertFalse(state.awakeOverride)
        assertTrue(state.sleepIntent)
        assertTrue(state.sleepRequestPending)

        state.clearSleepIntent()
        assertFalse(state.sleepIntent)
        assertTrue(state.sleepRequestPending)

        state.clearSleepRequestPending()
        assertFalse(state.sleepRequestPending)
    }

    @Test
    fun clearingTransientFlags_doesNotResetLockCooldownTimestamp() {
        val state = ClosedLidRuntimeState()
        state.recordLock(12_345L)
        state.markAwakeOverride()
        state.markSleepIntent()
        state.markSleepRequestPending()

        state.clearAwakeOverride()
        state.clearSleepIntent()
        state.clearSleepRequestPending()

        assertEquals(12_345L, state.lastLockAtElapsed)
    }
}
