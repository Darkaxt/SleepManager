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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.ui.components.SectionTitle
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.ui.label

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
        "Immediate" to 0L,
        "5 s" to 5000L,
        "10 s" to 10000L
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Grace period",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            if (customDelayEnabled) {
                "Using custom delay from Advanced settings."
            } else {
                "Wait before applying sleep actions. If the device wakes during this period, no changes are applied."
            },
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
                    label = { Text("Custom") }
                )
            }
        }

        if (customDelayEnabled) {
            TextButton(onClick = feedbackClick(onCustom)) {
                Text("Advanced • ${formatDuration(customDelayMs)}")
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
            title = "Advanced sync behavior",
            subtitle = "Control how supported sync apps run during sleep and wake transitions."
        )

        SettingsCard {
            AdvancedToggleRow(
                title = "Periodic sync while sleeping",
                subtitle = if (syncConditionsAvailable) {
                    "While the device stays asleep, sync managed clients every 24h, then stop them and restore the sleep state."
                } else {
                    "Requires BasicSync 3.19+. Syncthing-Fork support is planned."
                },
                checked = periodicSyncWhileSleeping,
                enabled = syncConditionsAvailable,
                onCheckedChange = onPeriodicSyncWhileSleepingChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = "Sync then stop on sleep & wake",
                subtitle = if (syncConditionsAvailable) {
                    "Sync managed clients after wake and again before sleep. After each sync completes, stop them to reduce background battery use."
                } else {
                    "Requires BasicSync 3.19+. Syncthing-Fork support is planned."
                },
                checked = syncThenStopOnSleepWake,
                enabled = syncConditionsAvailable,
                onCheckedChange = onSyncThenStopOnSleepWakeChange
            )
        }

        SectionTitle(
            title = "Advanced sleep conditions",
            subtitle = "Fine-tune when sleep actions are allowed and when they begin."
        )

        SettingsCard(
            modifier = Modifier.bringIntoViewRequester(customDelayRequester)
        ) {
            AdvancedToggleRow(
                title = "Use custom delay",
                subtitle = if (customDelayEnabled) {
                    "Sleep actions will start after ${formatDuration(customDelayMs)}."
                } else {
                    "Choose a longer delay before sleep actions than the standard Grace period options."
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
                        "Delay before sleep actions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    val options = listOf(
                        "1 min" to 60_000L,
                        "5 min" to 300_000L,
                        "10 min" to 600_000L,
                        "30 min" to 1_800_000L
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
            title = "Conditions",
            subtitle = "All enabled conditions must be true for sleep actions to run."
        )
        SettingsCard {
            AdvancedToggleRow(
                title = "Battery level",
                subtitle = if (batteryConditionEnabled) {
                    "Only when battery is below ${batteryBelowPercent}%"
                } else {
                    "Ignore battery percentage"
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
                                label = { Text("< ${level}%") }
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
                title = "Not charging",
                subtitle = if (notChargingOnly) {
                    "Only when the device is unplugged"
                } else {
                    "Ignore charging state"
                },
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
                        "Battery Saver",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        when (batterySaverMode) {
                            AppPreferences.BATTERY_SAVER_ON -> "Only when Android Battery Saver is ON"
                            AppPreferences.BATTERY_SAVER_OFF -> "Only when Android Battery Saver is OFF"
                            else -> "Ignore Battery Saver state"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            "Ignore" to AppPreferences.BATTERY_SAVER_IGNORE,
                            "ON" to AppPreferences.BATTERY_SAVER_ON,
                            "OFF" to AppPreferences.BATTERY_SAVER_OFF
                        )
                        items(modes.size) { index ->
                            val (label, mode) = modes[index]
                            FilterChip(
                                modifier = Modifier
                                    .testTag("battery_saver_mode_$mode")
                                    .semantics {
                                        contentDescription =
                                            "Battery Saver $label option"
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
                title = "Schedule",
                subtitle = if (scheduleEnabled) {
                    "Only between ${formatTime(scheduleStartMinutes)} and ${formatTime(scheduleEndMinutes)}"
                } else {
                    "No time restriction"
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
                        Text("From ${formatTime(scheduleStartMinutes)}")
                    }
                    OutlinedButton(
                        onClick = feedbackClick(onPickScheduleEnd),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("To ${formatTime(scheduleEndMinutes)}")
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
            modifier = Modifier.semantics {
                contentDescription = "$title toggle"
            }
        )
    }
}

