package com.med.sleepmanager.service

internal enum class BasicSyncRestoreRetryDecision {
    COMPLETE,
    RETRY,
    EXHAUSTED
}

/**
 * In-memory retry state for restoring a BasicSync state owned by SleepManager.
 *
 * The service still owns scheduling, observer lifetime, the actual restore
 * attempt and disable-flow side effects.
 */
internal class BasicSyncRestoreRetryState {
    var isPending: Boolean = false
        private set

    var attempts: Int = 0
        private set

    fun markAttemptStarted() {
        isPending = true
    }

    fun onRestoreResult(
        retryable: Boolean,
        maxAttempts: Int
    ): BasicSyncRestoreRetryDecision {
        if (!retryable) {
            isPending = false
            attempts = 0
            return BasicSyncRestoreRetryDecision.COMPLETE
        }

        attempts++
        if (attempts < maxAttempts) {
            isPending = true
            return BasicSyncRestoreRetryDecision.RETRY
        }

        isPending = false
        attempts = 0
        return BasicSyncRestoreRetryDecision.EXHAUSTED
    }

    fun reset() {
        isPending = false
        attempts = 0
    }

    /**
     * Matches service destruction semantics: pending work is no longer active,
     * while the attempt counter is left untouched because the process is ending.
     */
    fun clearPending() {
        isPending = false
    }
}
