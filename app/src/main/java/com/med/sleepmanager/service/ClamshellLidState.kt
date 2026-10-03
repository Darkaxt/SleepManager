package com.med.sleepmanager.service

/**
 * In-memory lid state for clamshell handling.
 *
 * The service still owns the input monitor and persistence. This class only
 * centralizes the current closed/open snapshot and the recovery rule used when
 * the input device cannot provide an immediate lid state.
 */
internal class ClamshellLidState {
    @Volatile
    var isClosed: Boolean = false
        private set

    fun initialize(
        currentLidState: Boolean?,
        interactive: Boolean?,
        lastKnownLidClosed: Boolean?
    ) {
        isClosed =
            currentLidState
                ?: if (interactive == false) {
                    lastKnownLidClosed ?: false
                } else {
                    false
                }
    }

    fun markClosed() {
        isClosed = true
    }

    fun markOpened() {
        isClosed = false
    }

    fun clear() {
        isClosed = false
    }
}
