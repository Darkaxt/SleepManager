package com.med.sleepmanager.service

internal enum class RaOfflineProxyPreSleepPhase {
    NONE,
    WAITING_FOR_QUEUE,
    WAITING_FOR_STOP_CONFIRMATION
}

/**
 * Short-lived event-wait state for a single sleep transaction.
 *
 * No polling or wakelock ownership lives here. generation invalidates stale
 * ContentObserver callbacks when a real wake/cancel starts a new transaction.
 */
internal class RaOfflineProxyPreSleepState {
    var phase: RaOfflineProxyPreSleepPhase =
        RaOfflineProxyPreSleepPhase.NONE
        private set

    var cycleId: Long = 0L
        private set

    var generation: Long = 0L
        private set

    fun waitForQueue(cycleId: Long): Long {
        this.cycleId = cycleId
        phase = RaOfflineProxyPreSleepPhase.WAITING_FOR_QUEUE
        generation++
        return generation
    }

    fun waitForStopConfirmation(cycleId: Long): Long {
        this.cycleId = cycleId
        phase =
            RaOfflineProxyPreSleepPhase.WAITING_FOR_STOP_CONFIRMATION
        generation++
        return generation
    }

    fun accepts(
        callbackCycleId: Long,
        callbackGeneration: Long
    ): Boolean =
        phase != RaOfflineProxyPreSleepPhase.NONE &&
            cycleId == callbackCycleId &&
            generation == callbackGeneration

    fun clear() {
        generation++
        phase = RaOfflineProxyPreSleepPhase.NONE
        cycleId = 0L
    }
}
