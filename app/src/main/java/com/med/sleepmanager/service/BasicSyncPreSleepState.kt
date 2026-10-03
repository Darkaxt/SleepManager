package com.med.sleepmanager.service

/**
 * Mutable state used while preparing a fresh sleep transition around BasicSync.
 *
 * The service keeps all scheduling and controller calls. This class only groups
 * the fresh-state probe generation and the active-sync wait bookkeeping so a
 * wake/cancel can invalidate stale callbacks without changing orchestration.
 */
internal class BasicSyncPreSleepState {
    var waitingForActiveSync = false
    var waitStartedAtElapsed = 0L
    var syncedSinceElapsed = 0L
    var freshProbeGeneration = 0L

    fun beginActiveWait(nowElapsed: Long) {
        waitingForActiveSync = true
        waitStartedAtElapsed = nowElapsed
        syncedSinceElapsed = 0L
    }

    fun cancelActiveWait() {
        freshProbeGeneration++
        waitingForActiveSync = false
        waitStartedAtElapsed = 0L
        syncedSinceElapsed = 0L
    }
}
