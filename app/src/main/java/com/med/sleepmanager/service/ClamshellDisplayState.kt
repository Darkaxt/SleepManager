package com.med.sleepmanager.service

/**
 * In-memory display/dock state used by clamshell handling.
 *
 * SleepManagerService still owns DisplayManager registration and all policy
 * decisions. This class only groups listener registration plus the latest
 * external-display connected/active snapshot.
 */
internal class ClamshellDisplayState {
    var listenerRegistered: Boolean = false
        private set

    var externalDisplayConnected: Boolean = false
        private set

    var externalDisplayActive: Boolean = false
        private set

    fun registerListener(
        connected: Boolean,
        active: Boolean
    ) {
        listenerRegistered = true
        setExternalDisplay(connected, active)
    }

    fun unregisterListener() {
        listenerRegistered = false
        clearExternalDisplay()
    }

    /**
     * Updates the latest display snapshot and returns whether an external
     * display was connected immediately before this update. The service uses
     * that previous value to preserve disconnect-debounce behavior.
     */
    fun updateExternalDisplay(
        connected: Boolean,
        active: Boolean
    ): Boolean {
        val wasConnected = externalDisplayConnected
        setExternalDisplay(connected, active)
        return wasConnected
    }

    fun clearExternalDisplay() {
        setExternalDisplay(
            connected = false,
            active = false
        )
    }

    private fun setExternalDisplay(
        connected: Boolean,
        active: Boolean
    ) {
        externalDisplayConnected = connected
        externalDisplayActive = active
    }
}
