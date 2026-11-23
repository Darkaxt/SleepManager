package com.med.sleepmanager.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BroadcastSenderPolicyTest {
    private val pkg = "com.chiller3.basicsync"
    private val uid = 10123
    private val none = BroadcastSenderPolicy.INVALID_UID

    @Test
    fun unattributedBroadcastFromInstalledAppIsAcceptedAsHint() {
        assertEquals(
            BroadcastSenderVerdict.UNATTRIBUTED,
            BroadcastSenderPolicy.evaluate(pkg, uid, none, null)
        )
    }

    @Test
    fun attributedExpectedSenderIsVerified() {
        assertEquals(
            BroadcastSenderVerdict.VERIFIED,
            BroadcastSenderPolicy.evaluate(pkg, uid, uid, pkg)
        )
        assertEquals(
            BroadcastSenderVerdict.VERIFIED,
            BroadcastSenderPolicy.evaluate(pkg, uid, uid, null)
        )
    }

    @Test
    fun attributedOtherSenderIsRejected() {
        assertEquals(
            BroadcastSenderVerdict.REJECTED,
            BroadcastSenderPolicy.evaluate(pkg, uid, 10999, null)
        )
        assertEquals(
            BroadcastSenderVerdict.REJECTED,
            BroadcastSenderPolicy.evaluate(pkg, uid, none, "com.example.spoof")
        )
        // A matching package name with a different uid is still another app.
        assertEquals(
            BroadcastSenderVerdict.REJECTED,
            BroadcastSenderPolicy.evaluate(pkg, uid, 10999, pkg)
        )
    }

    @Test
    fun nothingIsAcceptedWhileExpectedAppIsNotInstalled() {
        assertEquals(
            BroadcastSenderVerdict.REJECTED,
            BroadcastSenderPolicy.evaluate(pkg, null, none, null)
        )
    }

    @Test
    fun keyMapperPausedExtraMustBeBoolean() {
        assertEquals(true, KeyMapperStatePolicy.pausedExtra(true))
        assertEquals(false, KeyMapperStatePolicy.pausedExtra(false))
        assertNull(KeyMapperStatePolicy.pausedExtra(null))
        assertNull(KeyMapperStatePolicy.pausedExtra("false"))
        assertNull(KeyMapperStatePolicy.pausedExtra(0))
    }
}
