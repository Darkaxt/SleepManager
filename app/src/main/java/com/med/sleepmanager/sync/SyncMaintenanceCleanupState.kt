package com.med.sleepmanager.sync

/**
 * Transient in-memory state used while a sync-maintenance session waits for
 * temporary Wi-Fi cleanup and provider STOP confirmation.
 *
 * Handler callbacks, Helper IPC, provider polling, timeouts and logging remain
 * in SyncMaintenanceRunner. The reset methods intentionally keep the original
 * field-by-field lifecycle semantics.
 */
internal class SyncMaintenanceCleanupState {
    var temporaryWifiOnRequested: Boolean = false
        private set

    var cleanupPending: Boolean = false
        private set

    var pendingFinalSnapshot: SyncMaintenanceSnapshot? = null
        private set

    var stopConfirmationStartedAtMs: Long? = null
        private set

    fun recordTemporaryWifiRequest(requested: Boolean) {
        temporaryWifiOnRequested = requested
    }

    fun beginCleanup(snapshot: SyncMaintenanceSnapshot) {
        pendingFinalSnapshot = snapshot
        cleanupPending = true
    }

    fun clearCleanupPending() {
        cleanupPending = false
        pendingFinalSnapshot = null
    }

    fun rememberPendingFinalSnapshot(snapshot: SyncMaintenanceSnapshot) {
        pendingFinalSnapshot = snapshot
    }

    fun startStopConfirmation(startedAtMs: Long) {
        stopConfirmationStartedAtMs = startedAtMs
    }

    fun clearStopConfirmation() {
        stopConfirmationStartedAtMs = null
    }

    fun reset() {
        temporaryWifiOnRequested = false
        cleanupPending = false
        pendingFinalSnapshot = null
        stopConfirmationStartedAtMs = null
    }
}
