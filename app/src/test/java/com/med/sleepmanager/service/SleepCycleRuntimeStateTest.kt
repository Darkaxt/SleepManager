package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepCycleRuntimeStateTest {
    @Test
    fun actionsApplied_canBeMarkedAndClearedIndependently() {
        val state = SleepCycleRuntimeState().apply {
            markSkippedByConditions()
        }

        state.markActionsApplied()
        assertTrue(state.actionsApplied)
        assertTrue(state.skippedByConditions)

        state.clearActionsApplied()

        assertFalse(state.actionsApplied)
        assertTrue(state.skippedByConditions)
    }


    @Test
    fun falseWakeResleepPending_isOneShotAndIndependent() {
        val state = SleepCycleRuntimeState().apply {
            markActionsApplied()
            markSkippedByConditions()
            markFalseWakeResleepPending()
        }

        assertTrue(state.falseWakeResleepPending)
        assertTrue(state.consumeFalseWakeResleepPending())
        assertFalse(state.falseWakeResleepPending)
        assertFalse(state.consumeFalseWakeResleepPending())
        assertTrue(state.actionsApplied)
        assertTrue(state.skippedByConditions)
    }

    @Test
    fun skippedByConditions_canBeMarkedAndClearedIndependently() {
        val state = SleepCycleRuntimeState().apply {
            markActionsApplied()
        }

        state.markSkippedByConditions()
        assertTrue(state.skippedByConditions)
        assertTrue(state.actionsApplied)

        state.clearSkippedByConditions()

        assertFalse(state.skippedByConditions)
        assertTrue(state.actionsApplied)
    }
}
