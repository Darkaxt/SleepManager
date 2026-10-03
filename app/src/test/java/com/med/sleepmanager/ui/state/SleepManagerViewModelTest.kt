package com.med.sleepmanager.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepManagerViewModelTest {
    @Test
    fun updateReplacesUiStateFromCurrentSnapshot() {
        val viewModel = SleepManagerViewModel()

        viewModel.update {
            it.copy(
                activityRefreshToken = it.activityRefreshToken + 1,
                currentWifiState = true
            )
        }
        viewModel.update {
            it.copy(currentBluetoothState = false)
        }

        assertEquals(1, viewModel.uiState.activityRefreshToken)
        assertEquals(true, viewModel.uiState.currentWifiState)
        assertEquals(false, viewModel.uiState.currentBluetoothState)
        assertNull(viewModel.uiState.currentSyncthingState)
    }
}
