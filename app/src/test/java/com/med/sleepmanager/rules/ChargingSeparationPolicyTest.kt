package com.med.sleepmanager.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingSeparationPolicyTest {
    @Test
    fun ownedApply_alreadyDisabledNeedsNoWrite() {
        assertEquals(
            ChargingSeparationOwnedApplyDecision.ALREADY_DISABLED,
            ChargingSeparationPolicy.ownedApplyDecision(
                previous = true,
                current = false
            )
        )
    }

    @Test
    fun ownedApply_reappliesOffWhenPreviousWasOnAndCurrentIsNotOff() {
        assertEquals(
            ChargingSeparationOwnedApplyDecision.REAPPLY_DISABLED,
            ChargingSeparationPolicy.ownedApplyDecision(
                previous = true,
                current = true
            )
        )
        assertEquals(
            ChargingSeparationOwnedApplyDecision.REAPPLY_DISABLED,
            ChargingSeparationPolicy.ownedApplyDecision(
                previous = true,
                current = null
            )
        )
    }

    @Test
    fun ownedApply_cannotConfirmWhenPreviousWasAlreadyOff() {
        assertEquals(
            ChargingSeparationOwnedApplyDecision.CANNOT_CONFIRM_DISABLED,
            ChargingSeparationPolicy.ownedApplyDecision(
                previous = false,
                current = true
            )
        )
        assertEquals(
            ChargingSeparationOwnedApplyDecision.CANNOT_CONFIRM_DISABLED,
            ChargingSeparationPolicy.ownedApplyDecision(
                previous = false,
                current = null
            )
        )
    }

    @Test
    fun unownedApply_takesOwnershipOnlyWhenCurrentStateIsOn() {
        assertTrue(
            ChargingSeparationPolicy.shouldTakeOwnershipAndDisable(
                current = true
            )
        )
        assertFalse(
            ChargingSeparationPolicy.shouldTakeOwnershipAndDisable(
                current = false
            )
        )
        assertFalse(
            ChargingSeparationPolicy.shouldTakeOwnershipAndDisable(
                current = null
            )
        )
    }

    @Test
    fun restoreDecision_preservesUnknownCurrentStateAsRestoreRequired() {
        assertEquals(
            ChargingSeparationRestoreDecision.NOTHING_TO_RESTORE,
            ChargingSeparationPolicy.restoreDecision(
                owned = false,
                previous = true,
                current = false
            )
        )
        assertEquals(
            ChargingSeparationRestoreDecision.CLEAR_OWNERSHIP,
            ChargingSeparationPolicy.restoreDecision(
                owned = true,
                previous = true,
                current = true
            )
        )
        assertEquals(
            ChargingSeparationRestoreDecision.RESTORE_PREVIOUS,
            ChargingSeparationPolicy.restoreDecision(
                owned = true,
                previous = true,
                current = null
            )
        )
    }

    @Test
    fun recoveryKeepsDisabledOnlyForManagedClosedUndockedLid() {
        assertTrue(
            ChargingSeparationPolicy.shouldRemainDisabledDuringRecovery(
                manageWithLid = true,
                lidSupported = true,
                lidClosed = true,
                externalDisplayConnected = false
            )
        )
        assertFalse(
            ChargingSeparationPolicy.shouldRemainDisabledDuringRecovery(
                manageWithLid = false,
                lidSupported = true,
                lidClosed = true,
                externalDisplayConnected = false
            )
        )
        assertFalse(
            ChargingSeparationPolicy.shouldRemainDisabledDuringRecovery(
                manageWithLid = true,
                lidSupported = false,
                lidClosed = true,
                externalDisplayConnected = false
            )
        )
        assertFalse(
            ChargingSeparationPolicy.shouldRemainDisabledDuringRecovery(
                manageWithLid = true,
                lidSupported = true,
                lidClosed = false,
                externalDisplayConnected = false
            )
        )
        assertFalse(
            ChargingSeparationPolicy.shouldRemainDisabledDuringRecovery(
                manageWithLid = true,
                lidSupported = true,
                lidClosed = true,
                externalDisplayConnected = true
            )
        )
    }

    @Test
    fun recoveryDecision_restoresWhenClosedLidStateShouldNotRemainDisabled() {
        assertEquals(
            ChargingSeparationRecoveryDecision.RESTORE_PREVIOUS,
            ChargingSeparationPolicy.recoveryDecision(
                owned = true,
                shouldRemainDisabled = false,
                current = null
            )
        )
    }

    @Test
    fun recoveryDecision_keepsConfirmedOffOrReappliesUnknownOnState() {
        assertEquals(
            ChargingSeparationRecoveryDecision.ALREADY_DISABLED,
            ChargingSeparationPolicy.recoveryDecision(
                owned = true,
                shouldRemainDisabled = true,
                current = false
            )
        )
        assertEquals(
            ChargingSeparationRecoveryDecision.REAPPLY_DISABLED,
            ChargingSeparationPolicy.recoveryDecision(
                owned = true,
                shouldRemainDisabled = true,
                current = true
            )
        )
        assertEquals(
            ChargingSeparationRecoveryDecision.REAPPLY_DISABLED,
            ChargingSeparationPolicy.recoveryDecision(
                owned = true,
                shouldRemainDisabled = true,
                current = null
            )
        )
    }
}
