package com.med.sleepmanager.ui

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import com.med.sleepmanager.R

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun View.performSleepManagerFeedback() {
    if (isHapticFeedbackEnabled) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    if (isSoundEffectsEnabled) {
        playSoundEffect(SoundEffectConstants.CLICK)
    }
}

@Composable
internal fun feedbackClick(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performSleepManagerFeedback()
        action()
    }
}

@Composable
internal fun <T> feedbackChange(action: (T) -> Unit): (T) -> Unit {
    val view = LocalView.current
    return { value ->
        view.performSleepManagerFeedback()
        action(value)
    }
}

internal enum class AppSection {
    HOME,
    ADVANCED,
    STATS,
    ACTIVITY_LOG,
    ABOUT
}

internal val AppSection.label: String
    get() = when (this) {
        AppSection.HOME -> "Home"
        AppSection.ADVANCED -> "Advanced settings"
        AppSection.STATS -> "Stats"
        AppSection.ACTIVITY_LOG -> "Activity log"
        AppSection.ABOUT -> "About"
    }

internal val AppSection.iconRes: Int
    get() = when (this) {
        AppSection.HOME -> R.drawable.ic_home
        AppSection.ADVANCED -> R.drawable.ic_advanced
        AppSection.STATS -> R.drawable.ic_battery
        AppSection.ACTIVITY_LOG -> R.drawable.ic_activity_log
        AppSection.ABOUT -> R.drawable.ic_info
    }

