package com.med.sleepmanager.service

/**
 * Independent reasons for keeping the CPU awake during a sleep transition.
 * Each reason owns its own timed wake lock, so one flow (e.g. Tailscale
 * verification) can no longer end another flow's hold (e.g. the sync-stop wait).
 */
internal enum class SleepTransitionHold {
    SLEEP_DELAY,
    BASIC_SYNC_PROBE,
    SYNCTHING_PROBE,
    SYNC_STOP_WAIT,
    TAILSCALE_VERIFY;

    companion object {
        /**
         * Holds that end when the sleep radio step finishes. Tailscale
         * verification runs alongside that step (and continues into a wake)
         * and is released only by its own flow.
         */
        val PIPELINE: Set<SleepTransitionHold> =
            values().toSet() - TAILSCALE_VERIFY
    }
}

/** A timed wake lock; implemented with PowerManager.WakeLock by the service. */
internal interface TimedHold {
    fun acquire(timeoutMs: Long)
    fun release()
}

/**
 * Keeps at most one timed hold per [SleepTransitionHold]. Re-acquiring a reason
 * replaces only that reason's hold; releasing touches only the named reasons.
 */
internal class SleepTransitionHolds(
    private val newHold: (SleepTransitionHold) -> TimedHold?
) {
    private val held = mutableMapOf<SleepTransitionHold, TimedHold>()

    fun acquire(reason: SleepTransitionHold, timeoutMs: Long): Boolean {
        release(reason)
        val hold = newHold(reason) ?: return false
        hold.acquire(timeoutMs)
        held[reason] = hold
        return true
    }

    fun release(reason: SleepTransitionHold) {
        held.remove(reason)?.let { hold -> runCatching { hold.release() } }
    }

    fun release(reasons: Set<SleepTransitionHold>) {
        reasons.forEach(::release)
    }

    fun releaseAll() {
        release(held.keys.toSet())
    }

    fun isHeld(reason: SleepTransitionHold): Boolean = reason in held
}
