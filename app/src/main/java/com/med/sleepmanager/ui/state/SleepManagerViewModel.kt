package com.med.sleepmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

internal class SleepManagerViewModel : ViewModel() {
    var uiState by mutableStateOf(SleepManagerUiState())
        private set

    fun update(
        transform: (SleepManagerUiState) -> SleepManagerUiState
    ) {
        uiState = transform(uiState)
    }
}
