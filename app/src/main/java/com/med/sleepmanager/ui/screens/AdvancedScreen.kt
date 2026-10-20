package com.med.sleepmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.R
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.ui.components.SectionTitle
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.feedbackClick

import com.med.sleepmanager.ui.feedbackChange

@Composable
internal fun SleepGraceSelector(
    valueMs: Long,
    customDelayEnabled: Boolean,
    customDelayMs: Long,
    onChange: (Long) -> Unit,
    onCustomDelaySelected: (Long) -> Unit
) {
    val options: List<Pair<String, Long?>> = listOf(
        stringResource(R.string.grace_immediate) to 0L,
        stringResource(R.string.duration_5_seconds) to 5000L,
        stringResource(R.string.duration_10_seconds) to 10000L,
        stringResource(R.string.custom) to null
    )
    val customDurations = listOf(
        stringResource(R.string.duration_1_minute) to 60_000L,
        stringResource(R.string.duration_2_minutes) to 120_000L,
        stringResource(R.string.duration_3_minutes) to 180_000L,
        stringResource(R.string.duration_5_minutes) to 300_000L,
        stringResource(R.string.duration_10_minutes) to 600_000L,
        stringResource(R.string.duration_15_minutes) to 900_000L,
        stringResource(R.string.duration_30_minutes) to 1_800_000L,
        stringResource(R.string.duration_1_hour) to 3_600_000L
    )
    val selectedCustomDuration = customDurations
        .firstOrNull { (_, duration) -> duration == customDelayMs }?.first
        ?: stringResource(R.string.unknown)
    var showCustomPicker by remember { mutableStateOf(false) }
    var stagedCustomDelay by remember { mutableStateOf(customDelayMs) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.grace_period), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            stringResource(if (customDelayEnabled) R.string.grace_period_custom_delay_active else R.string.grace_period_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, duration) ->
                        val selected = if (duration == null) customDelayEnabled else !customDelayEnabled && valueMs == duration
                        OutlinedButton(
                            onClick = feedbackClick {
                                if (duration == null) {
                                    stagedCustomDelay = customDelayMs
                                    showCustomPicker = true
                                } else onChange(duration)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp)
                                .testTag(if (duration == null) "grace_option_custom" else "grace_option_$duration")
                                .semantics { this.selected = selected },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text(label, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }
        }
        if (customDelayEnabled) {
            Text(
                text = stringResource(R.string.grace_custom_selected_duration, selectedCustomDuration),
                modifier = Modifier.testTag("grace_custom_selected_duration"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showCustomPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomPicker = false },
            title = { Text(stringResource(R.string.use_custom_delay)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.custom_delay_description), style = MaterialTheme.typography.bodyMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customDurations.chunked(2).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (label, duration) ->
                                    val selected = stagedCustomDelay == duration
                                    OutlinedButton(
                                        onClick = feedbackClick { stagedCustomDelay = duration },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            .testTag("custom_delay_option_$duration")
                                            .semantics { this.selected = selected },
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                        )
                                    ) { Text(label, maxLines = 2) }
                                }
                                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = feedbackClick {
                    showCustomPicker = false
                    onCustomDelaySelected(stagedCustomDelay)
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = feedbackClick { showCustomPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
internal fun AdvancedSettingsPage(
    periodicSyncWhileSleeping: Boolean,
    syncThenStopOnSleepWake: Boolean,
    syncConditionsAvailable: Boolean,
    onPeriodicSyncWhileSleepingChange: (Boolean) -> Unit,
    onSyncThenStopOnSleepWakeChange: (Boolean) -> Unit,
    batteryConditionEnabled: Boolean,
    batteryBelowPercent: Int,
    notChargingOnly: Boolean,
    batterySaverMode: String,
    scheduleEnabled: Boolean,
    scheduleStartMinutes: Int,
    scheduleEndMinutes: Int,
    onBatteryConditionEnabledChange: (Boolean) -> Unit,
    onBatteryBelowPercentChange: (Int) -> Unit,
    onNotChargingOnlyChange: (Boolean) -> Unit,
    onBatterySaverModeChange: (String) -> Unit,
    onScheduleEnabledChange: (Boolean) -> Unit,
    onPickScheduleStart: () -> Unit,
    onPickScheduleEnd: () -> Unit
) {
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
                val decreaseDescription = stringResource(R.string.battery_threshold_decrease)
                val increaseDescription = stringResource(R.string.battery_threshold_increase)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = feedbackClick { onBatteryBelowPercentChange((batteryBelowPercent - 10).coerceAtLeast(20)) },
                            enabled = batteryBelowPercent > 20,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("battery_threshold_decrease")
                                .semantics { contentDescription = decreaseDescription }
                        ) { Text("−") }
                        Text(
                            stringResource(R.string.battery_level_chip, batteryBelowPercent),
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(
                            onClick = feedbackClick { onBatteryBelowPercentChange((batteryBelowPercent + 10).coerceAtMost(60)) },
                            enabled = batteryBelowPercent < 60,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("battery_threshold_increase")
                                .semantics { contentDescription = increaseDescription }
                        ) { Text("+") }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                .semantics {
                    contentDescription = toggleContentDescription
                }
        )
    }
}

