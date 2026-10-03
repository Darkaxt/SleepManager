package com.med.sleepmanager.service

internal enum class DeviceControlRestoreRetryDecision {
    COMPLETE,
    RETRY,
    EXHAUSTED
}

/**
 * In-memory retry state for restoring SleepManager-owned device controls
 * during disable.
 *
 * Scheduling, actual restores and failure side effects remain in
 * SleepManagerService.
 */
internal class DeviceControlRestoreRetryState {
    var isPending: Boolean = false
        private set

    var attempts: Int = 0
        private set

    fun begin(restored: Boolean): DeviceControlRestoreRetryDecision {
        attempts = 0
        if (restored) {
            isPending = false
            return DeviceControlRestoreRetryDecision.COMPLETE
        }

        isPending = true
        attempts = 1
        return DeviceControlRestoreRetryDecision.RETRY
    }

    fun onRetryResult(
        restored: Boolean,
        maxAttempts: Int
    ): DeviceControlRestoreRetryDecision {
        if (restored) {
            isPending = false
            attempts = 0
            return DeviceControlRestoreRetryDecision.COMPLETE
        }

        attempts++
        if (attempts < maxAttempts) {
            isPending = true
            return DeviceControlRestoreRetryDecision.RETRY
        }

        isPending = false
        attempts = 0
        return DeviceControlRestoreRetryDecision.EXHAUSTED
    }

    fun cancel() {
        isPending = false
        attempts = 0
    }

    /**
     * Matches service destruction semantics: pending work is no longer active,
     * while the attempt counter is irrelevant because the service is ending.
     */
    fun clearPending() {
        isPending = false
    }
}
