package com.med.sleepmanager.service

/**
 * Tracks whether a wake-triggered sync-and-stop maintenance run is pending.
 *
 * Persistent arming remains in SyncTransitionStore. This class only owns the
 * transient in-process pending flag after a real wake has been evaluated.
 */
internal class WakeTransitionSyncState {
    var isPending: Boolean = false
        private set

    fun arm(
        wakeSyncWasArmed: Boolean,
        syncThenStopAvailable: Boolean
    ) {
        isPending = wakeSyncWasArmed && syncThenStopAvailable
    }

    fun clear() {
        isPending = false
    }
}
