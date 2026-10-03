package com.med.sleepmanager.ui.screens

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.R
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.feedbackChange
import com.med.sleepmanager.ui.feedbackClick
import java.util.Date

@Composable
internal fun ActivityLogPage(
    context: Context,
    onCopyLog: () -> Unit
) {
    val events = EventHistoryStore.recent(context)
    var advancedDiagnostics by remember(context) {
        mutableStateOf(AppPreferences.advancedDiagnosticsEnabled(context))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = feedbackClick(onCopyLog)) {
                Text(stringResource(R.string.activity_copy_log))
            }
        }

        if (events.isEmpty()) {
            InfoCard(
                title = stringResource(R.string.nav_activity_log),
                text = stringResource(R.string.activity_no_recent_activity)
            )
        } else {
            SettingsCard {
                events.forEachIndexed { index, event ->
                    val date = Date(event.timestamp)
                    val timestamp =
                        DateFormat.getMediumDateFormat(context).format(date) +
                            " • " +
                            DateFormat.getTimeFormat(context).format(date)

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            event.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            timestamp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (index != events.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        SettingsCard {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
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
                    }
                )
            }
        }
    }
}
