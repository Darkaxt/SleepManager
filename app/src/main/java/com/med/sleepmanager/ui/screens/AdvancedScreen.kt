package com.med.sleepmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.R
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.ui.components.SectionTitle
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.controller.controllerFocusHighlight
import com.med.sleepmanager.ui.feedbackClick

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.med.sleepmanager.ui.feedbackChange

@Composable
internal fun SleepGraceSelector(
    valueMs: Long,
    customDelayEnabled: Boolean,
    customDelayMs: Long,
    onChange: (Long) -> Unit,
    onCustom: () -> Unit
) {
    val options = listOf(
        stringResource(R.string.grace_immediate) to 0L,
        stringResource(R.string.duration_5_seconds) to 5000L,
        stringResource(R.string.duration_10_seconds) to 10000L
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.grace_period),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            stringResource(
                if (customDelayEnabled) {
                    R.string.grace_period_custom_delay_active
                } else {
                    R.string.grace_period_description
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options.size) { index ->
                val (label, value) = options[index]
                FilterChip(
                    selected = !customDelayEnabled && valueMs == value,
                    onClick = feedbackClick { onChange(value) },
                    enabled = !customDelayEnabled,
                    label = { Text(label) }
                )
            }

            item {
                FilterChip(
                    selected = customDelayEnabled,
                    onClick = feedbackClick(onCustom),
                    label = { Text(stringResource(R.string.custom)) }
                )
            }
        }

        if (customDelayEnabled) {
            TextButton(onClick = feedbackClick(onCustom)) {
                Text(
                    stringResource(
                        R.string.advanced_with_duration,
                        formatDuration(customDelayMs)
                    )
                )
            }
        }
    }
}

internal enum class AdvancedScrollTarget {
    CUSTOM_DELAY
}

@Composable
internal fun AdvancedSettingsPage(
    periodicSyncWhileSleeping: Boolean,
    syncThenStopOnSleepWake: Boolean,
    syncConditionsAvailable: Boolean,
    onPeriodicSyncWhileSleepingChange: (Boolean) -> Unit,
    onSyncThenStopOnSleepWakeChange: (Boolean) -> Unit,
    customDelayEnabled: Boolean,
    customDelayMs: Long,
    batteryConditionEnabled: Boolean,
    batteryBelowPercent: Int,
    notChargingOnly: Boolean,
    batterySaverMode: String,
    scheduleEnabled: Boolean,
    scheduleStartMinutes: Int,
    scheduleEndMinutes: Int,
    onCustomDelayEnabledChange: (Boolean) -> Unit,
    onCustomDelayChange: (Long) -> Unit,
    onBatteryConditionEnabledChange: (Boolean) -> Unit,
    onBatteryBelowPercentChange: (Int) -> Unit,
    onNotChargingOnlyChange: (Boolean) -> Unit,
    onBatterySaverModeChange: (String) -> Unit,
    onScheduleEnabledChange: (Boolean) -> Unit,
    onPickScheduleStart: () -> Unit,
    onPickScheduleEnd: () -> Unit,
    scrollTarget: AdvancedScrollTarget? = null,
    scrollRequestId: Int = 0,
    onScrollTargetConsumed: () -> Unit = {}
) {
    val customDelayRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(scrollRequestId, scrollTarget) {
        if (scrollTarget == AdvancedScrollTarget.CUSTOM_DELAY) {
            customDelayRequester.bringIntoView()
            onScrollTargetConsumed()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(
            title = stringResource(R.string.advanced_sync_behavior),
            subtitle = stringResource(R.string.advanced_sync_behavior_description)
        )

        SettingsCard {
            AdvancedToggleRow(
                title = stringResource(R.string.periodic_sync_while_sleeping),
                subtitle = stringResource(
                    if (syncConditionsAvailable) {
                        R.string.periodic_sync_while_sleeping_description
                    } else {
                        R.string.advanced_sync_unavailable
                    }
                ),
                checked = periodicSyncWhileSleeping,
                enabled = syncConditionsAvailable,
                onCheckedChange = onPeriodicSyncWhileSleepingChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = stringResource(R.string.sync_then_stop_sleep_wake),
                subtitle = stringResource(
                    if (syncConditionsAvailable) {
                        R.string.sync_then_stop_sleep_wake_description
                    } else {
                        R.string.advanced_sync_unavailable
                    }
                ),
                checked = syncThenStopOnSleepWake,
                enabled = syncConditionsAvailable,
                onCheckedChange = onSyncThenStopOnSleepWakeChange
            )
        }

        SectionTitle(
            title = stringResource(R.string.advanced_sleep_conditions),
            subtitle = stringResource(R.string.advanced_sleep_conditions_description)
        )

        SettingsCard(
            modifier = Modifier.bringIntoViewRequester(customDelayRequester)
        ) {
            AdvancedToggleRow(
                title = stringResource(R.string.use_custom_delay),
                subtitle = if (customDelayEnabled) {
                    stringResource(
                        R.string.custom_delay_active_description,
                        formatDuration(customDelayMs)
                    )
                } else {
                    stringResource(R.string.custom_delay_description)
                },
                checked = customDelayEnabled,
                onCheckedChange = onCustomDelayEnabledChange
            )

            if (customDelayEnabled) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.delay_before_sleep_actions),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    val options = listOf(
                        stringResource(R.string.duration_1_minute) to 60_000L,
                        stringResource(R.string.duration_5_minutes) to 300_000L,
                        stringResource(R.string.duration_10_minutes) to 600_000L,
                        stringResource(R.string.duration_30_minutes) to 1_800_000L
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(options.size) { index ->
                            val (label, value) = options[index]
                            FilterChip(
                                modifier = Modifier.testTag(
                                    "custom_delay_option_$value"
                                ),
                                selected = customDelayMs == value,
                                onClick = feedbackClick { onCustomDelayChange(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        SectionTitle(
            title = stringResource(R.string.conditions),
            subtitle = stringResource(R.string.conditions_description)
        )
        SettingsCard {
            AdvancedToggleRow(
                title = stringResource(R.string.battery_level),
                subtitle = if (batteryConditionEnabled) {
                    stringResource(
                        R.string.battery_below_percent,
                        batteryBelowPercent
                    )
                } else {
                    stringResource(R.string.ignore_battery_percentage)
                },
                checked = batteryConditionEnabled,
                onCheckedChange = onBatteryConditionEnabledChange
            )

            if (batteryConditionEnabled) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val levels = listOf(20, 30, 40, 50, 60)
                        items(levels.size) { index ->
                            val level = levels[index]
                            FilterChip(
                                selected = batteryBelowPercent == level,
                                onClick = feedbackClick { onBatteryBelowPercentChange(level) },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.battery_level_chip,
                                            level
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = stringResource(R.string.not_charging),
                subtitle = stringResource(
                    if (notChargingOnly) {
                        R.string.only_when_unplugged
                    } else {
                        R.string.ignore_charging_state
                    }
                ),
                checked = notChargingOnly,
                onCheckedChange = onNotChargingOnlyChange
            )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.battery_saver),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(
                            when (batterySaverMode) {
                                AppPreferences.BATTERY_SAVER_ON ->
                                    R.string.battery_saver_only_when_on
                                AppPreferences.BATTERY_SAVER_OFF ->
                                    R.string.battery_saver_only_when_off
                                else ->
                                    R.string.battery_saver_ignore_state
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val modes = listOf(
                        stringResource(R.string.ignore) to
                            AppPreferences.BATTERY_SAVER_IGNORE,
                        stringResource(R.string.on) to
                            AppPreferences.BATTERY_SAVER_ON,
                        stringResource(R.string.off) to
                            AppPreferences.BATTERY_SAVER_OFF
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(modes.size) { index ->
                            val (label, mode) = modes[index]
                            val optionContentDescription =
                                stringResource(
                                    R.string.battery_saver_option,
                                    label
                                )
                            FilterChip(
                                modifier = Modifier
                                    .testTag("battery_saver_mode_$mode")
                                    .semantics {
                                        contentDescription =
                                            optionContentDescription
                                    },
                                selected = batterySaverMode == mode,
                                onClick = feedbackClick {
                                    onBatterySaverModeChange(mode)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = stringResource(R.string.schedule),
                subtitle = if (scheduleEnabled) {
                    stringResource(
                        R.string.schedule_between,
                        formatTime(scheduleStartMinutes),
                        formatTime(scheduleEndMinutes)
                    )
                } else {
                    stringResource(R.string.no_time_restriction)
                },
                checked = scheduleEnabled,
                onCheckedChange = onScheduleEnabledChange
            )

            if (scheduleEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = feedbackClick(onPickScheduleStart),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                R.string.schedule_from,
                                formatTime(scheduleStartMinutes)
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = feedbackClick(onPickScheduleEnd),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                R.string.schedule_to,
                                formatTime(scheduleEndMinutes)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdvancedToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val toggleContentDescription =
        stringResource(R.string.toggle_content_description, title)
    var toggleFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .controllerFocusHighlight(toggleFocused)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                }
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.55f
                )
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = feedbackChange(onCheckedChange),
            enabled = enabled,
            modifier = Modifier
                .onFocusChanged { toggleFocused = it.isFocused }
                .semantics {
                    contentDescription = toggleContentDescription
                }
        )
    }
}

