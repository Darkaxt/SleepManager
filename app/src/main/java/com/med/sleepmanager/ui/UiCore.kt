package com.med.sleepmanager.ui

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.annotation.StringRes
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

internal object SleepManagerFeedbackGate {
    private var suppressWrappedFeedback = false

    fun <T> withoutWrappedFeedback(block: () -> T): T {
        val previous = suppressWrappedFeedback
        suppressWrappedFeedback = true
        return try {
            block()
        } finally {
            suppressWrappedFeedback = previous
        }
    }

    fun performWrappedFeedback(view: View) {
        if (!suppressWrappedFeedback) {
            view.performSleepManagerFeedback()
        }
    }
}

@Composable
internal fun feedbackClick(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        SleepManagerFeedbackGate.performWrappedFeedback(view)
        action()
    }
}

@Composable
internal fun <T> feedbackChange(action: (T) -> Unit): (T) -> Unit {
    val view = LocalView.current
    return { value ->
        SleepManagerFeedbackGate.performWrappedFeedback(view)
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

@get:StringRes
internal val AppSection.labelRes: Int
    get() = when (this) {
        AppSection.HOME -> R.string.nav_home
        AppSection.ADVANCED -> R.string.nav_advanced
        AppSection.STATS -> R.string.nav_stats
        AppSection.ACTIVITY_LOG -> R.string.nav_activity_log
        AppSection.ABOUT -> R.string.nav_about
    }

@get:StringRes
internal val AppSection.titleRes: Int
    get() = when (this) {
        AppSection.HOME -> R.string.app_name
        AppSection.ADVANCED -> R.string.section_advanced_title
        AppSection.STATS -> R.string.nav_stats
        AppSection.ACTIVITY_LOG -> R.string.nav_activity_log
        AppSection.ABOUT -> R.string.nav_about
    }

@get:StringRes
internal val AppSection.subtitleRes: Int
    get() = when (this) {
        AppSection.HOME -> R.string.section_home_subtitle
        AppSection.ADVANCED -> R.string.section_advanced_subtitle
        AppSection.STATS -> R.string.section_stats_subtitle
        AppSection.ACTIVITY_LOG -> R.string.section_activity_log_subtitle
        AppSection.ABOUT -> R.string.section_about_subtitle
    }

internal val AppSection.iconRes: Int
    get() = when (this) {
        AppSection.HOME -> R.drawable.ic_home
        AppSection.ADVANCED -> R.drawable.ic_advanced
        AppSection.STATS -> R.drawable.ic_battery
        AppSection.ACTIVITY_LOG -> R.drawable.ic_activity_log
        AppSection.ABOUT -> R.drawable.ic_info
    }

