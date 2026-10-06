package com.med.sleepmanager.ui.components

import android.content.Context
import android.text.format.DateFormat
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.ui.screens.formatDuration
import java.util.Date

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BehaviorCard(
    wifi: Boolean,
    bluetooth: Boolean,
    batterySaver: Boolean,
    syncthing: Boolean,
    tailscale: Boolean,
    jamesDsp: Boolean,
    basicSync: Boolean,
    raOfflineProxy: Boolean,
    closedLidProtection: Boolean,
    chargingSeparationWithLid: Boolean,
    sleepOnExternalDisplayDisconnect: Boolean,
    powerButtonSleepsWithLidClosed: Boolean,
    sleepGraceMs: Long,
    advancedConditions: List<String>,
    periodicSyncWhileSleeping: Boolean,
    syncThenStopOnSleepWake: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSleepAction =
        wifi || bluetooth || batterySaver || syncthing || tailscale ||
            jamesDsp || basicSync || raOfflineProxy ||
            syncThenStopOnSleepWake
    val hasClamshellBehavior =
        closedLidProtection ||
            chargingSeparationWithLid ||
            sleepOnExternalDisplayDisconnect ||
            powerButtonSleepsWithLidClosed

    val sleepDuration =
        if (sleepGraceMs > 0L && hasSleepAction) {
            formatDuration(sleepGraceMs)
        } else {
            null
        }
    val waitLine =
        sleepDuration?.let {
            stringResource(R.string.behavior_wait, it)
        }
    val onlyIfPrefix = stringResource(R.string.behavior_only_if_prefix)
    val syncBeforeSleep = stringResource(R.string.behavior_sync_before_sleep)
    val pauseSyncthing = stringResource(R.string.behavior_pause_syncthing)
    val disconnectTailscale = stringResource(R.string.behavior_disconnect_tailscale)
    val powerOffJamesDsp = stringResource(R.string.behavior_power_off_jamesdsp)
    val stopBasicSync = stringResource(R.string.behavior_stop_basicsync)
    val stopRaOfflineProxy =
        stringResource(R.string.behavior_stop_raofflineproxy)
    val enableBatterySaver = stringResource(R.string.behavior_enable_battery_saver)
    val wifiOff = stringResource(R.string.behavior_wifi_off)
    val bluetoothOff = stringResource(R.string.behavior_bluetooth_off)
    val noScreenOffActions = stringResource(R.string.behavior_no_screen_off_actions)
    val restoreWifi = stringResource(R.string.behavior_restore_wifi)
    val restoreBluetooth = stringResource(R.string.behavior_restore_bluetooth)
    val restoreBatterySaver = stringResource(R.string.behavior_restore_battery_saver)
    val resumeSyncthing = stringResource(R.string.behavior_resume_syncthing)
    val restoreTailscale = stringResource(R.string.behavior_restore_tailscale)
    val restoreJamesDsp = stringResource(R.string.behavior_restore_jamesdsp)
    val restoreBasicSync = stringResource(R.string.behavior_restore_basicsync)
    val restoreRaOfflineProxy =
        stringResource(R.string.behavior_restore_raofflineproxy)
    val syncAfterWake = stringResource(R.string.behavior_sync_after_wake)
    val closedLidReturnToSleep =
        stringResource(R.string.behavior_closed_lid_return_to_sleep)
    val disableChargingSeparation =
        stringResource(R.string.behavior_disable_charging_separation)
    val sleepOnDisplayDisconnect =
        stringResource(R.string.behavior_sleep_on_display_disconnect)
    val powerButtonSleep = stringResource(R.string.behavior_power_button_sleep)
    val periodicSyncLine = stringResource(R.string.behavior_periodic_sync)
    val wifiLabel = stringResource(R.string.wifi)
    val bluetoothLabel = stringResource(R.string.bluetooth)
    val batterySaverLabel = stringResource(R.string.battery_saver)
    val syncthingLabel = stringResource(R.string.behavior_syncthing)
    val tailscaleLabel = stringResource(R.string.integration_tailscale)
    val jamesDspLabel = stringResource(R.string.integration_jamesdsp)
    val basicSyncLabel = stringResource(R.string.integration_basicsync)
    val raOfflineProxyLabel =
        stringResource(R.string.integration_raofflineproxy)
    val sleepWakeSyncLabel = stringResource(R.string.behavior_sleep_wake_sync)
    val periodicSyncLabel = stringResource(R.string.behavior_periodic_sync_short)
    val conditionSummary =
        if (advancedConditions.isNotEmpty()) {
            stringResource(
                if (advancedConditions.size == 1) {
                    R.string.behavior_condition_singular
                } else {
                    R.string.behavior_condition_plural
                },
                advancedConditions.size
            )
        } else {
            null
        }
    val clamshellLabel = stringResource(R.string.behavior_clamshell)
    val noActionsLabel = stringResource(R.string.behavior_no_actions)
    val nothingToRestore = stringResource(R.string.behavior_nothing_to_restore)

    val sleepLines = buildList {
        waitLine?.let(::add)
        advancedConditions.forEach {
            add("$onlyIfPrefix $it")
        }
        if (syncThenStopOnSleepWake) add(syncBeforeSleep)
        if (syncthing) add(pauseSyncthing)
        if (tailscale) add(disconnectTailscale)
        if (jamesDsp) add(powerOffJamesDsp)
        if (basicSync && !syncThenStopOnSleepWake) add(stopBasicSync)
        if (raOfflineProxy) add(stopRaOfflineProxy)
        if (batterySaver) add(enableBatterySaver)
        if (wifi) add(wifiOff)
        if (bluetooth) add(bluetoothOff)
        if (!hasSleepAction) add(noScreenOffActions)
    }

    val wakeLines = buildList {
        if (wifi) add(restoreWifi)
        if (bluetooth) add(restoreBluetooth)
        if (batterySaver) add(restoreBatterySaver)
        if (syncthing) add(resumeSyncthing)
        if (tailscale) add(restoreTailscale)
        if (jamesDsp) add(restoreJamesDsp)
        if (basicSync && !syncThenStopOnSleepWake) add(restoreBasicSync)
        if (raOfflineProxy) add(restoreRaOfflineProxy)
        if (syncThenStopOnSleepWake) add(syncAfterWake)
    }

    val clamshellLines = buildList {
        if (closedLidProtection) add(closedLidReturnToSleep)
        if (chargingSeparationWithLid) add(disableChargingSeparation)
        if (sleepOnExternalDisplayDisconnect) add(sleepOnDisplayDisconnect)
        if (powerButtonSleepsWithLidClosed) add(powerButtonSleep)
    }

    val sleepMaintenanceLines = buildList {
        if (periodicSyncWhileSleeping) add(periodicSyncLine)
    }

    val compactSleepSummary = buildList {
        sleepDuration?.let(::add)
        if (wifi) add(wifiLabel)
        if (bluetooth) add(bluetoothLabel)
        if (batterySaver) add(batterySaverLabel)
        if (syncthing) add(syncthingLabel)
        if (tailscale) add(tailscaleLabel)
        if (jamesDsp) add(jamesDspLabel)
        if (basicSync) add(basicSyncLabel)
        if (raOfflineProxy) add(raOfflineProxyLabel)
        if (syncThenStopOnSleepWake) add(sleepWakeSyncLabel)
        if (periodicSyncWhileSleeping) add(periodicSyncLabel)
        conditionSummary?.let(::add)
        if (hasClamshellBehavior) add(clamshellLabel)
        if (!hasSleepAction && !hasClamshellBehavior && !periodicSyncWhileSleeping) {
            add(noActionsLabel)
        }
    }.joinToString(" • ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.behavior_current),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        compactSleepSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = feedbackClick { expanded = !expanded }) {
                    Text(
                        stringResource(
                            if (expanded) R.string.less else R.string.details
                        )
                    )
                }
            }

            if (expanded) {
                BehaviorGroup(
                    title = stringResource(R.string.behavior_when_screen_off),
                    lines = sleepLines
                )

                BehaviorGroup(
                    title = stringResource(R.string.behavior_when_screen_on),
                    lines = wakeLines.ifEmpty {
                        listOf(nothingToRestore)
                    }
                )

                if (sleepMaintenanceLines.isNotEmpty()) {
                    BehaviorGroup(
                        title = stringResource(R.string.behavior_while_sleeping),
                        lines = sleepMaintenanceLines
                    )
                }

                if (clamshellLines.isNotEmpty()) {
                    BehaviorGroup(
                        title = stringResource(R.string.behavior_clamshell_group),
                        lines = clamshellLines
                    )
                }

                if (wifi || bluetooth || batterySaver || basicSync || syncthing ||
                    tailscale || jamesDsp
                ) {
                    Text(
                        stringResource(R.string.behavior_restore_only_changed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (closedLidProtection) {
                    Text(
                        stringResource(R.string.behavior_false_wakes_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
@Composable
internal fun BehaviorGroup(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        lines.forEach {
            Text(
                "• $it",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun LastActivityCard(
    context: Context,
    onViewLog: () -> Unit,
    onCopyLog: () -> Unit
) {
    val event = DiagnosticsStateStore.lastEvent(context)
    val time = DiagnosticsStateStore.lastEventTime(context)

    val timeText = if (time > 0L) {
        val date = Date(time)
        DateFormat.getMediumDateFormat(context).format(date) +
            " • " + DateFormat.getTimeFormat(context).format(date)
    } else {
        null
    }

    val isStructured = event.contains(" → ")
    val phase = if (isStructured) event.substringBefore(" → ") else null
    val actions = if (isStructured) {
        event.substringAfter(" → ").split(" · ").filter { it.isNotBlank() }
    } else emptyList()

    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.last_activity),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (phase != null) {
            Text(phase, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

            actions.forEach { action ->
                val subject = when {
                    action.startsWith("Wi‑Fi ") -> "Wi‑Fi"
                    action.startsWith("Bluetooth ") -> "Bluetooth"
                    action.startsWith("Battery Saver ") -> "Battery Saver"
                    action.startsWith("Charging Separation ") -> "Charging Separation"
                    action.startsWith("Syncthing ") -> "Syncthing"
                    action.startsWith("Tailscale ") -> "Tailscale"
                    action.startsWith("JamesDSP ") -> "JamesDSP"
                    action.startsWith("BasicSync ") -> "BasicSync"
                    else -> null
                }
                val subjectLabel = when (subject) {
                    "Wi‑Fi" -> context.getString(R.string.wifi)
                    "Bluetooth" -> context.getString(R.string.bluetooth)
                    "Battery Saver" -> context.getString(R.string.battery_saver)
                    "Charging Separation" ->
                        context.getString(R.string.charging_separation)
                    "Syncthing" -> context.getString(R.string.behavior_syncthing)
                    "Tailscale" -> context.getString(R.string.integration_tailscale)
                    "JamesDSP" -> context.getString(R.string.integration_jamesdsp)
                    "BasicSync" -> context.getString(R.string.integration_basicsync)
                    else -> null
                }
                val detail = when (subject) {
                    "Wi‑Fi" -> action.removePrefix("Wi‑Fi ").replaceFirstChar { it.uppercase() }
                    "Bluetooth" -> action.removePrefix("Bluetooth ").replaceFirstChar { it.uppercase() }
                    "Battery Saver" -> action.removePrefix("Battery Saver ").replaceFirstChar { it.uppercase() }
                    "Charging Separation" -> action.removePrefix("Charging Separation ").replaceFirstChar { it.uppercase() }
                    "Syncthing" -> action.removePrefix("Syncthing ").replaceFirstChar { it.uppercase() }
                    "Tailscale" -> action.removePrefix("Tailscale ").replaceFirstChar { it.uppercase() }
                    "JamesDSP" -> action.removePrefix("JamesDSP ").replaceFirstChar { it.uppercase() }
                    "BasicSync" -> action.removePrefix("BasicSync ").replaceFirstChar { it.uppercase() }
                    else -> action
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (subjectLabel != null) {
                        Text(
                            "$subjectLabel ·",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (detail.equals("Unchanged", ignoreCase = true)) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        } else {
            Text(event, style = MaterialTheme.typography.bodyMedium)
        }

        timeText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = feedbackClick(onViewLog),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.view_log))
            }

            OutlinedButton(
                onClick = feedbackClick(onCopyLog),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.activity_copy_log))
            }
        }
    }
}

@Composable
internal fun ActivityLogDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val events = EventHistoryStore.recent(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nav_activity_log)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (events.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.activity_no_recent_activity),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(events.size) { index ->
                        val event = events[index]
                        val date = Date(event.timestamp)
                        val timestamp =
                            DateFormat.getMediumDateFormat(context).format(date) +
                                " • " +
                                DateFormat.getTimeFormat(context).format(date)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = feedbackClick(onDismiss)) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
internal fun SyncthingTargetDialog(
    targets: List<SyncthingController.Target>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (SyncthingController.Target) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_syncthing_build)) },
        text = {
            Column {
                targets.forEach { target ->
                    TextButton(
                        onClick = feedbackClick { onSelect(target) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                target.displayName,
                                modifier = Modifier.weight(1f)
                            )
                            if (target.packageName == selected) {
                                Text(
                                    stringResource(R.string.selected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = feedbackClick(onDismiss)) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

