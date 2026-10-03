package com.med.sleepmanager.integration.connector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncthingConnectorTest {
    private val packageName = "com.github.catfriend1.syncthingfork"

    @Test
    fun confirmedRunningStopKeepsConfirmedRestoreToken() {
        val result =
            SyncthingConnector.resultAfterStop(
                packageName = packageName,
                preSleepState = SyncthingConnector.PreSleepState.CONFIRMED_RUNNING,
                stopSent = true
            )

        assertTrue(result.attempted)
        assertTrue(result.changed)
        assertEquals("confirmed:$packageName", result.restoreToken)
        assertEquals("STOP sent; running state was confirmed", result.detail)
        assertEquals(
            packageName,
            SyncthingConnector.restoreTargetPackage(result.restoreToken)
        )
    }

    @Test
    fun unknownPreSleepStateKeepsLegacyRestoreOwnershipWithoutClaimingConfirmation() {
        val result =
            SyncthingConnector.resultAfterStop(
                packageName = packageName,
                preSleepState = SyncthingConnector.PreSleepState.UNKNOWN,
                stopSent = true
            )

        assertTrue(result.attempted)
        assertTrue(result.changed)
        assertEquals("unverified:$packageName", result.restoreToken)
        assertEquals(
            "STOP sent; previous state could not be confirmed",
            result.detail
        )
        assertEquals(
            packageName,
            SyncthingConnector.restoreTargetPackage(result.restoreToken)
        )
    }

    @Test
    fun failedStopDoesNotCreateRestoreOwnership() {
        val result =
            SyncthingConnector.resultAfterStop(
                packageName = packageName,
                preSleepState = SyncthingConnector.PreSleepState.CONFIRMED_RUNNING,
                stopSent = false
            )

        assertTrue(result.attempted)
        assertFalse(result.changed)
        assertNull(result.restoreToken)
        assertEquals("STOP not sent", result.detail)
    }

    @Test
    fun restoreTargetStillAcceptsLegacyRawPackageToken() {
        assertEquals(
            packageName,
            SyncthingConnector.restoreTargetPackage(packageName)
        )
    }
}
