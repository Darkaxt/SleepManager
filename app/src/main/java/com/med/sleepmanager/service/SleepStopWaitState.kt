package com.med.sleepmanager.service

/**
 * Mutable state for the STOP-confirmation gate that runs before disruptive
 * sleep actions. The service still owns orchestration; this class only keeps
 * the pending options, timing and async verification generation together.
 */
internal class SleepStopWaitState {
    var pendingWifi = false
    var pendingBluetooth = false
    var pendingSyncthing = false
    var pendingBasicSync = false
    var pendingPostStopActions = false

    var startedAtElapsed = 0L
    var lastBasicSyncStateRequestAtElapsed = 0L
    var syncthingProbeInFlight = false
    var syncthingConfirmed = false
    var generation = 0L

    fun begin(
        nowElapsed: Long,
        elapsedBeforeRecoveryMs: Long,
        timeoutMs: Long
    ) {
        val recoveredElapsed = elapsedBeforeRecoveryMs.coerceIn(0L, timeoutMs)
        generation++
        startedAtElapsed = nowElapsed - recoveredElapsed
        lastBasicSyncStateRequestAtElapsed = 0L
        syncthingProbeInFlight = false
        syncthingConfirmed = !pendingSyncthing
    }

    fun clearPendingActions() {
        pendingWifi = false
        pendingBluetooth = false
        pendingSyncthing = false
        pendingBasicSync = false
        pendingPostStopActions = false
    }

    fun clear() {
        generation++
        clearPendingActions()
        startedAtElapsed = 0L
        lastBasicSyncStateRequestAtElapsed = 0L
        syncthingProbeInFlight = false
        syncthingConfirmed = false
    }
}
