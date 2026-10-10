package com.med.sleepmanager.ui.screens

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.R
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.ui.activity.ActivityTimelineBuilder
import com.med.sleepmanager.ui.activity.ActivityTimelineGroup
import com.med.sleepmanager.ui.activity.ActivityTimelineKind
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.controller.controllerFocusHighlight
import com.med.sleepmanager.ui.feedbackChange
import java.util.Date

@Composable
internal fun ActivityLogPage(
    context: Context
) {
    val events = EventHistoryStore.recent(context)
    val timeline = ActivityTimelineBuilder.build(events)
    var advancedDiagnostics by remember(context) {
        mutableStateOf(AppPreferences.advancedDiagnosticsEnabled(context))
    }
    var diagnosticsFocused by remember {
        mutableStateOf(false)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (timeline.isEmpty()) {
            InfoCard(
                title = stringResource(R.string.nav_activity_log),
                text = stringResource(R.string.activity_no_recent_activity)
            )
        } else {
            timeline.forEach { group ->
                ActivityTimelineCard(
                    context = context,
                    group = group
                )
            }
        }

        SettingsCard {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .controllerFocusHighlight(diagnosticsFocused)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        stringResource(R.string.activity_advanced_diagnostics),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.activity_advanced_diagnostics_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = advancedDiagnostics,
                    onCheckedChange = feedbackChange { enabled ->
                        advancedDiagnostics = enabled
                        AppPreferences.setAdvancedDiagnosticsEnabled(context, enabled)
                    },
                    modifier =
                        Modifier.onFocusChanged {
                            diagnosticsFocused = it.isFocused
                        }
                )
            }
        }
    }
}

@Composable
private fun ActivityTimelineCard(
    context: Context,
    group: ActivityTimelineGroup
) {
    val started = Date(group.startedAt)
    val dateTime =
        DateFormat.getMediumDateFormat(context).format(started) +
            " • " +
            DateFormat.getTimeFormat(context).format(started)
    val errorColor = MaterialTheme.colorScheme.error
    val sequence =
        buildAnnotatedString {
            group.steps.forEachIndexed { index, step ->
                if (index > 0) {
                    append(" → ")
                }

                if (isActivityProblemText(step.text)) {
                    withStyle(
                        SpanStyle(
                            color = errorColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(step.text)
                    }
                } else {
                    append(step.text)
                }
            }
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activityTimelineTitle(group.kind),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dateTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard {
            Text(
                text = sequence,
                modifier =
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 13.dp
                    ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun isActivityProblemText(text: String): Boolean {
    val value = text.lowercase()
    return listOf(
        "could not",
        "failed",
        "pending",
        "timed out",
        "unavailable",
        "not compatible",
        "not confirmed",
        "did not",
        "no network",
        "with some errors",
        "low memory"
    ).any(value::contains)
}

@Composable
private fun activityTimelineTitle(
    kind: ActivityTimelineKind
): String =
    stringResource(
        when (kind) {
            ActivityTimelineKind.SLEEP -> R.string.activity_timeline_sleep
            ActivityTimelineKind.WAKE -> R.string.activity_timeline_wake
            ActivityTimelineKind.MAINTENANCE ->
                R.string.activity_timeline_maintenance
            ActivityTimelineKind.PROTECTION ->
                R.string.activity_timeline_protection
            ActivityTimelineKind.RECOVERY ->
                R.string.activity_timeline_recovery
            ActivityTimelineKind.SYSTEM ->
                R.string.activity_timeline_system
        }
    )
