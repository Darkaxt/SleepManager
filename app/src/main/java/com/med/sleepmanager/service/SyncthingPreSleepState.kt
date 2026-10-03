package com.med.sleepmanager.service

/**
 * Tracks the generation of the asynchronous Syncthing pre-sleep probe.
 *
 * Wakes/cancels invalidate the current generation so a late probe result cannot
 * continue a stale sleep transition.
 */
internal class SyncthingPreSleepState {
    private var generation = 0L

    fun nextProbeGeneration(): Long {
        generation++
        return generation
    }

    fun isCurrent(candidate: Long): Boolean =
        candidate == generation

    fun invalidate() {
        generation++
    }
}
