package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperNetworkRestoreHandoffStateTest {
    @Test
    fun prepareArmsOnlyWhenHelperAndNetworkRestoreAreBothNeeded() {
        val state = HelperNetworkRestoreHandoffState()

        state.prepare(
            helperRestoreNeeded = true,
            networkRestoreNeeded = true
        )
        assertTrue(state.isPending)

        state.prepare(
            helperRestoreNeeded = true,
            networkRestoreNeeded = false
        )
        assertFalse(state.isPending)

        state.prepare(
            helperRestoreNeeded = false,
            networkRestoreNeeded = true
        )
        assertFalse(state.isPending)
    }

    @Test
    fun consumeReturnsPendingValueAndClearsHandoff() {
        val state = HelperNetworkRestoreHandoffState()
        state.prepare(
            helperRestoreNeeded = true,
            networkRestoreNeeded = true
        )

        assertTrue(state.consume())
        assertFalse(state.isPending)
        assertFalse(state.consume())
    }

    @Test
    fun clearCancelsPendingHandoff() {
        val state = HelperNetworkRestoreHandoffState()
        state.prepare(
            helperRestoreNeeded = true,
            networkRestoreNeeded = true
        )

        state.clear()

        assertFalse(state.isPending)
        assertFalse(state.consume())
    }
}
