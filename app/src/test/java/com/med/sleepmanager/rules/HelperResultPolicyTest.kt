package com.med.sleepmanager.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class HelperResultPolicyTest {
    @Test
    fun matchingCycle_isCurrent() {
        assertEquals(
            HelperResultCorrelation.CURRENT,
            HelperResultPolicy.correlate(
                resultCycleId = 42L,
                cycleActive = true,
                currentCycleId = 42L
            )
        )
    }

    @Test
    fun olderHelperWithoutCycleId_isAcceptedOnlyDuringActiveCycle() {
        assertEquals(
            HelperResultCorrelation.LEGACY_UNCORRELATED,
            HelperResultPolicy.correlate(
                resultCycleId = 0L,
                cycleActive = true,
                currentCycleId = 42L
            )
        )
    }

    @Test
    fun delayedLegacyResultAfterCycleCompletion_isStale() {
        assertEquals(
            HelperResultCorrelation.STALE,
            HelperResultPolicy.correlate(
                resultCycleId = 0L,
                cycleActive = false,
                currentCycleId = 0L
            )
        )
    }

    @Test
    fun mismatchedCycle_isStale() {
        assertEquals(
            HelperResultCorrelation.STALE,
            HelperResultPolicy.correlate(
                resultCycleId = 41L,
                cycleActive = true,
                currentCycleId = 42L
            )
        )
    }

    @Test
    fun correlatedResultWithoutActiveCycle_isStale() {
        assertEquals(
            HelperResultCorrelation.STALE,
            HelperResultPolicy.correlate(
                resultCycleId = 42L,
                cycleActive = false,
                currentCycleId = 0L
            )
        )
    }
}
