package com.med.sleepmanager.service

/**
 * Tracks whether the custom pre-sleep delay is currently armed.
 *
 * Scheduling still belongs to SleepManagerService. This class only owns the
 * in-memory pending flag used by duplicate-screen-off suppression and wake
 * decisions.
 */
internal class SleepDelayState {
    var isPending: Boolean = false
        private set

    fun markPending() {
        isPending = true
    }

    fun clear() {
        isPending = false
    }
}
