package com.med.sleepmanager.service

/**
 * Transient runtime state used by clamshell closed-lid protection.
 *
 * Lid detection and display monitoring remain in SleepManagerService. This
 * class only groups the closed-lid keep-awake/sleep intent flags, the in-flight
 * lock request flag and the lock cooldown timestamp.
 */
internal class ClosedLidRuntimeState {
    var awakeOverride: Boolean = false
        private set

    var sleepIntent: Boolean = false
        private set

    var sleepRequestPending: Boolean = false
        private set

    var lastLockAtElapsed: Long = 0L
        private set

    fun markAwakeOverride() {
        awakeOverride = true
    }

    fun clearAwakeOverride() {
        awakeOverride = false
    }

    fun markSleepIntent() {
        sleepIntent = true
    }

    fun clearSleepIntent() {
        sleepIntent = false
    }

    fun markSleepRequestPending() {
        sleepRequestPending = true
    }

    fun clearSleepRequestPending() {
        sleepRequestPending = false
    }

    fun recordLock(nowElapsed: Long) {
        lastLockAtElapsed = nowElapsed
    }
}
