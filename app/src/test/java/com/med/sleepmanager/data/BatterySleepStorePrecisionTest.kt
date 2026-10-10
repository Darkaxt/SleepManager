package com.med.sleepmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySleepStorePrecisionTest {

    @Test
    fun preciseDrainIsPreferredOverIntegerBatteryLevel() {
        val session =
            BatterySleepStore.SleepSession(
                startedAt = 0L,
                endedAt = 5L * 60L * 60L * 1000L,
                startPercent = 80,
                endPercent = 80,
                drainPercent = 0,
                durationMs = 5L * 60L * 60L * 1000L,
                chargedDuringSleep = false,
                drainMah = 37.0,
                preciseDrainPercent = 0.62,
                preciseBatteryChangePercent = -0.62
            )

        assertEquals(
            0.62,
            BatterySleepStore.effectiveDrainPercent(session)!!,
            0.000001
        )
        assertEquals(0.124, session.drainPerHour!!, 0.000001)
    }

    @Test
    fun legacyZeroPercentWithoutMeasuredDrainIsNotAStatsSample() {
        val session =
            BatterySleepStore.SleepSession(
                startedAt = 0L,
                endedAt = 5L * 60L * 60L * 1000L,
                startPercent = 80,
                endPercent = 80,
                drainPercent = 0,
                durationMs = 5L * 60L * 60L * 1000L,
                chargedDuringSleep = false,
                drainMah = null
            )

        assertNull(BatterySleepStore.effectiveDrainPercent(session))
        assertNull(session.drainPerHour)
        assertTrue(
            BatterySleepStore.sessionAnalyticsEligible(session)
        )
        assertEquals(
            "no measurable battery decrease",
            BatterySleepStore.drainSampleExclusionReason(session)
        )
    }

    @Test
    fun legacyIntegerDrainStillWorksWhenNoPreciseMeasurementExists() {
        val session =
            BatterySleepStore.SleepSession(
                startedAt = 0L,
                endedAt = 5L * 60L * 60L * 1000L,
                startPercent = 80,
                endPercent = 79,
                drainPercent = 1,
                durationMs = 5L * 60L * 60L * 1000L,
                chargedDuringSleep = false,
                drainMah = null
            )

        assertEquals(
            1.0,
            BatterySleepStore.effectiveDrainPercent(session)!!,
            0.000001
        )
        assertEquals(0.2, session.drainPerHour!!, 0.000001)
    }

    @Test
    fun measuredMahCanUpgradeExistingHistory() {
        assertEquals(
            0.6166666667,
            BatterySleepStore.deriveDrainPercentFromMah(
                drainMah = 37.0,
                capacityMah = 6000.0
            )!!,
            0.000001
        )
    }

    @Test
    fun zeroMahIsNotTreatedAsExactZeroDrain() {
        assertNull(
            BatterySleepStore.deriveDrainPercentFromMah(
                drainMah = 0.0,
                capacityMah = 6000.0
            )
        )
    }
}
