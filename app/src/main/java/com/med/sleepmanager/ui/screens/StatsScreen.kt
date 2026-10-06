package com.med.sleepmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.R
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.ui.feedbackClick
import java.util.Locale

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BatteryStatsPage(
    stats: BatterySleepStore.Stats
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatsCard(title = stringResource(R.string.stats_battery)) {
            StatsGrid(
                metrics = listOf(
                    stringResource(R.string.stats_current) to (
                        stats.currentPrecisePercent?.let {
                            stringResource(
                                R.string.stats_percent_value,
                                formatPercentTwoDecimals(it)
                            )
                        } ?: stats.currentPercent?.let {
                            stringResource(R.string.stats_percent_int_value, it)
                        } ?: stringResource(R.string.stats_dash)
                    ),
                    stringResource(R.string.stats_estimated_capacity) to (
                        stats.estimatedCapacityMah?.let {
                            stringResource(
                                R.string.stats_approx_mah_value,
                                formatMah(it)
                            )
                        } ?: stringResource(R.string.stats_unavailable)
                    ),
                    stringResource(R.string.stats_current_charge) to (
                        stats.currentChargeMah?.let {
                            stringResource(
                                R.string.stats_mah_value,
                                formatMah(it)
                            )
                        } ?: stringResource(R.string.stats_unavailable)
                    ),
                    stringResource(R.string.stats_battery_health) to (
                        stats.batteryHealthPercent?.let {
                            stringResource(
                                R.string.stats_percent_value,
                                formatPercentOneDecimal(it)
                            )
                        } ?: stringResource(R.string.stats_unavailable)
                    )
                )
            )
        }

        StatsCard(title = stringResource(R.string.stats_sleep_efficiency)) {
            StatsGrid(
                metrics = listOf(
                    stringResource(R.string.stats_7_day_drain) to (
                        stats.averageDrainPerHour?.let {
                            stringResource(
                                R.string.stats_percent_per_hour_value,
                                formatDrainRate(it)
                            )
                        } ?: stringResource(R.string.stats_not_enough_data)
                    ),
                    stringResource(R.string.stats_charge_drain) to (
                        stats.averageDrainMahPerHour?.let {
                            stringResource(
                                R.string.stats_mah_per_hour_value,
                                formatMahRate(it)
                            )
                        } ?: stringResource(R.string.stats_unavailable)
                    ),
                    stringResource(R.string.stats_deep_sleep) to (
                        stats.averageDeepSleepPercent?.let {
                            stringResource(
                                R.string.stats_percent_value,
                                formatPercentTwoDecimals(it)
                            )
                        } ?: stringResource(R.string.stats_collecting_data)
                    ),
                    stringResource(R.string.stats_measured_sleep) to
                        formatSleepSessionDuration(stats.totalMeasuredSleepMs)
                )
            )
        }

        StatsCard(title = stringResource(R.string.stats_standby_estimate)) {
            StatsGrid(
                metrics = listOf(
                    stringResource(R.string.stats_from_current_battery) to (
                        stats.estimatedHoursRemaining?.let {
                            formatStandbyEstimate(it)
                        } ?: stringResource(R.string.stats_not_enough_data)
                    ),
                    stringResource(R.string.stats_from_full) to (
                        stats.estimatedHoursFromFull?.let {
                            formatStandbyEstimate(it)
                        } ?: stringResource(R.string.stats_not_enough_data)
                    ),
                    stringResource(R.string.stats_best_drain) to (
                        stats.bestDrainPerHour?.let {
                            stringResource(
                                R.string.stats_percent_per_hour_value,
                                formatDrainRate(it)
                            )
                        } ?: stringResource(R.string.stats_dash)
                    ),
                    stringResource(R.string.stats_worst_drain) to (
                        stats.worstDrainPerHour?.let {
                            stringResource(
                                R.string.stats_percent_per_hour_value,
                                formatDrainRate(it)
                            )
                        } ?: stringResource(R.string.stats_dash)
                    )
                )
            )
            Text(
                stringResource(R.string.stats_standby_estimate_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        stats.lastSession?.let { last ->
            StatsCard(title = stringResource(R.string.stats_last_sleep)) {
                StatsGrid(
                    metrics = listOf(
                        stringResource(R.string.stats_duration) to
                            formatSleepSessionDuration(last.durationMs),
                        stringResource(
                            if (last.chargedDuringSleep) {
                                R.string.stats_battery_change
                            } else {
                                R.string.stats_battery_used
                            }
                        ) to formatBatteryChange(last),
                        stringResource(R.string.stats_charge_used) to (
                            last.drainMah?.let {
                                stringResource(
                                    R.string.stats_mah_value,
                                    formatMah(it)
                                )
                            } ?: if (last.chargedDuringSleep) {
                                stringResource(R.string.stats_charging_during_sleep)
                            } else {
                                stringResource(R.string.stats_unavailable)
                            }
                        ),
                        stringResource(R.string.stats_deep_sleep) to (
                            last.deepSleepPercent?.let {
                                stringResource(
                                    R.string.stats_percent_value,
                                    formatPercentTwoDecimals(it)
                                )
                            } ?: stringResource(R.string.stats_collecting_data)
                        )
                    )
                )
            }
        }

        StatsCard(title = stringResource(R.string.stats_measurement)) {
            Text(
                stringResource(
                    if (stats.averageSessionCount == 1) {
                        R.string.stats_eligible_session_singular
                    } else {
                        R.string.stats_eligible_session_plural
                    },
                    stats.averageSessionCount
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.stats_measurement_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StatsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
internal fun StatsGrid(
    metrics: List<Pair<String, String>>
) {
    metrics.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            row.forEach { pair ->
                BatteryMetric(
                    modifier = Modifier.weight(1f),
                    label = pair.first,
                    value = pair.second
                )
            }
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

internal fun formatMah(value: Double): String =
    String.format(Locale.US, "%.0f", value)

internal fun formatMahRate(value: Double): String =
    if (value < 10.0) {
        String.format(Locale.US, "%.1f", value)
    } else {
        String.format(Locale.US, "%.0f", value)
    }

internal fun formatPercentOneDecimal(value: Double): String =
    String.format(Locale.US, "%.1f", value)

internal fun formatPercentTwoDecimals(value: Double): String =
    String.format(Locale.getDefault(), "%.2f", value)

@Composable
internal fun formatStandbyEstimate(hours: Double): String {
    if (!hours.isFinite() || hours <= 0.0) {
        return stringResource(R.string.stats_dash)
    }

    val totalHours = hours.toLong().coerceAtLeast(1L)
    val days = totalHours / 24L
    val remainderHours = totalHours % 24L
    return when {
        days > 0L && remainderHours > 0L ->
            stringResource(R.string.stats_days_hours, days, remainderHours)
        days > 0L ->
            stringResource(R.string.stats_days, days)
        else ->
            stringResource(R.string.stats_hours, totalHours)
    }
}

@Composable
internal fun BatteryDashboardCard(
    dashboard: BatterySleepStore.Dashboard,
    stats: BatterySleepStore.Stats
) {
    val currentText = dashboard.currentPercent?.let { "$it%" } ?: "—"
    val last = dashboard.lastSession

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                InteractiveBatteryGauge(
                    percent = dashboard.currentPercent,
                    charging = dashboard.currentCharging,
                    currentChargeMah = stats.currentChargeMah,
                    estimatedCapacityMah = stats.estimatedCapacityMah,
                    estimatedHoursRemaining = stats.estimatedHoursRemaining,
                    averageDrainPerHour = stats.averageDrainPerHour,
                    averageDeepSleepPercent = stats.averageDeepSleepPercent,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 280.dp)
                )

                val gaugeBodyHeight =
                    if (LocalConfiguration.current.screenWidthDp < 600) 66.dp else 74.dp

                Column(
                    modifier = Modifier
                        .align(Alignment.Top)
                        .height(gaugeBodyHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        currentText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (dashboard.currentCharging) {
                        Text(
                            stringResource(R.string.stats_charging),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (last == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_last_sleep),
                        value = stringResource(R.string.stats_dash)
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_drain),
                        value = stringResource(R.string.stats_dash)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_7_day_average),
                        value = stringResource(R.string.stats_dash)
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_samples),
                        value = "0"
                    )
                }

                Text(
                    stringResource(R.string.stats_first_sleep_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_last_sleep),
                        value = stringResource(
                            R.string.stats_battery_change_and_duration,
                            formatBatteryChange(last),
                            formatSleepSessionDuration(last.durationMs)
                        )
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_drain),
                        value =
                            when {
                                last.chargedDuringSleep ->
                                    stringResource(R.string.stats_charging_during_sleep)
                                last.durationMs < BatterySleepStore.MIN_AVERAGE_DURATION_MS ->
                                    stringResource(R.string.stats_short_session)
                                else -> last.drainPerHour?.let {
                                    stringResource(
                                        R.string.stats_percent_per_hour_value,
                                        formatDrainRate(it)
                                    )
                                } ?: stringResource(R.string.stats_dash)
                            }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_7_day_average),
                        value =
                            dashboard.averageDrainPerHour?.let {
                                stringResource(
                                    R.string.stats_percent_per_hour_value,
                                    formatDrainRate(it)
                                )
                            } ?: stringResource(R.string.stats_not_enough_data)
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_samples),
                        value = dashboard.averageSessionCount.toString()
                    )
                }

                when {
                    last.chargedDuringSleep -> {
                        Text(
                            stringResource(R.string.stats_charging_excluded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    last.durationMs < BatterySleepStore.MIN_AVERAGE_DURATION_MS -> {
                        Text(
                            stringResource(
                                if (dashboard.averageSessionCount == 0) {
                                    R.string.stats_complete_long_sleep
                                } else {
                                    R.string.stats_short_session_excluded
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        last.drainMah?.let {
                            Text(
                                stringResource(
                                    R.string.stats_measured_charge_used,
                                    String.format(Locale.US, "%.0f", it)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InteractiveBatteryGauge(
    percent: Int?,
    charging: Boolean,
    currentChargeMah: Double?,
    estimatedCapacityMah: Double?,
    estimatedHoursRemaining: Double?,
    averageDrainPerHour: Double?,
    averageDeepSleepPercent: Double?,
    modifier: Modifier = Modifier
) {
    var infoIndex by remember { mutableIntStateOf(0) }
    var hasInteracted by remember { mutableStateOf(false) }
    val level = (percent ?: 0).coerceIn(0, 100)
    val animatedLevel by animateFloatAsState(
        targetValue = level / 100f,
        label = "Battery level"
    )

    // Above 50%, follow the app/system Material accent so the battery naturally
    // matches the current Android UI. Lower levels intentionally override it.
    val levelColor = when {
        charging -> MaterialTheme.colorScheme.primary
        percent == null -> MaterialTheme.colorScheme.outline
        level >= 50 -> MaterialTheme.colorScheme.primary
        level >= 20 -> Color(0xFFE7B55E)
        else -> Color(0xFFE97878)
    }

    val chargeText =
        if (currentChargeMah != null && estimatedCapacityMah != null) {
            stringResource(
                R.string.stats_charge_over_capacity,
                formatMah(currentChargeMah),
                formatMah(estimatedCapacityMah)
            )
        } else {
            stringResource(R.string.stats_charge_data_unavailable)
        }

    val standbyText =
        estimatedHoursRemaining?.let {
            stringResource(
                R.string.stats_estimated_standby_value,
                formatStandbyEstimate(it)
            )
        } ?: stringResource(R.string.stats_estimated_standby_not_enough)

    val sleepDrainText =
        averageDrainPerHour?.let {
            stringResource(
                R.string.stats_sleep_drain_value,
                formatDrainRate(it)
            )
        } ?: stringResource(R.string.stats_sleep_drain_not_enough)

    val deepSleepText =
        averageDeepSleepPercent?.let {
            stringResource(
                R.string.stats_deep_sleep_value,
                formatPercentTwoDecimals(it)
            )
        } ?: stringResource(R.string.stats_deep_sleep_collecting)

    val infoTexts = listOf(
        chargeText,
        standbyText,
        sleepDrainText,
        deepSleepText
    )
    val currentInfo = infoTexts[infoIndex]
    val gaugeContentDescription =
        if (percent != null) {
            stringResource(
                R.string.stats_battery_gauge_content_description,
                percent,
                currentInfo
            )
        } else {
            stringResource(
                R.string.stats_battery_gauge_unavailable_content_description,
                currentInfo
            )
        }

    val onGaugeClick = feedbackClick {
        hasInteracted = true
        infoIndex = (infoIndex + 1) % infoTexts.size
    }

    // Do not keep an infinite frame-clock animation alive while discharging.
    val chargingAlpha = if (charging) {
        val chargingTransition = rememberInfiniteTransition(label = "Charging pulse")
        val alpha by chargingTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Charging alpha"
        )
        alpha
    } else {
        1f
    }

    val compactBatteryLayout = LocalConfiguration.current.screenWidthDp < 600
    val gaugeHeight = if (compactBatteryLayout) 66.dp else 74.dp
    val terminalHeight = if (compactBatteryLayout) 30.dp else 34.dp
    val bodyShape = RoundedCornerShape(if (compactBatteryLayout) 18.dp else 20.dp)
    val innerShape = RoundedCornerShape(if (compactBatteryLayout) 12.dp else 14.dp)

    Column(
        modifier = modifier
            .testTag("battery_gauge")
            .clickable(onClick = onGaugeClick)
            .semantics {
                contentDescription = gaugeContentDescription
            },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier
                .testTag("battery_gauge_body")
                .fillMaxWidth()
                .height(gaugeHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(bodyShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 3.dp,
                        color = levelColor,
                        shape = bodyShape
                    )
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(innerShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedLevel)
                            .background(levelColor.copy(alpha = 0.30f))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Crossfade(
                                targetState = infoIndex,
                                label = "Battery info"
                            ) { index ->
                                Text(
                                    text = infoTexts[index],
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (charging) {
                            Text(
                                "⚡",
                                modifier = Modifier.alpha(chargingAlpha),
                                style = MaterialTheme.typography.titleMedium,
                                color = levelColor
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(10.dp)
                    .height(terminalHeight)
                    .clip(RoundedCornerShape(0.dp, 6.dp, 6.dp, 0.dp))
                    .background(levelColor)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(end = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = !hasInteracted) {
                Text(
                    stringResource(R.string.stats_tap_to_cycle),
                    modifier = Modifier.testTag("battery_gauge_hint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!hasInteracted) {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Row(
                modifier = Modifier.testTag("battery_gauge_page_indicator"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(infoTexts.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == infoIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
internal fun BatteryMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun PendingRestoreCard(
    problem: String,
    onForget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.stats_pending_restore_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                stringResource(R.string.stats_pending_restore_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            OutlinedButton(
                onClick = feedbackClick(onForget)
            ) {
                Text(stringResource(R.string.stats_forget_pending_restore))
            }
        }
    }
}

internal fun formatBatteryChange(
    session: BatterySleepStore.SleepSession
): String {
    val precise = session.preciseBatteryChangePercent
    if (precise != null && precise.isFinite()) {
        val magnitude = formatPercentTwoDecimals(kotlin.math.abs(precise))
        return when {
            precise > 0.0 -> "+$magnitude%"
            precise < 0.0 -> "−$magnitude%"
            else -> "0.00%"
        }
    }

    val delta = session.endPercent - session.startPercent
    return when {
        delta > 0 -> "+${delta}%"
        delta < 0 -> "−${-delta}%"
        else -> "0%"
    }
}

@Composable
internal fun formatSleepSessionDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L ->
            stringResource(R.string.stats_hours_minutes, hours, minutes)
        hours > 0L ->
            stringResource(R.string.stats_hours, hours)
        totalMinutes > 0L ->
            stringResource(R.string.stats_minutes, totalMinutes)
        else ->
            stringResource(R.string.stats_under_one_minute)
    }
}

internal fun formatDrainRate(value: Double): String =
    when {
        value < 0.01 -> "<0.01"
        value < 1.0 -> String.format(Locale.US, "%.2f", value)
        else -> String.format(Locale.US, "%.1f", value)
    }

