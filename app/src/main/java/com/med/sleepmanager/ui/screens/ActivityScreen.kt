package com.med.sleepmanager.ui.screens

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.feedbackClick
import java.util.Date

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ActivityLogPage(
    context: Context,
    onCopyLog: () -> Unit
) {
    val events = EventHistoryStore.recent(context)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = feedbackClick(onCopyLog)) {
                Text("Copy log")
            }
        }

        if (events.isEmpty()) {
            InfoCard(
                title = "Activity log",
                text = "No recent activity"
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
    }
}

