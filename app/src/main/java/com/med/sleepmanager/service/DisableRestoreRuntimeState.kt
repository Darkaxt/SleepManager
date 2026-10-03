package com.med.sleepmanager.service

/**
 * In-memory runtime state for disable-and-restore coordination.
 *
 * The service keeps ownership of scheduling, restore side effects, persistent
 * stores, network work and final shutdown. This class only groups the transient
 * flags that describe the disable flow while the service process is alive.
 */
internal class DisableRestoreRuntimeState {
    var isRequested: Boolean = false
        private set

    var isInitializing: Boolean = false
        private set

    var forceStopRequested: Boolean = false
        private set

    fun begin() {
        isRequested = true
        isInitializing = true
        forceStopRequested = false
    }

    fun markInitializationComplete() {
        isInitializing = false
    }

    fun requestForceStop() {
        forceStopRequested = true
    }

    /**
     * Matches the successful finish path: initialization is already complete,
     * so only request ownership and the accumulated force-stop request reset.
     */
    fun complete() {
        isRequested = false
        forceStopRequested = false
    }

    /**
     * Preserves onDestroy ordering: the disable request is cleared before the
     * remaining callbacks and retry state are torn down.
     */
    fun clearRequest() {
        isRequested = false
    }

    /**
     * Completes the later onDestroy reset without changing request ownership.
     */
    fun clearTransientFlags() {
        isInitializing = false
        forceStopRequested = false
    }
}
