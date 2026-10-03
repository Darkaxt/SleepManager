package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClamshellLidStateTest {
    @Test
    fun readableCurrentState_alwaysWinsRecovery() {
        val state = ClamshellLidState()

        state.initialize(
            currentLidState = true,
            interactive = true,
            lastKnownLidClosed = false
        )

        assertTrue(state.isClosed)

        state.initialize(
            currentLidState = false,
            interactive = false,
            lastKnownLidClosed = true
        )

        assertFalse(state.isClosed)
    }

    @Test
    fun missingCurrentState_usesLastKnownOnlyWhileNonInteractive() {
        val state = ClamshellLidState()

        state.initialize(
            currentLidState = null,
            interactive = false,
            lastKnownLidClosed = true
        )

        assertTrue(state.isClosed)

        state.initialize(
            currentLidState = null,
            interactive = true,
            lastKnownLidClosed = true
        )

        assertFalse(state.isClosed)
    }

    @Test
    fun missingCurrentAndUnknownInteractiveState_defaultsOpen() {
        val state = ClamshellLidState()

        state.initialize(
            currentLidState = null,
            interactive = null,
            lastKnownLidClosed = true
        )

        assertFalse(state.isClosed)
    }

    @Test
    fun callbacksAndClear_updateSnapshot() {
        val state = ClamshellLidState()

        state.markClosed()
        assertTrue(state.isClosed)

        state.markOpened()
        assertFalse(state.isClosed)

        state.markClosed()
        state.clear()
        assertFalse(state.isClosed)
    }
}
