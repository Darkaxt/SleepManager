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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.ui.label
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
        StatsCard(title = "Battery") {
            StatsGrid(
                metrics = listOf(
                    "Current" to (
                        stats.currentPrecisePercent?.let {
                            "${formatPercentTwoDecimals(it)}%"
                        } ?: stats.currentPercent?.let { "$it%" } ?: "—"
                    ),
                    "Estimated capacity" to (
                        stats.estimatedCapacityMah?.let {
                            "~${formatMah(it)} mAh"
                        } ?: "Unavailable"
                    ),
                    "Current charge" to (
                        stats.currentChargeMah?.let {
                            "${formatMah(it)} mAh"
                        } ?: "Unavailable"
                    )
                )
            )
        }

        StatsCard(title = "Sleep efficiency") {
            StatsGrid(
                metrics = listOf(
                    "7-day drain" to (
                        stats.averageDrainPerHour?.let {
                            "${formatDrainRate(it)}% / h"
                        } ?: "Not enough data"
                    ),
                    "Charge drain" to (
                        stats.averageDrainMahPerHour?.let {
                            "${formatMahRate(it)} mAh / h"
                        } ?: "Unavailable"
                    ),
                    "Deep sleep" to (
                        stats.averageDeepSleepPercent?.let {
                            "${formatPercentTwoDecimals(it)}%"
                        } ?: "Collecting data"
                    ),
                    "Measured sleep" to formatSleepSessionDuration(
                        stats.totalMeasuredSleepMs
                    )
                )
            )
        }

        StatsCard(title = "Standby estimate") {
            StatsGrid(
                metrics = listOf(
                    "From current battery" to (
                        stats.estimatedHoursRemaining?.let {
                            formatStandbyEstimate(it)
                        } ?: "Not enough data"
                    ),
                    "From 100%" to (
                        stats.estimatedHoursFromFull?.let {
                            formatStandbyEstimate(it)
                        } ?: "Not enough data"
                    ),
                    "Best drain" to (
                        stats.bestDrainPerHour?.let {
                            "${formatDrainRate(it)}% / h"
                        } ?: "—"
                    ),
                    "Worst drain" to (
                        stats.worstDrainPerHour?.let {
                            "${formatDrainRate(it)}% / h"
                        } ?: "—"
                    )
                )
            )
            Text(
                "Standby estimates use eligible sleep sessions of at least 3 hours from the last 7 days and are only indicative.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        stats.lastSession?.let { last ->
            StatsCard(title = "Last sleep") {
                StatsGrid(
                    metrics = listOf(
                        "Duration" to formatSleepSessionDuration(last.durationMs),
                        (if (last.chargedDuringSleep) "Battery change" else "Battery used") to
                            formatBatteryChange(last),
                        "Charge used" to (
                            last.drainMah?.let {
                                "${formatMah(it)} mAh"
                            } ?: if (last.chargedDuringSleep) {
                                "Charging during sleep"
                            } else {
                                "Unavailable"
                            }
                        ),
                        "Deep sleep" to (
                            last.deepSleepPercent?.let {
                                "${formatPercentTwoDecimals(it)}%"
                            } ?: "Collecting data"
                        )
                    )
                )
            }
        }

        StatsCard(title = "Measurement") {
            Text(
                "${stats.averageSessionCount} eligible sleep session" +
                    if (stats.averageSessionCount == 1) "." else "s.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Sessions shorter than 3 hours or containing charging are excluded from battery statistics. Shorter sleeps still appear in Last sleep and history. Capacity is an estimate when Android does not expose a readable full-capacity value.",
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

internal fun formatStandbyEstimate(hours: Double): String {
    if (!hours.isFinite() || hours <= 0.0) return "—"

    val totalHours = hours.toLong().coerceAtLeast(1L)
    val days = totalHours / 24L
    val remainderHours = totalHours % 24L
    return when {
        days > 0L && remainderHours > 0L -> "${days}d ${remainderHours}h"
        days > 0L -> "${days}d"
        else -> "${totalHours}h"
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
                            "Charging",
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
                        label = "Last sleep",
                        value = "—"
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Drain",
                        value = "—"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "7-day average",
                        value = "—"
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Samples",
                        value = "0"
                    )
                }

                Text(
                    "Sleep statistics will appear after your first sleep session of at least 3 hours without charging.",
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
                        label = "Last sleep",
                        value =
                            "${formatBatteryChange(last)} • ${formatSleepSessionDuration(last.durationMs)}"
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Drain",
                        value =
                            when {
                                last.chargedDuringSleep -> "Charging during sleep"
                                last.durationMs < BatterySleepStore.MIN_AVERAGE_DURATION_MS -> "Short session"
                                else -> last.drainPerHour?.let {
                                    "${formatDrainRate(it)}% / h"
                                } ?: "—"
                            }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "7-day average",
                        value =
                            dashboard.averageDrainPerHour?.let {
                                "${formatDrainRate(it)}% / h"
                            } ?: "Not enough data"
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Samples",
                        value = dashboard.averageSessionCount.toString()
                    )
                }

                when {
                    last.chargedDuringSleep -> {
                        Text(
                            "Sessions with charging are excluded from the 7-day drain average.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    last.durationMs < BatterySleepStore.MIN_AVERAGE_DURATION_MS -> {
                        Text(
                            if (dashboard.averageSessionCount == 0) {
                                "Complete a sleep session of at least 3 hours to start building sleep averages."
                            } else {
                                "This short session is excluded from the 7-day average."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        last.drainMah?.let {
                            Text(
                                "Measured charge used: ${String.format(Locale.US, "%.0f", it)} mAh",
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
            "${formatMah(currentChargeMah)} / ~${formatMah(estimatedCapacityMah)} mAh"
        } else {
            "Charge data unavailable"
        }

    val standbyText =
        estimatedHoursRemaining?.let {
            "Estimated standby • ${formatStandbyEstimate(it)}"
        } ?: "Estimated standby • Not enough data"

    val sleepDrainText =
        averageDrainPerHour?.let {
            "Sleep drain • ${formatDrainRate(it)}% / h"
        } ?: "Sleep drain • Not enough data"

    val deepSleepText =
        averageDeepSleepPercent?.let {
            "Deep sleep • ${formatPercentTwoDecimals(it)}%"
        } ?: "Deep sleep • Collecting data"

    val infoTexts = listOf(
        chargeText,
        standbyText,
        sleepDrainText,
        deepSleepText
    )
    val currentInfo = infoTexts[infoIndex]

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
                contentDescription =
                    "Battery ${percent?.let { "$it percent" } ?: "level unavailable"}. " +
                        "$currentInfo. Tap to cycle battery stats."
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
                    "Tap to cycle stats",
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
                "Pending restore needs attention",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "SleepManager keeps the transaction instead of pretending the restore succeeded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            OutlinedButton(
                onClick = feedbackClick(onForget)
            ) {
                Text("Forget pending restore")
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

internal fun formatSleepSessionDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        totalMinutes > 0L -> "${totalMinutes}m"
        else -> "<1m"
    }
}

internal fun formatDrainRate(value: Double): String =
    when {
        value < 0.01 -> "<0.01"
        value < 1.0 -> String.format(Locale.US, "%.2f", value)
        else -> String.format(Locale.US, "%.1f", value)
    }

