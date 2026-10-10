package com.med.sleepmanager.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryCapacityPolicyTest {
    @Test
    fun normalLearnedCapacity_preservesRawValues() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 80,
                chargeCounterUah = 4_727_200L,
                learnedFullUah = 5_909_000L,
                designFullUah = 5_938_000L
            )

        assertFalse(result.learnedFullSuspect)
        assertEquals(5_909_000L, result.selectedFullUah)
        assertEquals(4_727_200L, result.displayedCurrentUah)
        assertEquals(BatteryCapacitySource.LEARNED_FULL, result.capacitySource)
        assertEquals(BatteryCurrentSource.RAW_COUNTER, result.currentSource)
    }

    @Test
    fun wornBatteryBelowDesign_remainsValidLearnedCapacity() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 75,
                chargeCounterUah = 3_750_000L,
                learnedFullUah = 5_000_000L,
                designFullUah = 5_938_000L
            )

        assertFalse(result.learnedFullSuspect)
        assertEquals(5_000_000L, result.selectedFullUah)
        assertEquals(BatteryCapacitySource.LEARNED_FULL, result.capacitySource)
    }

    @Test
    fun implausiblyHighLearnedCapacity_fallsBackToDesign() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 80,
                chargeCounterUah = 6_674_635L,
                learnedFullUah = 8_336_000L,
                designFullUah = 5_938_000L
            )

        assertTrue(result.learnedFullSuspect)
        assertEquals(5_938_000L, result.selectedFullUah)
        assertEquals(4_754_556L, result.displayedCurrentUah)
        assertEquals(BatteryCapacitySource.DESIGN_FULL, result.capacitySource)
        assertEquals(BatteryCurrentSource.NORMALIZED_COUNTER, result.currentSource)
    }

    @Test
    fun normalizedCounter_preservesSubPercentMovementAtSameAndroidPercent() {
        val start =
            BatteryCapacityPolicy.select(
                percent = 80,
                chargeCounterUah = 6_674_635L,
                learnedFullUah = 8_336_000L,
                designFullUah = 5_938_000L
            )
        val end =
            BatteryCapacityPolicy.select(
                percent = 80,
                chargeCounterUah = 6_650_000L,
                learnedFullUah = 8_336_000L,
                designFullUah = 5_938_000L
            )

        assertEquals(4_754_556L, start.displayedCurrentUah)
        assertEquals(4_737_008L, end.displayedCurrentUah)
        assertEquals(BatteryCurrentSource.NORMALIZED_COUNTER, start.currentSource)
        assertEquals(BatteryCurrentSource.NORMALIZED_COUNTER, end.currentSource)
        assertTrue(start.displayedCurrentUah!! > end.displayedCurrentUah!!)
    }

    @Test
    fun suspectLearnedCapacity_keepsAlreadyPlausibleRawCounter() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 80,
                chargeCounterUah = 4_750_000L,
                learnedFullUah = 8_336_000L,
                designFullUah = 5_938_000L
            )

        assertEquals(4_750_000L, result.displayedCurrentUah)
        assertEquals(BatteryCurrentSource.RAW_COUNTER, result.currentSource)
    }

    @Test
    fun normalizedCounterMustRemainConsistentWithAndroidPercent() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 50,
                chargeCounterUah = 7_500_000L,
                learnedFullUah = 8_336_000L,
                designFullUah = 5_938_000L
            )

        assertEquals(2_969_000L, result.displayedCurrentUah)
        assertEquals(BatteryCurrentSource.PERCENT_DERIVED, result.currentSource)
    }

    @Test
    fun implausibleCounter_withPlausibleFull_usesPercentageForDisplay() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 50,
                chargeCounterUah = 7_000_000L,
                learnedFullUah = 5_900_000L,
                designFullUah = 5_938_000L
            )

        assertEquals(5_900_000L, result.selectedFullUah)
        assertEquals(2_950_000L, result.displayedCurrentUah)
        assertEquals(BatteryCurrentSource.PERCENT_DERIVED, result.currentSource)
    }

    @Test
    fun missingLearnedCapacity_usesDesign() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 50,
                chargeCounterUah = 2_950_000L,
                learnedFullUah = null,
                designFullUah = 5_938_000L
            )

        assertEquals(5_938_000L, result.selectedFullUah)
        assertEquals(BatteryCapacitySource.DESIGN_FULL, result.capacitySource)
    }

    @Test
    fun missingFullValues_derivesCapacityFromCounterAndPercent() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 80,
                chargeCounterUah = 4_800_000L,
                learnedFullUah = null,
                designFullUah = null
            )

        assertEquals(6_000_000L, result.selectedFullUah)
        assertEquals(BatteryCapacitySource.COUNTER_DERIVED, result.capacitySource)
        assertEquals(4_800_000L, result.displayedCurrentUah)
    }

    @Test
    fun batteryHealth_usesReportedFullAgainstDesign() {
        val health =
            BatteryCapacityPolicy.batteryHealthPercent(
                learnedFullUah = 5_886_000L,
                designFullUah = 5_938_000L
            )

        assertEquals(99.1242842708, health!!, 0.0000001)
    }

    @Test
    fun batteryHealth_capsUserFacingValueAtOneHundred() {
        val health =
            BatteryCapacityPolicy.batteryHealthPercent(
                learnedFullUah = 5_361_000L,
                designFullUah = 5_097_000L
            )
        val raw =
            BatteryCapacityPolicy.rawBatteryHealthPercent(
                learnedFullUah = 5_361_000L,
                designFullUah = 5_097_000L
            )

        assertEquals(100.0, health!!, 0.0000001)
        assertEquals(105.1795173632, raw!!, 0.0000001)
    }

    @Test
    fun konkrDecimalScaleDesign_isCorrectedOnlyWithConsistentEvidence() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 62,
                chargeCounterUah = 2_493_640L,
                learnedFullUah = 4_022_000L,
                designFullUah = 402_000L
            )

        assertTrue(result.designScaleAdjusted)
        assertEquals(4_020_000L, result.effectiveDesignUah)
        assertFalse(result.learnedFullSuspect)
        assertEquals(4_022_000L, result.selectedFullUah)
        assertEquals(2_493_640L, result.displayedCurrentUah)
        assertEquals(BatteryCurrentSource.RAW_COUNTER, result.currentSource)
    }

    @Test
    fun oppositeTimesTenCase_doesNotRescaleAlreadyPlausibleDesign() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 80,
                chargeCounterUah = 40_000_000L,
                learnedFullUah = 50_000_000L,
                designFullUah = 5_000_000L
            )

        assertFalse(result.designScaleAdjusted)
        assertEquals(5_000_000L, result.effectiveDesignUah)
        assertTrue(result.learnedFullSuspect)
        assertEquals(5_000_000L, result.selectedFullUah)
        // The design value itself must stay untouched, but the counter can
        // still be normalized from the suspect learned-capacity scale.
        assertEquals(4_000_000L, result.displayedCurrentUah)
        assertEquals(BatteryCurrentSource.NORMALIZED_COUNTER, result.currentSource)
    }

    @Test
    fun batteryHealth_isUnavailableWhenReportedFullIsSuspect() {
        val health =
            BatteryCapacityPolicy.batteryHealthPercent(
                learnedFullUah = 8_336_000L,
                designFullUah = 5_938_000L
            )

        assertEquals(null, health)
    }

    @Test
    fun batteryHealth_requiresBothReportedAndDesignCapacity() {
        assertEquals(
            null,
            BatteryCapacityPolicy.batteryHealthPercent(
                learnedFullUah = null,
                designFullUah = 5_938_000L
            )
        )
        assertEquals(
            null,
            BatteryCapacityPolicy.batteryHealthPercent(
                learnedFullUah = 5_886_000L,
                designFullUah = null
            )
        )
    }

    @Test
    fun percentageFallback_roundsToNearestMicroampHour() {
        val result =
            BatteryCapacityPolicy.select(
                percent = 33,
                chargeCounterUah = 9_000_000L,
                learnedFullUah = 6_001_001L,
                designFullUah = 6_000_000L
            )

        assertEquals(1_980_330L, result.displayedCurrentUah)
        assertEquals(BatteryCurrentSource.PERCENT_DERIVED, result.currentSource)
    }
}
