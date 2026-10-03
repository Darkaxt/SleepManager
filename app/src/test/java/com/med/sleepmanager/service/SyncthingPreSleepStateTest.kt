package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncthingPreSleepStateTest {
    @Test
    fun nextProbeGeneration_makesNewestProbeCurrent() {
        val state = SyncthingPreSleepState()

        val first = state.nextProbeGeneration()
        val second = state.nextProbeGeneration()

        assertFalse(state.isCurrent(first))
        assertTrue(state.isCurrent(second))
    }

    @Test
    fun invalidate_rejectsLateProbeResult() {
        val state = SyncthingPreSleepState()
        val generation = state.nextProbeGeneration()

        state.invalidate()

        assertFalse(state.isCurrent(generation))
    }
}
