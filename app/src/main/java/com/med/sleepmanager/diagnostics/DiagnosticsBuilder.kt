package com.med.sleepmanager.diagnostics

import android.content.Context
import android.os.Build
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.sync.ManagedSyncProviders
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsBuilder {

    fun build(
        context: Context,
        wifiState: Boolean?,
        bluetoothState: Boolean?,
        syncthingState: SyncthingController.RuntimeState?,
        tailscaleConnected: Boolean?
    ): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val helperVersion = runCatching {
            context.packageManager
                .getPackageInfo(HelperController.PACKAGE, 0)
                .versionName
        }.getOrNull()

        val syncthing = SyncthingController.selectedTarget(context)
        val cycle = SleepCycleStore.current(context)
        val syncthingPending =
            SleepCycleStore.hasConnectorChange(context, SyncthingConnector.id)
        val tailscalePending =
            SleepCycleStore.connectorChange(context, TailscaleConnector.id)
        val tailscalePackage = TailscaleController.selectedPackage(context)
        val tailscaleVersion = TailscaleController.versionName(context)
        val jamesDsp = JamesDspController.selectedTarget(context)
        val jamesDspPending =
            SleepCycleStore.connectorChange(context, JamesDspConnector.id)
        val basicSyncVersion = BasicSyncController.versionName(context)
        val basicSyncState = BasicSyncController.lastObservedState()
        val basicSyncPending =
            SleepCycleStore.connectorChange(context, BasicSyncConnector.id)
        val wifiDiagnostic = AppPreferences.lastWifiToggleDiagnostic(context)
        val processExitHistory = ProcessExitHistoryReader.read(context)

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        return buildString {
            appendLine("SleepManager diagnostics")
            appendLine("Generated: ${formatter.format(Date())}")
            appendLine()
            appendLine("App")
            appendLine("- Version: $versionName ($versionCode)")
            appendLine("- Enabled: ${AppPreferences.isEnabled(context)}")
            appendLine("- Service running: ${SleepManagerService.running}")
            appendLine("- Home grace: ${AppPreferences.sleepGraceMs(context)} ms")
            appendLine("- Custom delay enabled: ${AppPreferences.customDelayEnabled(context)}")
            appendLine("- Custom delay: ${AppPreferences.customDelayMs(context)} ms")
            appendLine("- Effective sleep delay: ${AppPreferences.effectiveSleepDelayMs(context)} ms")
            appendLine()
            appendLine("Process exit history")
            if (processExitHistory == null) {
                appendLine(
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        "- unavailable on Android < 11"
                    } else {
                        "- unavailable"
                    }
                )
            } else {
                appendLine(
                    "- Low-memory reason reporting: " +
                        when (processExitHistory.lowMemoryReasonSupported) {
                            true -> "supported"
                            false -> "not supported"
                            null -> "unknown"
                        }
                )

                if (processExitHistory.records.isEmpty()) {
                    appendLine("- No previous process exits reported")
                } else {
                    processExitHistory.records.forEachIndexed { index, exit ->
                        val label =
                            if (index == 0) "Latest" else "Previous ${index + 1}"
                        appendLine(
                            "- $label: " +
                                "${formatter.format(Date(exit.timestamp))} • " +
                                ProcessExitReasonFormatter.reasonLabel(exit.reason) +
                                " • status=${exit.status} • importance=" +
                                ProcessExitReasonFormatter.importanceLabel(exit.importance)
                        )
                        if (exit.pssKb > 0L || exit.rssKb > 0L) {
                            appendLine(
                                "  Memory sample: PSS=${exit.pssKb} kB • RSS=${exit.rssKb} kB"
                            )
                        }
                        exit.description?.let {
                            appendLine("  Description: $it")
                        }
                    }
                }
            }
            appendLine()
            appendLine("Device")
            appendLine("- Model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("- Thor hall support: ${ThorLidMonitor.isSupported()}")
            appendLine()
            appendLine("Selected actions")
            appendLine("- Wi-Fi: ${AppPreferences.manageWifi(context)}")
            appendLine("- Bluetooth: ${AppPreferences.manageBluetooth(context)}")
            appendLine("- Syncthing-Fork: ${AppPreferences.manageSyncthing(context)}")
            appendLine("- Tailscale: ${AppPreferences.manageTailscale(context)}")
            appendLine("- JamesDSP: ${AppPreferences.manageJamesDsp(context)}")
            appendLine("- BasicSync: ${AppPreferences.manageBasicSync(context)}")
            appendLine("- Thor protection: ${AppPreferences.manageThorProtection(context)}")
            appendLine()
            appendLine("Advanced sync conditions")
            appendLine("- Periodic sync while sleeping: ${AppPreferences.periodicSyncWhileSleeping(context)}")
            appendLine("- Sync then stop on sleep & wake: ${AppPreferences.syncThenStopOnSleepWake(context)}")
            appendLine("- Completion-aware providers ready: ${ManagedSyncProviders.completionReady(context)}")
            appendLine("- Periodic runtime active-capable: ${AppPreferences.periodicSyncWhileSleeping(context) && ManagedSyncProviders.completionReady(context)}")
            appendLine("- Sleep/wake runtime active-capable: ${AppPreferences.syncThenStopOnSleepWake(context) && ManagedSyncProviders.completionReady(context)}")
            appendLine()
            appendLine("Advanced sleep conditions")
            appendLine("- Battery condition: ${AppPreferences.batteryConditionEnabled(context)}")
            appendLine("- Battery below: ${AppPreferences.batteryBelowPercent(context)}%")
            appendLine("- Not charging only: ${AppPreferences.notChargingOnly(context)}")
            appendLine("- Battery Saver mode: ${AppPreferences.batterySaverMode(context)}")
            appendLine("- Schedule enabled: ${AppPreferences.scheduleEnabled(context)}")
            appendLine("- Schedule start: ${formatMinutes(AppPreferences.scheduleStartMinutes(context))}")
            appendLine("- Schedule end: ${formatMinutes(AppPreferences.scheduleEndMinutes(context))}")
            appendLine()
            appendLine("Current state")
            appendLine("- Wi-Fi: ${formatState(wifiState)}")
            appendLine("- Bluetooth: ${formatState(bluetoothState)}")
            appendLine("- Helper: ${if (helperVersion != null) "installed • $helperVersion" else "not installed"}")
            appendLine(
                "- Syncthing target: " +
                    if (syncthing != null) "${syncthing.displayName} • ${syncthing.packageName}"
                    else "not detected"
            )
            appendLine(
                "- Syncthing state: " +
                    (syncthingState?.name ?: "unknown")
            )
            appendLine(
                "- Tailscale / TailDNS: " +
                    if (tailscaleVersion != null) "installed • $tailscaleVersion"
                    else "not installed"
            )
            appendLine("- Tailscale / TailDNS target: ${tailscalePackage ?: "not detected"}")
            appendLine(
                "- Tailscale / TailDNS state: " +
                    when (tailscaleConnected) {
                        true -> "CONNECTED"
                        false -> "DISCONNECTED"
                        null -> "unknown"
                    }
            )
            appendLine(
                "- JamesDSP: " +
                    if (jamesDsp != null) {
                        "installed" +
                            (jamesDsp.versionName?.let { " • $it" } ?: "") +
                            " • ${jamesDsp.packageName}"
                    } else {
                        "not installed"
                    }
            )
            appendLine(
                "- BasicSync: " +
                    if (basicSyncVersion != null) {
                        "installed • $basicSyncVersion • ${BasicSyncController.PACKAGE}"
                    } else {
                        "not installed"
                    }
            )
            appendLine("- BasicSync state API: ${if (BasicSyncController.supportsStateApi(context)) "supported (3.18+)" else "legacy / unavailable"}")
            appendLine("- BasicSync sync counters: ${if (BasicSyncController.supportsSyncCounters(context)) "supported (3.19+)" else "unavailable"}")
            appendLine(
                "- BasicSync observed state: " +
                    if (basicSyncState != null) {
                        "${basicSyncState.mode} / ${basicSyncState.runState}"
                    } else {
                        "unknown"
                    }
            )
            appendLine(
                "- BasicSync blocked reasons: " +
                    (basicSyncState?.blockedReasons
                        ?.takeIf { it.isNotEmpty() }
                        ?.joinToString()
                        ?: "none")
            )
            appendLine(
                "- BasicSync counters: " +
                    (basicSyncState?.syncCounters?.toString() ?: "unavailable")
            )
            appendLine()
            appendLine("Last Wi-Fi toggle")
            if (wifiDiagnostic == null) {
                appendLine("- none")
            } else {
                appendLine("- Phase: ${wifiDiagnostic.phase}")
                appendLine("- Action: ${wifiDiagnostic.action}")
                appendLine("- Attempted: ${wifiDiagnostic.attempted}")
                appendLine(
                    "- Result: " +
                        if (!wifiDiagnostic.attempted) {
                            "not required"
                        } else if (wifiDiagnostic.success) {
                            "success"
                        } else {
                            "failed"
                        }
                )
                appendLine(
                    "- Airplane mode: " +
                        if (wifiDiagnostic.airplaneMode) "ON" else "OFF"
                )
                if (wifiDiagnostic.timestamp > 0L) {
                    appendLine(
                        "- Time: ${formatter.format(Date(wifiDiagnostic.timestamp))}"
                    )
                }
            }
            appendLine()
            appendLine("Transaction")
            appendLine("- Active: ${cycle.active}")
            appendLine("- Cycle id: ${cycle.cycleId}")
            appendLine("- Helper expected: ${cycle.helperExpected}")
            appendLine("- Helper sleep requested: ${cycle.helperSleepRequested}")
            appendLine("- Helper restored: ${cycle.helperRestored}")
            appendLine("- Wi-Fi managed: ${cycle.wifiManaged}")
            appendLine("- Bluetooth managed: ${cycle.bluetoothManaged}")
            appendLine("- Syncthing restore pending: $syncthingPending")
            appendLine(
                "- Tailscale transaction: " +
                    (tailscalePending?.restoreToken ?: "none")
            )
            appendLine(
                "- JamesDSP transaction: " +
                    (jamesDspPending?.restoreToken ?: "none")
            )
            appendLine(
                "- BasicSync transaction: " +
                    (basicSyncPending?.restoreToken ?: "none")
            )

            val events = EventHistoryStore.recent(context)
            appendLine()
            appendLine("Recent activity (${events.size})")
            if (events.isEmpty()) {
                appendLine("- none")
            } else {
                events.forEach { event ->
                    appendLine(
                        "- ${formatter.format(Date(event.timestamp))} • ${event.message}"
                    )
                }
            }
        }
    }

    private fun formatMinutes(minutes: Int): String {
        val safe = minutes.coerceIn(0, 1439)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    private fun formatState(value: Boolean?): String = when (value) {
        true -> "ON"
        false -> "OFF"
        null -> "unknown"
    }
}
