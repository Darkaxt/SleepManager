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
        assertEquals(4_750_400L, result.displayedCurrentUah)
        assertEquals(BatteryCapacitySource.DESIGN_FULL, result.capacitySource)
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
