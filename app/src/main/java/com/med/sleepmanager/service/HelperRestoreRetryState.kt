package com.med.sleepmanager.service

internal enum class HelperRestoreRetryDecision {
    RETRY,
    EXHAUSTED,
    STALE
}

/**
 * In-memory timer state for replaying the same cycle-correlated Helper restore
 * when its acknowledgement is lost. Persistent ownership remains in
 * SleepCycleStore and the Helper; this class only bounds replay attempts.
 */
internal class HelperRestoreRetryState {
    var pending: Boolean = false
        private set

    var cycleId: Long = 0L
        private set

    var attempts: Int = 0
        private set

    fun begin(cycleId: Long, requestSent: Boolean) {
        if (!requestSent || cycleId == 0L) {
            clear()
            return
        }

        pending = true
        this.cycleId = cycleId
        attempts = 1
    }

    fun decision(
        activeCycleId: Long,
        stillNeedsRestore: Boolean,
        maxAttempts: Int
    ): HelperRestoreRetryDecision {
        if (
            !pending ||
            cycleId == 0L ||
            activeCycleId != cycleId ||
            !stillNeedsRestore
        ) {
            return HelperRestoreRetryDecision.STALE
        }

        return if (attempts >= maxAttempts.coerceAtLeast(1)) {
            HelperRestoreRetryDecision.EXHAUSTED
        } else {
            HelperRestoreRetryDecision.RETRY
        }
    }

    fun recordRetrySent() {
        if (pending) attempts++
    }

    fun acknowledge(resultCycleId: Long) {
        if (pending && (resultCycleId == 0L || resultCycleId == cycleId)) {
            clear()
        }
    }

    fun clear() {
        pending = false
        cycleId = 0L
        attempts = 0
    }
}
