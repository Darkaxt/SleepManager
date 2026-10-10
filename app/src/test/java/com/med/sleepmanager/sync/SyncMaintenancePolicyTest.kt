package com.med.sleepmanager.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMaintenancePolicyTest {
    @Test
    fun completionRequiresAtLeastOneSelectedProvider() {
        assertFalse(
            SyncMaintenancePolicy.completionReady(
                selectedProviderCount = 0,
                allSelectedProvidersCompletionAware = true
            )
        )
    }

    @Test
    fun oneNonCompletionAwareProviderBlocksMaintenance() {
        assertFalse(
            SyncMaintenancePolicy.completionReady(
                selectedProviderCount = 2,
                allSelectedProvidersCompletionAware = false
            )
        )
    }

    @Test
    fun completionReadyWhenEverySelectedProviderSupportsIt() {
        assertTrue(
            SyncMaintenancePolicy.completionReady(
                selectedProviderCount = 2,
                allSelectedProvidersCompletionAware = true
            )
        )
    }

    @Test
    fun periodicRequiresManagerFeatureAndCompletionCapability() {
        assertTrue(
            SyncMaintenancePolicy.periodicCanRun(
                managerEnabled = true,
                periodicEnabled = true,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.periodicCanRun(
                managerEnabled = true,
                periodicEnabled = false,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.periodicCanRun(
                managerEnabled = true,
                periodicEnabled = true,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = false
            )
        )
    }

    @Test
    fun screenOffKeepsPreSleepAndPeriodicMaintenanceButCancelsWakeMaintenance() {
        assertFalse(
            SyncMaintenancePolicy.shouldCancelMaintenanceOnScreenOff(
                SyncMaintenanceTrigger.BEFORE_SLEEP
            )
        )
        assertFalse(
            SyncMaintenancePolicy.shouldCancelMaintenanceOnScreenOff(
                SyncMaintenanceTrigger.PERIODIC_SLEEP
            )
        )
        assertTrue(
            SyncMaintenancePolicy.shouldCancelMaintenanceOnScreenOff(
                SyncMaintenanceTrigger.AFTER_WAKE
            )
        )
    }

    @Test
    fun periodicAlarmRetriesOnlyForSuppressedClosedLidFalseWake() {
        assertEquals(
            PeriodicAlarmDeviceDecision.RUN,
            SyncMaintenancePolicy.periodicAlarmDeviceDecision(
                interactive = false,
                closedLidWakeSuppressed = false
            )
        )
        assertEquals(
            PeriodicAlarmDeviceDecision.RETRY_AFTER_CLOSED_LID_FALSE_WAKE,
            SyncMaintenancePolicy.periodicAlarmDeviceDecision(
                interactive = true,
                closedLidWakeSuppressed = true
            )
        )
        assertEquals(
            PeriodicAlarmDeviceDecision.CANCEL_AWAKE,
            SyncMaintenancePolicy.periodicAlarmDeviceDecision(
                interactive = true,
                closedLidWakeSuppressed = false
            )
        )
    }

    @Test
    fun periodicCompletionSchedulesNextOnlyWhileStillSleepingAndEnabled() {
        assertEquals(
            PeriodicMaintenanceFollowUp.SCHEDULE_NEXT,
            SyncMaintenancePolicy.periodicCompletionFollowUp(
                stillSleeping = true,
                periodicEnabled = true
            )
        )
        assertEquals(
            PeriodicMaintenanceFollowUp.CANCEL,
            SyncMaintenancePolicy.periodicCompletionFollowUp(
                stillSleeping = false,
                periodicEnabled = true
            )
        )
        assertEquals(
            PeriodicMaintenanceFollowUp.CANCEL,
            SyncMaintenancePolicy.periodicCompletionFollowUp(
                stillSleeping = true,
                periodicEnabled = false
            )
        )
    }

    @Test
    fun wakeBeforePreSleepCompletion_doesNotContinueSleepTransition() {
        assertTrue(
            SyncMaintenancePolicy.shouldContinueSleepAfterPreSleepMaintenance(
                stillSleeping = true,
                managerEnabled = true,
                actionsApplied = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.shouldContinueSleepAfterPreSleepMaintenance(
                stillSleeping = false,
                managerEnabled = true,
                actionsApplied = true
            )
        )
    }

    @Test
    fun transitionRequiresManagerFeatureAndCompletionCapability() {
        assertTrue(
            SyncMaintenancePolicy.transitionCanRun(
                managerEnabled = true,
                transitionEnabled = true,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.transitionCanRun(
                managerEnabled = false,
                transitionEnabled = true,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.transitionCanRun(
                managerEnabled = true,
                transitionEnabled = true,
                selectedProviderCount = 0,
                allSelectedProvidersCompletionAware = true
            )
        )
    }
}
