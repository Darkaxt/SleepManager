package com.med.sleepmanager.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySaverPolicyTest {
    @Test
    fun sleepEnable_requiresManagedSupportedUnownedAndCurrentlyOff() {
        assertTrue(
            BatterySaverPolicy.shouldEnableForSleep(
                manageEnabled = true,
                controlSupported = true,
                owned = false,
                currentlyEnabled = false,
                externalPowerConnected = false
            )
        )
        assertFalse(
            BatterySaverPolicy.shouldEnableForSleep(
                manageEnabled = false,
                controlSupported = true,
                owned = false,
                currentlyEnabled = false
            )
        )
        assertFalse(
            BatterySaverPolicy.shouldEnableForSleep(
                manageEnabled = true,
                controlSupported = false,
                owned = false,
                currentlyEnabled = false
            )
        )
        assertFalse(
            BatterySaverPolicy.shouldEnableForSleep(
                manageEnabled = true,
                controlSupported = true,
                owned = true,
                currentlyEnabled = false
            )
        )
        assertFalse(
            BatterySaverPolicy.shouldEnableForSleep(
                manageEnabled = true,
                controlSupported = true,
                owned = false,
                currentlyEnabled = true
            )
        )
    }


    @Test
    fun sleepEnable_isSkippedWhileExternalPowerIsConnected() {
        assertFalse(
            BatterySaverPolicy.shouldEnableForSleep(
                manageEnabled = true,
                controlSupported = true,
                owned = false,
                currentlyEnabled = false,
                externalPowerConnected = true
            )
        )
    }

    @Test
    fun powerDisconnectDuringSleep_enablesManagedBatterySaver() {
        assertEquals(
            BatterySaverPowerEventDecision.ENABLE_SLEEP_STATE,
            BatterySaverPolicy.powerEventDecision(
                manageEnabled = true,
                controlSupported = true,
                effectivelySleeping = true,
                externalPowerConnected = false,
                deferredForExternalPower = true,
                owned = false,
                previous = false,
                currentlyEnabled = false
            )
        )
    }


    @Test
    fun transientDisconnectThatStillReportsExternalPower_doesNothing() {
        assertEquals(
            BatterySaverPowerEventDecision.NOTHING,
            BatterySaverPolicy.powerEventDecision(
                manageEnabled = true,
                controlSupported = true,
                effectivelySleeping = true,
                externalPowerConnected = true,
                deferredForExternalPower = true,
                owned = false,
                previous = false,
                currentlyEnabled = false
            )
        )
    }

    @Test
    fun unplugWithoutDeferredSleepAction_doesNothing() {
        assertEquals(
            BatterySaverPowerEventDecision.NOTHING,
            BatterySaverPolicy.powerEventDecision(
                manageEnabled = true,
                controlSupported = true,
                effectivelySleeping = true,
                externalPowerConnected = false,
                deferredForExternalPower = false,
                owned = false,
                previous = false,
                currentlyEnabled = false
            )
        )
    }

    @Test
    fun powerReconnectDuringSleep_restoresOwnedBatterySaver() {
        assertEquals(
            BatterySaverPowerEventDecision.RESTORE_PREVIOUS,
            BatterySaverPolicy.powerEventDecision(
                manageEnabled = true,
                controlSupported = true,
                effectivelySleeping = true,
                externalPowerConnected = true,
                deferredForExternalPower = false,
                owned = true,
                previous = false,
                currentlyEnabled = true
            )
        )
        assertEquals(
            BatterySaverPowerEventDecision.RESTORE_PREVIOUS,
            BatterySaverPolicy.powerEventDecision(
                manageEnabled = true,
                controlSupported = true,
                effectivelySleeping = true,
                externalPowerConnected = true,
                deferredForExternalPower = false,
                owned = true,
                previous = false,
                currentlyEnabled = false
            )
        )
    }

    @Test
    fun powerChangesWhileAwake_doNotChangeBatterySaver() {
        assertEquals(
            BatterySaverPowerEventDecision.NOTHING,
            BatterySaverPolicy.powerEventDecision(
                manageEnabled = true,
                controlSupported = true,
                effectivelySleeping = false,
                externalPowerConnected = false,
                deferredForExternalPower = true,
                owned = false,
                previous = false,
                currentlyEnabled = false
            )
        )
    }

    @Test
    fun restoreDecision_preservesOwnershipSemantics() {
        assertEquals(
            BatterySaverRestoreDecision.NOTHING_TO_RESTORE,
            BatterySaverPolicy.restoreDecision(
                owned = false,
                previous = false,
                current = true
            )
        )
        assertEquals(
            BatterySaverRestoreDecision.CLEAR_OWNERSHIP,
            BatterySaverPolicy.restoreDecision(
                owned = true,
                previous = false,
                current = false
            )
        )
        assertEquals(
            BatterySaverRestoreDecision.RESTORE_PREVIOUS,
            BatterySaverPolicy.restoreDecision(
                owned = true,
                previous = false,
                current = true
            )
        )
    }

    @Test
    fun maintenanceTemporaryRestore_requiresOwnedSleepEnabledState() {
        assertTrue(
            BatterySaverPolicy.shouldTemporarilyRestoreForMaintenance(
                owned = true,
                previous = false,
                current = true
            )
        )
        assertFalse(
            BatterySaverPolicy.shouldTemporarilyRestoreForMaintenance(
                owned = true,
                previous = true,
                current = true
            )
        )
        assertFalse(
            BatterySaverPolicy.shouldTemporarilyRestoreForMaintenance(
                owned = true,
                previous = false,
                current = false
            )
        )
    }

    @Test
    fun maintenanceReapply_requiresSleepingOwnedStateStillOff() {
        assertTrue(
            BatterySaverPolicy.shouldReapplyAfterMaintenance(
                owned = true,
                previous = false,
                isRealWake = false,
                current = false
            )
        )
        assertFalse(
            BatterySaverPolicy.shouldReapplyAfterMaintenance(
                owned = true,
                previous = false,
                isRealWake = true,
                current = false
            )
        )
        assertFalse(
            BatterySaverPolicy.shouldReapplyAfterMaintenance(
                owned = true,
                previous = false,
                isRealWake = false,
                current = true
            )
        )
    }

    @Test
    fun recoveryDecision_restoresOnRealWakeBeforeSleepStateChecks() {
        assertEquals(
            BatterySaverRecoveryDecision.RESTORE_PREVIOUS,
            BatterySaverPolicy.recoveryDecision(
                owned = true,
                realWake = true,
                effectivelySleeping = null,
                previous = false,
                manageEnabled = null,
                currentlyEnabled = null
            )
        )
    }

    @Test
    fun recoveryDecision_reappliesOnlyForManagedOwnedSleepingStateThatIsOff() {
        assertEquals(
            BatterySaverRecoveryDecision.REAPPLY_SLEEP_STATE,
            BatterySaverPolicy.recoveryDecision(
                owned = true,
                realWake = false,
                effectivelySleeping = true,
                previous = false,
                manageEnabled = true,
                currentlyEnabled = false
            )
        )

        assertEquals(
            BatterySaverRecoveryDecision.NOTHING,
            BatterySaverPolicy.recoveryDecision(
                owned = true,
                realWake = false,
                effectivelySleeping = true,
                previous = false,
                manageEnabled = true,
                currentlyEnabled = true
            )
        )

        assertEquals(
            BatterySaverRecoveryDecision.NOTHING,
            BatterySaverPolicy.recoveryDecision(
                owned = true,
                realWake = false,
                effectivelySleeping = false,
                previous = false,
                manageEnabled = null,
                currentlyEnabled = null
            )
        )
    }
}
