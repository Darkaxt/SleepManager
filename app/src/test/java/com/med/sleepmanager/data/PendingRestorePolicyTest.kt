package com.med.sleepmanager.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRestorePolicyTest {
    @Test
    fun nothingOwnedIsNotPending() {
        assertFalse(PendingRestorePolicy.isPending(cycleActive = false, ownedStatePending = false))
    }

    @Test
    fun activeSleepCycleIsPending() {
        assertTrue(PendingRestorePolicy.isPending(cycleActive = true, ownedStatePending = false))
    }

    @Test
    fun ownedStateIsPendingWithoutCycle() {
        assertTrue(PendingRestorePolicy.isPending(cycleActive = false, ownedStatePending = true))
    }

    @Test
    fun cycleCompletesOnlyWhenNothingBlocks() {
        assertTrue(
            PendingRestorePolicy.cycleRestored(
                blockingParticipantPending = false,
                anyConnectorChangeRecorded = false
            )
        )
        assertFalse(
            PendingRestorePolicy.cycleRestored(
                blockingParticipantPending = true,
                anyConnectorChangeRecorded = false
            )
        )
        // An unregistered connector's recorded change must still block.
        assertFalse(
            PendingRestorePolicy.cycleRestored(
                blockingParticipantPending = false,
                anyConnectorChangeRecorded = true
            )
        )
    }
}
