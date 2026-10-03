package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisableRestoreRuntimeStateTest {
    @Test
    fun beginStartsDisableRestoreAndClearsPreviousForceStopRequest() {
        val state = DisableRestoreRuntimeState()
        state.requestForceStop()

        state.begin()

        assertTrue(state.isRequested)
        assertTrue(state.isInitializing)
        assertFalse(state.forceStopRequested)
    }

    @Test
    fun initializationCompletionPreservesRequestAndForceStopState() {
        val state = DisableRestoreRuntimeState()
        state.begin()
        state.requestForceStop()

        state.markInitializationComplete()

        assertTrue(state.isRequested)
        assertFalse(state.isInitializing)
        assertTrue(state.forceStopRequested)
    }

    @Test
    fun completeClearsRequestAndForceStopAfterInitialization() {
        val state = DisableRestoreRuntimeState()
        state.begin()
        state.requestForceStop()
        state.markInitializationComplete()

        state.complete()

        assertFalse(state.isRequested)
        assertFalse(state.isInitializing)
        assertFalse(state.forceStopRequested)
    }

    @Test
    fun destroyStyleRequestClearPreservesTransientFlagsUntilLaterReset() {
        val state = DisableRestoreRuntimeState()
        state.begin()
        state.requestForceStop()

        state.clearRequest()

        assertFalse(state.isRequested)
        assertTrue(state.isInitializing)
        assertTrue(state.forceStopRequested)

        state.clearTransientFlags()

        assertFalse(state.isRequested)
        assertFalse(state.isInitializing)
        assertFalse(state.forceStopRequested)
    }
}
