package com.med.sleepmanager

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.ui.screens.AdvancedSettingsPage
import com.med.sleepmanager.ui.screens.SleepGraceSelector
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdvancedSettingsComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyAdvancedControl_invokesItsExpectedCallback() {
        var periodic by mutableStateOf(false)
        var syncThenStop by mutableStateOf(false)
        var batteryCondition by mutableStateOf(false)
        var batteryBelow by mutableStateOf(30)
        var notCharging by mutableStateOf(false)
        var batterySaverMode by mutableStateOf(AppPreferences.BATTERY_SAVER_IGNORE)
        var scheduleEnabled by mutableStateOf(false)
        var startPicked = false
        var endPicked = false

        composeRule.setContent {
            SleepManagerTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    AdvancedSettingsPage(
                        periodicSyncWhileSleeping = periodic,
                        syncThenStopOnSleepWake = syncThenStop,
                        syncConditionsAvailable = true,
                        onPeriodicSyncWhileSleepingChange = { periodic = it },
                        onSyncThenStopOnSleepWakeChange = { syncThenStop = it },
                        batteryConditionEnabled = batteryCondition,
                        batteryBelowPercent = batteryBelow,
                        notChargingOnly = notCharging,
                        batterySaverMode = batterySaverMode,
                        scheduleEnabled = scheduleEnabled,
                        scheduleStartMinutes = 23 * 60,
                        scheduleEndMinutes = 7 * 60,
                        onBatteryConditionEnabledChange = {
                            batteryCondition = it
                        },
                        onBatteryBelowPercentChange = { batteryBelow = it },
                        onNotChargingOnlyChange = { notCharging = it },
                        onBatterySaverModeChange = {
                            batterySaverMode = it
                        },
                        onScheduleEnabledChange = {
                            scheduleEnabled = it
                        },
                        onPickScheduleStart = { startPicked = true },
                        onPickScheduleEnd = { endPicked = true }
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            "Periodic sync while sleeping toggle"
        ).performScrollTo().performClick()
        assertTrue(periodic)

        composeRule.onNodeWithContentDescription(
            "Sync then stop on sleep & wake toggle"
        ).performScrollTo().performClick()
        assertTrue(syncThenStop)

        composeRule.onNodeWithContentDescription("Battery level toggle")
            .performScrollTo().performClick()
        assertTrue(batteryCondition)

        repeat(3) {
            composeRule.onNodeWithTag("battery_threshold_increase")
                .performScrollTo()
                .performClick()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            batteryBelow == 60
        }
        assertEquals(60, batteryBelow)

        composeRule.onNodeWithContentDescription("Not charging toggle")
            .performScrollTo().performClick()
        assertTrue(notCharging)

        // The Battery Saver condition only reads Android state and must
        // remain available even without privileged Home action control.
        composeRule.onNodeWithTag("battery_saver_mode_on")
            .performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battery_saver_mode_on")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            batterySaverMode == AppPreferences.BATTERY_SAVER_ON
        }
        assertEquals(AppPreferences.BATTERY_SAVER_ON, batterySaverMode)
        composeRule.onNodeWithTag("battery_saver_mode_off")
            .fetchSemanticsNode()

        composeRule.onNodeWithContentDescription("Schedule toggle")
            .performScrollTo().performClick()
        assertTrue(scheduleEnabled)

        composeRule.onNodeWithText("From 23:00")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("To 07:00")
            .performScrollTo().performClick()

        assertTrue(startPicked)
        assertTrue(endPicked)
    }

    @Test
    fun gracePeriodLargeTouchTargets_customPickerConfirmsOrCancels() {
        var graceMs by mutableStateOf(5_000L)
        var customEnabled by mutableStateOf(false)
        var customMs by mutableStateOf(60_000L)

        composeRule.setContent {
            SleepManagerTheme {
                SleepGraceSelector(
                    valueMs = graceMs,
                    customDelayEnabled = customEnabled,
                    customDelayMs = customMs,
                    onChange = {
                        customEnabled = false
                        graceMs = it
                    },
                    onCustomDelaySelected = {
                        customMs = it
                        customEnabled = true
                        graceMs = 0L
                    }
                )
            }
        }

        composeRule.onNodeWithTag("grace_option_5000").assertIsSelected()
        composeRule.onNodeWithTag("grace_option_custom").performClick()
        composeRule.onNodeWithTag("custom_delay_option_120000").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(60_000L, customMs)
        assertEquals(false, customEnabled)

        composeRule.onNodeWithTag("grace_option_custom").performClick()
        composeRule.onNodeWithTag("custom_delay_option_120000").performClick()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(120_000L, customMs)
        assertTrue(customEnabled)
        composeRule.onNodeWithTag("grace_option_custom").assertIsSelected()
        composeRule.onNodeWithText("Selected: 2 min").assertIsDisplayed()

        // Ensure the new one-hour choice persists and is displayed using
        // the same readable duration label as the Custom dialog.
        composeRule.onNodeWithTag("grace_option_custom").performClick()
        composeRule.onNodeWithTag("custom_delay_option_3600000").performClick()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(3_600_000L, customMs)
        assertTrue(customEnabled)
        composeRule.onNodeWithText("Selected: 1 h").assertIsDisplayed()
        composeRule.onNodeWithText("Advanced • 3600s").assertDoesNotExist()

        composeRule.onNodeWithTag("grace_option_10000").performClick()
        assertEquals(10_000L, graceMs)
        assertEquals(false, customEnabled)
        composeRule.onNodeWithTag("grace_option_10000").assertIsSelected()
        composeRule.onNodeWithTag("grace_custom_selected_duration").assertDoesNotExist()
    }
}
