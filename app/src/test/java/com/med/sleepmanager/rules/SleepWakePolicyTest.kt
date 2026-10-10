package com.med.sleepmanager.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepWakePolicyTest {
    @Test
    fun closedLidFalseWake_doesNotCancelGraceOrRestore() {
        val decision =
            SleepWakePolicy.onScreenOn(
                closedLidProtectionEnabled = true,
                lidClosed = true,
                sleepDelayPending = true
            )

        assertTrue(decision.suppressWake)
        assertTrue(decision.requestClosedLidLock)
        assertFalse(decision.cancelSleepDelay)
        assertFalse(decision.restoreNormalWake)
    }

    @Test
    fun realWakeWithOpenLid_cancelsGraceAndRestores() {
        val decision =
            SleepWakePolicy.onScreenOn(
                closedLidProtectionEnabled = true,
                lidClosed = false,
                sleepDelayPending = true
            )

        assertFalse(decision.suppressWake)
        assertFalse(decision.requestClosedLidLock)
        assertTrue(decision.cancelSleepDelay)
        assertTrue(decision.restoreNormalWake)
    }

    @Test
    fun closedLidFalseWake_isSuppressedEvenWithoutGrace() {
        val decision =
            SleepWakePolicy.onScreenOn(
                closedLidProtectionEnabled = true,
                lidClosed = true,
                sleepDelayPending = false
            )

        assertTrue(decision.suppressWake)
        assertFalse(decision.cancelSleepDelay)
        assertFalse(decision.restoreNormalWake)
    }


    @Test
    fun falseWakeWithExistingCycle_preservesSleepTransactionForResleep() {
        assertTrue(
            SleepWakePolicy.shouldPreserveSleepTransactionOnFalseWake(
                suppressWake = true,
                sleepDelayPending = false,
                cycleActive = true,
                actionsApplied = true,
                stopWaitPending = false
            )
        )
    }

    @Test
    fun falseWakeDuringGrace_preservesPendingSleepWorkForResleep() {
        assertTrue(
            SleepWakePolicy.shouldPreserveSleepTransactionOnFalseWake(
                suppressWake = true,
                sleepDelayPending = true,
                cycleActive = false,
                actionsApplied = false,
                stopWaitPending = false
            )
        )
    }

    @Test
    fun suppressedWakeWithoutSleepWork_doesNotSkipFutureFreshSleep() {
        assertFalse(
            SleepWakePolicy.shouldPreserveSleepTransactionOnFalseWake(
                suppressWake = true,
                sleepDelayPending = false,
                cycleActive = false,
                actionsApplied = false,
                stopWaitPending = false
            )
        )
    }

    @Test
    fun batterySaverAlone_waitsForSyncthingStop() {
        assertTrue(
            SleepWakePolicy.shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                wifiManaged = false,
                radioControlAvailable = false,
                batterySaverWillEnable = true,
                syncthingStopRequested = true,
                basicSyncStopRequested = false
            )
        )
    }

    @Test
    fun batterySaverAlone_waitsForBasicSyncStop() {
        assertTrue(
            SleepWakePolicy.shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                wifiManaged = false,
                radioControlAvailable = false,
                batterySaverWillEnable = true,
                syncthingStopRequested = false,
                basicSyncStopRequested = true
            )
        )
    }

    @Test
    fun wifiWithRadioControl_keepsExistingStopGate() {
        assertTrue(
            SleepWakePolicy.shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                wifiManaged = true,
                radioControlAvailable = true,
                batterySaverWillEnable = false,
                syncthingStopRequested = true,
                basicSyncStopRequested = false
            )
        )
    }

    @Test
    fun wifiWithoutRadioControl_doesNotPretendItCanDisruptNetworking() {
        assertFalse(
            SleepWakePolicy.shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                wifiManaged = true,
                radioControlAvailable = false,
                batterySaverWillEnable = false,
                syncthingStopRequested = true,
                basicSyncStopRequested = false
            )
        )
    }

    @Test
    fun bluetoothOnly_doesNotCreateSyncStopGate() {
        assertFalse(
            SleepWakePolicy.shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                wifiManaged = false,
                radioControlAvailable = true,
                batterySaverWillEnable = false,
                syncthingStopRequested = true,
                basicSyncStopRequested = false
            )
        )
    }

    @Test
    fun pendingPostStopActions_preserveFreshSleepTransaction() {
        assertFalse(
            SleepWakePolicy.shouldCompleteSleepCycleAfterFreshActions(
                postStopActionsPending = true
            )
        )
        assertTrue(
            SleepWakePolicy.shouldCompleteSleepCycleAfterFreshActions(
                postStopActionsPending = false
            )
        )
    }

    @Test
    fun batterySaver_waitsForPendingTailscaleDisconnectVerification() {
        assertTrue(
            SleepWakePolicy.shouldWaitForTailscaleBeforeDisruptiveSleepAction(
                radioControlAvailable = false,
                batterySaverWillEnable = true,
                tailscaleVerificationPending = true
            )
        )
    }

    @Test
    fun closedLidFalseWake_isStillEffectivelySleeping() {
        assertTrue(
            SleepWakePolicy.isSuppressedClosedLidFalseWake(
                interactive = true,
                closedLidProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = false
            )
        )
        assertTrue(
            SleepWakePolicy.isEffectivelySleeping(
                interactive = true,
                closedLidProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = false
            )
        )
        assertFalse(
            SleepWakePolicy.isRealWake(
                interactive = true,
                closedLidProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = false
            )
        )
    }

    @Test
    fun dockedClosedLidWake_isARealWake() {
        assertFalse(
            SleepWakePolicy.isSuppressedClosedLidFalseWake(
                interactive = true,
                closedLidProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = true
            )
        )
        assertFalse(
            SleepWakePolicy.isEffectivelySleeping(
                interactive = true,
                closedLidProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = true
            )
        )
        assertTrue(
            SleepWakePolicy.isRealWake(
                interactive = true,
                closedLidProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = true
            )
        )
    }

    @Test
    fun openLidInteractiveState_isARealWake() {
        assertTrue(
            SleepWakePolicy.isRealWake(
                interactive = true,
                closedLidProtectionEnabled = true,
                lidClosed = false,
                bypassClosedLidProtection = false
            )
        )
    }

}
