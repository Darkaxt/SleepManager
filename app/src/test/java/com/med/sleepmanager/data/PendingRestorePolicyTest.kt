package com.med.sleepmanager.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRestorePolicyTest {
    private fun pending(
        cycleActive: Boolean = false,
        batterySaverOwned: Boolean = false,
        chargingSeparationOwned: Boolean = false,
        basicSyncOwned: Boolean = false
    ) =
        PendingRestorePolicy.isPending(
            cycleActive = cycleActive,
            batterySaverOwned = batterySaverOwned,
            chargingSeparationOwned = chargingSeparationOwned,
            basicSyncOwned = basicSyncOwned
        )

    @Test
    fun nothingOwnedIsNotPending() {
        assertFalse(pending())
    }

    @Test
    fun activeSleepCycleIsPending() {
        assertTrue(pending(cycleActive = true))
    }

    @Test
    fun ownedDeviceControlsArePending() {
        assertTrue(pending(batterySaverOwned = true))
        assertTrue(pending(chargingSeparationOwned = true))
    }

    @Test
    fun ownedBasicSyncStateIsPending() {
        assertTrue(pending(basicSyncOwned = true))
    }
}
