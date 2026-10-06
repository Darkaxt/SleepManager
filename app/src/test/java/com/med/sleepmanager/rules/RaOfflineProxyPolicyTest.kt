package com.med.sleepmanager.rules

import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyCommandResult
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyQueueState
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyQueueStatus
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaOfflineProxyPolicyTest {
    @Test
    fun alreadyStoppedIsUntouched() {
        assertEquals(
            RaOfflineProxyPreSleepDecision.LEAVE_UNTOUCHED,
            RaOfflineProxyPolicy.preSleepDecision(
                status(
                    running = false,
                    shouldBeRunning = false,
                    queue = RaOfflineProxyQueueState.IDLE
                )
            )
        )
    }

    @Test
    fun idleAndBlockedAreSafeToStop() {
        for (
            queue in listOf(
                RaOfflineProxyQueueState.IDLE,
                RaOfflineProxyQueueState.BLOCKED
            )
        ) {
            assertEquals(
                RaOfflineProxyPreSleepDecision.STOP_NOW,
                RaOfflineProxyPolicy.preSleepDecision(
                    status(queue = queue)
                )
            )
        }
    }

    @Test
    fun cachingAndWaitingKeepProxyAndNetworkAvailable() {
        for (
            queue in listOf(
                RaOfflineProxyQueueState.CACHING,
                RaOfflineProxyQueueState.WAITING
            )
        ) {
            assertEquals(
                RaOfflineProxyPreSleepDecision.WAIT_FOR_QUEUE,
                RaOfflineProxyPolicy.preSleepDecision(
                    status(queue = queue)
                )
            )
        }
    }

    @Test
    fun unknownStatusFailsSafe() {
        assertEquals(
            RaOfflineProxyPreSleepDecision.WAIT_FOR_SAFE_STATUS,
            RaOfflineProxyPolicy.preSleepDecision(null)
        )
        assertEquals(
            RaOfflineProxyPreSleepDecision.WAIT_FOR_SAFE_STATUS,
            RaOfflineProxyPolicy.preSleepDecision(
                status(queue = RaOfflineProxyQueueState.UNKNOWN)
            )
        )
    }

    @Test
    fun unsupportedApiIsRejected() {
        assertEquals(
            RaOfflineProxyPreSleepDecision.UNSUPPORTED_API,
            RaOfflineProxyPolicy.preSleepDecision(
                status(version = 2)
            )
        )
    }

    @Test
    fun acceptedStopTakesOwnershipBeforeRunningTurnsFalse() {
        val initial = status()
        val stopStatus =
            status(
                running = true,
                shouldBeRunning = false
            )
        val result =
            RaOfflineProxyCommandResult(
                code = RaOfflineProxyCommandResult.RESULT_OK,
                status = stopStatus
            )

        assertTrue(
            RaOfflineProxyPolicy.shouldTakeStopOwnership(
                initial,
                result
            )
        )
        assertFalse(
            RaOfflineProxyPolicy.canReleaseManagedWifi(stopStatus)
        )
    }

    @Test
    fun wifiCanOnlyTurnOffAfterStopIsConfirmed() {
        assertFalse(
            RaOfflineProxyPolicy.canReleaseManagedWifi(
                status(
                    running = true,
                    shouldBeRunning = false
                )
            )
        )
        assertTrue(
            RaOfflineProxyPolicy.canReleaseManagedWifi(
                status(
                    running = false,
                    shouldBeRunning = false
                )
            )
        )
    }

    @Test
    fun restoreOnlyCompletesWhenIntentAndRuntimeAreRunning() {
        assertFalse(
            RaOfflineProxyPolicy.restoreConfirmed(
                status(
                    running = false,
                    shouldBeRunning = true
                )
            )
        )
        assertTrue(
            RaOfflineProxyPolicy.restoreConfirmed(
                status(
                    running = true,
                    shouldBeRunning = true
                )
            )
        )
    }

    @Test
    fun backgroundStartRestrictionIsExplicit() {
        assertEquals(
            RaOfflineProxyRestoreDecision.NEEDS_UNRESTRICTED_BATTERY,
            RaOfflineProxyPolicy.restoreDecision(
                owned = true,
                lastResultCode =
                    RaOfflineProxyCommandResult
                        .RESULT_FOREGROUND_SERVICE_NOT_ALLOWED
            )
        )
    }

    private fun status(
        version: Int = 1,
        running: Boolean = true,
        shouldBeRunning: Boolean = true,
        queue: RaOfflineProxyQueueState =
            RaOfflineProxyQueueState.IDLE
    ) =
        RaOfflineProxyStatus(
            version = version,
            running = running,
            shouldBeRunning = shouldBeRunning,
            online = running,
            queue =
                RaOfflineProxyQueueStatus(
                    count =
                        if (
                            queue == RaOfflineProxyQueueState.CACHING ||
                            queue == RaOfflineProxyQueueState.WAITING
                        ) {
                            10
                        } else {
                            0
                        },
                    state = queue,
                    nextWindowAt = null
                )
        )
}
