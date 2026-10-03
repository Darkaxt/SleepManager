package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClamshellDisplayStateTest {
    @Test
    fun registerListener_recordsInitialDisplaySnapshot() {
        val state = ClamshellDisplayState()

        state.registerListener(
            connected = true,
            active = true
        )

        assertTrue(state.listenerRegistered)
        assertTrue(state.externalDisplayConnected)
        assertTrue(state.externalDisplayActive)
    }

    @Test
    fun updateExternalDisplay_returnsPreviousConnectionState() {
        val state = ClamshellDisplayState()
        state.updateExternalDisplay(
            connected = true,
            active = true
        )

        val wasConnected = state.updateExternalDisplay(
            connected = false,
            active = false
        )

        assertTrue(wasConnected)
        assertFalse(state.externalDisplayConnected)
        assertFalse(state.externalDisplayActive)
    }

    @Test
    fun unregisterListener_clearsRegistrationAndDisplaySnapshot() {
        val state = ClamshellDisplayState()
        state.registerListener(
            connected = true,
            active = true
        )

        state.unregisterListener()

        assertFalse(state.listenerRegistered)
        assertFalse(state.externalDisplayConnected)
        assertFalse(state.externalDisplayActive)
    }
}
