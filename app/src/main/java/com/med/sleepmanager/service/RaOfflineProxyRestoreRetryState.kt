package com.med.sleepmanager.service

internal enum class RaOfflineProxyRestoreRetryDecision {
    COMPLETE,
    RETRY,
    EXHAUSTED
}

/**
 * Bounded in-memory retry bookkeeping. Durable ownership stays in
 * SleepCycleStore and therefore survives process death.
 */
internal class RaOfflineProxyRestoreRetryState {
    var isPending: Boolean = false
        private set

    var attempts: Int = 0
        private set

    fun markAttemptStarted() {
        isPending = true
    }

    fun onRestoreResult(
        confirmed: Boolean,
        retryable: Boolean,
        maxAttempts: Int
    ): RaOfflineProxyRestoreRetryDecision {
        if (confirmed) {
            reset()
            return RaOfflineProxyRestoreRetryDecision.COMPLETE
        }

        if (!retryable) {
            reset()
            return RaOfflineProxyRestoreRetryDecision.EXHAUSTED
        }

        attempts++
        if (attempts < maxAttempts) {
            isPending = true
            return RaOfflineProxyRestoreRetryDecision.RETRY
        }

        reset()
        return RaOfflineProxyRestoreRetryDecision.EXHAUSTED
    }

    fun reset() {
        isPending = false
        attempts = 0
    }

    fun clearPending() {
        isPending = false
    }
}
