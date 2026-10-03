package com.med.sleepmanager.service

/**
 * In-memory runtime state for Tailscale sleep/wake verification.
 *
 * Handler scheduling, wake locks, Tailscale calls, persistent connector
 * ownership, logging and restore orchestration remain in SleepManagerService.
 */
internal class TailscaleVerificationRuntimeState {
    var sleepAttempts: Int = 0
        private set

    var wakeAttempts: Int = 0
        private set

    var needsWakeRestore: Boolean = false
        private set

    fun resetSleepAttempts() {
        sleepAttempts = 0
    }

    fun incrementSleepAttempts(): Int {
        sleepAttempts++
        return sleepAttempts
    }

    fun resetWakeAttempts() {
        wakeAttempts = 0
    }

    fun incrementWakeAttempts(): Int {
        wakeAttempts++
        return wakeAttempts
    }

    fun markWakeRestoreNeeded() {
        needsWakeRestore = true
    }

    fun consumeWakeRestoreNeeded(): Boolean {
        val needed = needsWakeRestore
        needsWakeRestore = false
        return needed
    }

    fun reset() {
        sleepAttempts = 0
        wakeAttempts = 0
        needsWakeRestore = false
    }
}
