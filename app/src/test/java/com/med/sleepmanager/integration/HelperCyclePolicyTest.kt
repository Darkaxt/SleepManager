package com.med.sleepmanager.integration

import com.med.sleepmanager.protocol.HelperCyclePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class HelperCyclePolicyTest {
    @Test
    fun freshSleep_startsNewCycle() {
        assertEquals(
            HelperCyclePolicy.SleepDecision.START_NEW,
            HelperCyclePolicy.sleepDecision(false, 0L, 42L)
        )
    }

    @Test
    fun duplicateSleepForSameCycle_reportsAlreadySleeping() {
        assertEquals(
            HelperCyclePolicy.SleepDecision.ALREADY_SLEEPING,
            HelperCyclePolicy.sleepDecision(true, 42L, 42L)
        )
    }

    @Test
    fun legacySleepWithoutCycleId_keepsExistingCycleCompatible() {
        assertEquals(
            HelperCyclePolicy.SleepDecision.ALREADY_SLEEPING,
            HelperCyclePolicy.sleepDecision(true, 42L, 0L)
        )
    }

    @Test
    fun sleepForDifferentCorrelatedCycle_isRejected() {
        assertEquals(
            HelperCyclePolicy.SleepDecision.CYCLE_MISMATCH,
            HelperCyclePolicy.sleepDecision(true, 42L, 43L)
        )
    }

    @Test
    fun activeMatchingRestore_restoresCurrentCycle() {
        assertEquals(
            HelperCyclePolicy.RestoreDecision.RESTORE_ACTIVE,
            HelperCyclePolicy.restoreDecision(true, 42L, 0L, 42L)
        )
    }

    @Test
    fun legacyRestoreWithoutCycleId_restoresActiveCycle() {
        assertEquals(
            HelperCyclePolicy.RestoreDecision.RESTORE_ACTIVE,
            HelperCyclePolicy.restoreDecision(true, 42L, 0L, 0L)
        )
    }

    @Test
    fun restoreForDifferentCorrelatedCycle_isRejected() {
        assertEquals(
            HelperCyclePolicy.RestoreDecision.CYCLE_MISMATCH,
            HelperCyclePolicy.restoreDecision(true, 42L, 0L, 43L)
        )
    }

    @Test
    fun duplicateRestoreForCompletedCycle_isIdempotent() {
        assertEquals(
            HelperCyclePolicy.RestoreDecision.ALREADY_RESTORED,
            HelperCyclePolicy.restoreDecision(false, 0L, 42L, 42L)
        )
    }

    @Test
    fun restoreWithoutMatchingCompletedCycle_reportsNoActiveCycle() {
        assertEquals(
            HelperCyclePolicy.RestoreDecision.NO_ACTIVE_CYCLE,
            HelperCyclePolicy.restoreDecision(false, 0L, 41L, 42L)
        )
    }

    @Test
    fun legacyRestoreWithNoActiveCycle_reportsNoActiveCycle() {
        assertEquals(
            HelperCyclePolicy.RestoreDecision.NO_ACTIVE_CYCLE,
            HelperCyclePolicy.restoreDecision(false, 0L, 42L, 0L)
        )
    }
}
