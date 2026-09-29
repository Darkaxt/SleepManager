package com.med.sleepmanager.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepWakePolicyTest {
    @Test
    fun closedLidFalseWake_doesNotCancelGraceOrRestore() {
        val decision =
            SleepWakePolicy.onScreenOn(
                thorProtectionEnabled = true,
                lidClosed = true,
                sleepDelayPending = true
            )

        assertTrue(decision.suppressWake)
        assertTrue(decision.requestThorLock)
        assertFalse(decision.cancelSleepDelay)
        assertFalse(decision.restoreNormalWake)
    }

    @Test
    fun realWakeWithOpenLid_cancelsGraceAndRestores() {
        val decision =
            SleepWakePolicy.onScreenOn(
                thorProtectionEnabled = true,
                lidClosed = false,
                sleepDelayPending = true
            )

        assertFalse(decision.suppressWake)
        assertFalse(decision.requestThorLock)
        assertTrue(decision.cancelSleepDelay)
        assertTrue(decision.restoreNormalWake)
    }

    @Test
    fun closedLidFalseWake_isSuppressedEvenWithoutGrace() {
        val decision =
            SleepWakePolicy.onScreenOn(
                thorProtectionEnabled = true,
                lidClosed = true,
                sleepDelayPending = false
            )

        assertTrue(decision.suppressWake)
        assertFalse(decision.cancelSleepDelay)
        assertFalse(decision.restoreNormalWake)
    }


    @Test
    fun batterySaverAlone_waitsForSyncthingStop() {
        assertTrue(
            SleepWakePolicy.shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                wifiManaged = false,
                helperAvailable = false,
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
                helperAvailable = false,
                batterySaverWillEnable = true,
                syncthingStopRequested = false,
                basicSyncStopRequested = true
            )
        )
    }

    @Test
    fun wifiWithHelper_keepsExistingStopGate() {
        assertTrue(
            SleepWakePolicy.shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                wifiManaged = true,
                helperAvailable = true,
                batterySaverWillEnable = false,
                syncthingStopRequested = true,
                basicSyncStopRequested = false
            )
        )
    }

    @Test
    fun wifiWithoutHelper_doesNotPretendItCanDisruptNetworking() {
        assertFalse(
            SleepWakePolicy.shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                wifiManaged = true,
                helperAvailable = false,
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
                helperAvailable = true,
                batterySaverWillEnable = false,
                syncthingStopRequested = true,
                basicSyncStopRequested = false
            )
        )
    }

    @Test
    fun batterySaver_waitsForPendingTailscaleDisconnectVerification() {
        assertTrue(
            SleepWakePolicy.shouldWaitForTailscaleBeforeDisruptiveSleepAction(
                helperAvailable = false,
                batterySaverWillEnable = true,
                tailscaleVerificationPending = true
            )
        )
    }

    @Test
    fun thorFalseWake_isStillEffectivelySleeping() {
        assertTrue(
            SleepWakePolicy.isSuppressedThorFalseWake(
                interactive = true,
                thorProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = false
            )
        )
        assertTrue(
            SleepWakePolicy.isEffectivelySleeping(
                interactive = true,
                thorProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = false
            )
        )
        assertFalse(
            SleepWakePolicy.isRealWake(
                interactive = true,
                thorProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = false
            )
        )
    }

    @Test
    fun dockedClosedLidWake_isARealWake() {
        assertFalse(
            SleepWakePolicy.isSuppressedThorFalseWake(
                interactive = true,
                thorProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = true
            )
        )
        assertFalse(
            SleepWakePolicy.isEffectivelySleeping(
                interactive = true,
                thorProtectionEnabled = true,
                lidClosed = true,
                bypassClosedLidProtection = true
            )
        )
        assertTrue(
            SleepWakePolicy.isRealWake(
                interactive = true,
                thorProtectionEnabled = true,
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
                thorProtectionEnabled = true,
                lidClosed = false,
                bypassClosedLidProtection = false
            )
        )
    }

}
