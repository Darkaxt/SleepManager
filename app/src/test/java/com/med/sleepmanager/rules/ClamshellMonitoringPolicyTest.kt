package com.med.sleepmanager.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClamshellMonitoringPolicyTest {
    @Test
    fun unsupportedLid_neverStartsMonitoring() {
        assertFalse(
            ClamshellMonitoringPolicy.shouldMonitorLid(
                lidSupported = false,
                closedLidProtectionEnabled = true,
                chargingSeparationWithLidEnabled = true,
                chargingSeparationControlSupported = true
            )
        )
    }

    @Test
    fun closedLidProtection_enablesMonitoringWhenLidIsSupported() {
        assertTrue(
            ClamshellMonitoringPolicy.shouldMonitorLid(
                lidSupported = true,
                closedLidProtectionEnabled = true,
                chargingSeparationWithLidEnabled = false,
                chargingSeparationControlSupported = false
            )
        )
    }

    @Test
    fun chargingSeparation_enablesMonitoringOnlyWhenControlIsSupported() {
        assertTrue(
            ClamshellMonitoringPolicy.shouldMonitorLid(
                lidSupported = true,
                closedLidProtectionEnabled = false,
                chargingSeparationWithLidEnabled = true,
                chargingSeparationControlSupported = true
            )
        )

        assertFalse(
            ClamshellMonitoringPolicy.shouldMonitorLid(
                lidSupported = true,
                closedLidProtectionEnabled = false,
                chargingSeparationWithLidEnabled = true,
                chargingSeparationControlSupported = false
            )
        )
    }

    @Test
    fun noClamshellFeatureEnabled_doesNotStartMonitoring() {
        assertFalse(
            ClamshellMonitoringPolicy.shouldMonitorLid(
                lidSupported = true,
                closedLidProtectionEnabled = false,
                chargingSeparationWithLidEnabled = false,
                chargingSeparationControlSupported = true
            )
        )
    }
}
