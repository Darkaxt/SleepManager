package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTransitionHoldsTest {
    private class FakeHold : TimedHold {
        var acquiredTimeoutMs: Long? = null
        var held = false

        override fun acquire(timeoutMs: Long) {
            acquiredTimeoutMs = timeoutMs
            held = true
        }

        override fun release() {
            held = false
        }
    }

    private val created = mutableListOf<Pair<SleepTransitionHold, FakeHold>>()
    private val holds =
        SleepTransitionHolds { reason -> FakeHold().also { created += reason to it } }

    private fun latest(reason: SleepTransitionHold): FakeHold =
        created.last { it.first == reason }.second

    @Test
    fun acquiringOneReasonDoesNotEndAnotherReasonsHold() {
        holds.acquire(SleepTransitionHold.SYNC_STOP_WAIT, 13_000L)
        holds.acquire(SleepTransitionHold.TAILSCALE_VERIFY, 5_000L)

        assertTrue(latest(SleepTransitionHold.SYNC_STOP_WAIT).held)
        assertTrue(latest(SleepTransitionHold.TAILSCALE_VERIFY).held)
    }

    @Test
    fun releasingOneReasonKeepsTheOthers() {
        holds.acquire(SleepTransitionHold.SYNC_STOP_WAIT, 13_000L)
        holds.acquire(SleepTransitionHold.TAILSCALE_VERIFY, 5_000L)

        holds.release(SleepTransitionHold.TAILSCALE_VERIFY)

        assertTrue(latest(SleepTransitionHold.SYNC_STOP_WAIT).held)
        assertFalse(latest(SleepTransitionHold.TAILSCALE_VERIFY).held)
        assertTrue(holds.isHeld(SleepTransitionHold.SYNC_STOP_WAIT))
    }

    @Test
    fun reacquiringTheSameReasonReplacesOnlyThatHold() {
        holds.acquire(SleepTransitionHold.TAILSCALE_VERIFY, 5_000L)
        val first = latest(SleepTransitionHold.TAILSCALE_VERIFY)

        holds.acquire(SleepTransitionHold.TAILSCALE_VERIFY, 6_000L)

        assertFalse(first.held)
        assertEquals(6_000L, latest(SleepTransitionHold.TAILSCALE_VERIFY).acquiredTimeoutMs)
        assertTrue(latest(SleepTransitionHold.TAILSCALE_VERIFY).held)
    }

    @Test
    fun everyAcquireIsTimed() {
        holds.acquire(SleepTransitionHold.SLEEP_DELAY, 18_000L)

        assertEquals(18_000L, latest(SleepTransitionHold.SLEEP_DELAY).acquiredTimeoutMs)
    }

    @Test
    fun pipelineReleaseKeepsTailscaleVerification() {
        holds.acquire(SleepTransitionHold.SYNC_STOP_WAIT, 13_000L)
        holds.acquire(SleepTransitionHold.SYNCTHING_PROBE, 10_000L)
        holds.acquire(SleepTransitionHold.TAILSCALE_VERIFY, 5_000L)

        holds.release(SleepTransitionHold.PIPELINE)

        assertFalse(latest(SleepTransitionHold.SYNC_STOP_WAIT).held)
        assertFalse(latest(SleepTransitionHold.SYNCTHING_PROBE).held)
        assertTrue(latest(SleepTransitionHold.TAILSCALE_VERIFY).held)
    }

    @Test
    fun releaseAllEndsEveryHold() {
        holds.acquire(SleepTransitionHold.SYNC_STOP_WAIT, 13_000L)
        holds.acquire(SleepTransitionHold.TAILSCALE_VERIFY, 5_000L)

        holds.releaseAll()

        assertTrue(created.none { it.second.held })
        assertFalse(holds.isHeld(SleepTransitionHold.TAILSCALE_VERIFY))
    }

    @Test
    fun unavailablePowerServiceRecordsNoHold() {
        val unavailable = SleepTransitionHolds { null }

        assertFalse(unavailable.acquire(SleepTransitionHold.SLEEP_DELAY, 1_000L))
        assertFalse(unavailable.isHeld(SleepTransitionHold.SLEEP_DELAY))
    }
}
