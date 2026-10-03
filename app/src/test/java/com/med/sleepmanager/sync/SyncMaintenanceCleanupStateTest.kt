package com.med.sleepmanager.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMaintenanceCleanupStateTest {
    private fun snapshot(): SyncMaintenanceSnapshot =
        SyncMaintenanceSnapshot(
            trigger = SyncMaintenanceTrigger.PERIODIC_SLEEP,
            phase = SyncMaintenancePhase.FINISHED,
            outcome = SyncMaintenanceOutcome.COMPLETED,
            startedProviderIds = setOf("basic"),
            completedProviderIds = setOf("basic"),
            failedProviderIds = emptySet()
        )

    @Test
    fun cleanupPendingCanBeClearedWithoutLosingTemporaryWifiOwnership() {
        val state = SyncMaintenanceCleanupState()
        val snapshot = snapshot()

        state.recordTemporaryWifiRequest(true)
        state.beginCleanup(snapshot)
        state.clearCleanupPending()

        assertTrue(state.temporaryWifiOnRequested)
        assertFalse(state.cleanupPending)
        assertNull(state.pendingFinalSnapshot)
    }

    @Test
    fun stopConfirmationStateIsIndependentFromCleanupPendingState() {
        val state = SyncMaintenanceCleanupState()
        val snapshot = snapshot()

        state.recordTemporaryWifiRequest(true)
        state.rememberPendingFinalSnapshot(snapshot)
        state.startStopConfirmation(1234L)
        state.clearStopConfirmation()

        assertTrue(state.temporaryWifiOnRequested)
        assertFalse(state.cleanupPending)
        assertSame(snapshot, state.pendingFinalSnapshot)
        assertNull(state.stopConfirmationStartedAtMs)
    }

    @Test
    fun beginCleanupStoresFinalSnapshotAndMarksCleanupPending() {
        val state = SyncMaintenanceCleanupState()
        val snapshot = snapshot()

        state.beginCleanup(snapshot)

        assertTrue(state.cleanupPending)
        assertSame(snapshot, state.pendingFinalSnapshot)
    }

    @Test
    fun resetClearsAllCleanupRuntimeState() {
        val state = SyncMaintenanceCleanupState()
        val snapshot = snapshot()

        state.recordTemporaryWifiRequest(true)
        state.beginCleanup(snapshot)
        state.startStopConfirmation(4567L)

        state.reset()

        assertFalse(state.temporaryWifiOnRequested)
        assertFalse(state.cleanupPending)
        assertNull(state.pendingFinalSnapshot)
        assertNull(state.stopConfirmationStartedAtMs)
    }

    @Test
    fun temporaryWifiRequestTracksHelperSendResult() {
        val state = SyncMaintenanceCleanupState()

        state.recordTemporaryWifiRequest(true)
        assertTrue(state.temporaryWifiOnRequested)

        state.recordTemporaryWifiRequest(false)
        assertFalse(state.temporaryWifiOnRequested)
        assertNull(state.pendingFinalSnapshot)
    }
}
