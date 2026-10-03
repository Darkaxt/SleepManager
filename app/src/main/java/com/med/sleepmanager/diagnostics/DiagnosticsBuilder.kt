package com.med.sleepmanager.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.DiagnosticsCycleStore
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.data.DiagnosticsTransitionStore
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.device.DeviceControlStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import com.med.sleepmanager.protection.LidMonitor
import com.med.sleepmanager.rules.BatteryCapacitySelection
import com.med.sleepmanager.rules.BatteryCapacitySource
import com.med.sleepmanager.rules.BatteryCurrentSource
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

        val helperPackageInfo = runCatching {
            context.packageManager
                .getPackageInfo(HelperController.PACKAGE, 0)
        }.getOrNull()
        val helperVersion = helperPackageInfo?.versionName
        val helperVersionCode =
            helperPackageInfo?.let {
                if (Build.VERSION.SDK_INT >= 28) {
                    it.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    it.versionCode.toLong()
                }
            }

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
        val wifiDiagnostic = DiagnosticsStateStore.lastWifiToggleDiagnostic(context)
        val processExitHistory = ProcessExitHistoryReader.read(context)
        val deviceCapabilities = DeviceControlController.capabilities(context)
        val batterySaverOwned = DeviceControlStore.batterySaver(context)
        val chargingSeparationOwned =
            DeviceControlStore.chargingSeparation(context)
        val lidSupported = LidMonitor.isSupported()
        val lidDetection = LidMonitor.detectionDescription()
        val batterySnapshot = BatterySleepStore.currentSnapshot(context)
        val batterySelection = batterySnapshot.capacitySelection
        val batteryStats = BatterySleepStore.stats(context)
        val falseWakeCount = DiagnosticsStateStore.falseWakeCount(context)
        val lastFalseWakeTime = DiagnosticsStateStore.lastFalseWakeTime(context)
        val advancedDiagnostics = AppPreferences.advancedDiagnosticsEnabled(context)
        val cycleHistory =
            if (advancedDiagnostics) DiagnosticsCycleStore.diagnosticHistory(context)
            else emptyList()
        val storedCycleCount =
            if (advancedDiagnostics) DiagnosticsCycleStore.storedCount(context)
            else 0
        val transitions =
            if (advancedDiagnostics) DiagnosticsTransitionStore.all(context)
            else emptyList()
        val latestCycle = cycleHistory.firstOrNull()
        val currentRestoreProblem = SleepCycleStore.restoreProblem(context)
        val pendingRestores =
            buildList {
                if (cycle.active && cycle.helperExpected && !cycle.helperRestored) {
                    add("Helper")
                }
                if (syncthingPending) add("Syncthing-Fork")
                if (tailscalePending != null) add("Tailscale")
                if (jamesDspPending != null) add("JamesDSP")
                if (basicSyncPending != null) add("BasicSync")
                if (batterySaverOwned.owned) add("Battery Saver")
                if (chargingSeparationOwned.owned) add("Charging Separation")
            }
        val memoryInfo =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.let { manager ->
                    ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
                }

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        return buildString {
            appendLine("SleepManager diagnostics")
            appendLine("Diagnostics format: 2")
            appendLine("Generated: ${formatter.format(Date())}")
            appendLine()
            appendLine("Quick summary")
            val summaryProblem =
                currentRestoreProblem ?: latestCycle?.restoreProblem
            val overall =
                when {
                    summaryProblem != null ->
                        "ATTENTION · restore problem"
                    pendingRestores.isNotEmpty() ->
                        "ATTENTION · restore pending"
                    memoryInfo?.lowMemory == true ->
                        "ATTENTION · Android reports low memory"
                    AppPreferences.isEnabled(context) && !SleepManagerService.running ->
                        "ATTENTION · manager enabled but foreground service is not running"
                    else ->
                        "OK"
                }
            appendLine("- Overall: $overall")
            appendLine(
                "- Current transaction: " +
                    if (cycle.active) {
                        "ACTIVE · cycle=${cycle.cycleId}"
                    } else {
                        "idle"
                    }
            )
            appendLine(
                "- Pending restores: " +
                    (pendingRestores.takeIf { it.isNotEmpty() }?.joinToString() ?: "none")
            )
            appendLine("- Restore problem: ${summaryProblem ?: "none"}")
            appendLine(
                "- Latest diagnostics cycle: " +
                    if (latestCycle == null) {
                        "none"
                    } else {
                        "${latestCycle.status} · falseWakes=${latestCycle.falseWakeCount}" +
                            (latestCycle.restoreProblem?.let { " · restoreProblem=$it" } ?: "")
                    }
            )
            batteryStats.lastSession?.let { session ->
                appendLine(
                    "- Last sleep: " +
                        "${formatDurationMs(session.durationMs)} · " +
                        "drain=${session.drainPerHour?.let { "%.3f%%/h".format(Locale.US, it) } ?: "unknown"} · " +
                        "deepSleep=${session.deepSleepPercent?.let { "%.1f%%".format(Locale.US, it) } ?: "unknown"} · " +
                        "falseWakes=${session.falseWakeCount}"
                )
            } ?: appendLine("- Last sleep: none")
            appendLine(
                "- Battery now: " +
                    "${batterySnapshot.percent?.let { "$it%" } ?: "unknown"} · " +
                    "capacity=${batterySelection.selectedFullUah?.let { "%.0f mAh".format(Locale.US, it / 1000.0) } ?: "unknown"} · " +
                    "source=${batteryCapacitySource(batterySelection)}" +
                    if (batterySelection.learnedFullSuspect) " · learnedFull=SUSPECT" else ""
            )
            appendLine(
                "- Memory now: " +
                    if (memoryInfo == null) {
                        "unavailable"
                    } else {
                        "${formatBytesAsMiB(memoryInfo.availMem)} available / " +
                            "${formatBytesAsMiB(memoryInfo.totalMem)} · lowMemory=${memoryInfo.lowMemory}"
                    }
            )
            appendLine(
                "- Lid: " +
                    if (lidSupported) {
                        "available · $lidDetection"
                    } else {
                        "unavailable · $lidDetection"
                    }
            )
            appendLine()
            appendLine("App")
            appendLine("- Version: $versionName ($versionCode)")
            appendLine("- Enabled: ${AppPreferences.isEnabled(context)}")
            appendLine("- Service running: ${SleepManagerService.running}")
            appendLine(
                "- Advanced diagnostics: " +
                    if (advancedDiagnostics) "ON" else "OFF · runtime snapshots disabled"
            )
            appendLine("- Home grace: ${AppPreferences.sleepGraceMs(context)} ms")
            appendLine("- Custom delay enabled: ${AppPreferences.customDelayEnabled(context)}")
            appendLine("- Custom delay: ${AppPreferences.customDelayMs(context)} ms")
            appendLine("- Effective sleep delay: ${AppPreferences.effectiveSleepDelayMs(context)} ms")
            appendLine()
            appendLine("SleepManager process exit history")
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
            appendLine(
                "- External app exit reasons (Quickstep/Cocoon/etc.): " +
                    "not directly readable by a normal Android app without DUMP permission"
            )
            appendLine(
                "- ADB evidence: optional troubleshooting fallback only; " +
                    "not required for normal SleepManager operation"
            )
            appendLine()
            appendLine("Memory")
            if (memoryInfo == null) {
                appendLine("- unavailable")
            } else {
                appendLine("- Total RAM: ${formatBytesAsMiB(memoryInfo.totalMem)}")
                appendLine("- Available RAM: ${formatBytesAsMiB(memoryInfo.availMem)}")
                appendLine("- Low memory: ${memoryInfo.lowMemory}")
                appendLine("- Low-memory threshold: ${formatBytesAsMiB(memoryInfo.threshold)}")
            }
            appendLine()
            appendLine("Battery raw / interpreted diagnostics")
            appendLine("- Percent: ${batterySnapshot.percent?.let { "$it%" } ?: "unknown"}")
            appendLine("- Charging/powered: ${batterySnapshot.charging}")
            appendLine("- External power connected: ${batterySnapshot.externalPowerConnected}")
            appendLine("- Raw charge_counter: ${batterySnapshot.chargeCounterUah?.let { "$it uAh" } ?: "unavailable"}")
            appendLine("- Raw charge_full: ${batterySnapshot.fullChargeUah?.let { "$it uAh" } ?: "unavailable"}")
            appendLine("- Raw charge_full_design: ${batterySnapshot.designChargeUah?.let { "$it uAh" } ?: "unavailable"}")
            appendLine(
                "- Selected full capacity: " +
                    (
                        batterySelection.selectedFullUah
                            ?.let { "%.1f mAh".format(Locale.US, it / 1000.0) }
                            ?: "unavailable"
                    )
            )
            appendLine(
                "- Displayed current charge: " +
                    (
                        batterySelection.displayedCurrentUah
                            ?.let { "%.1f mAh".format(Locale.US, it / 1000.0) }
                            ?: "unavailable"
                    )
            )
            appendLine("- Capacity source: ${batteryCapacitySource(batterySelection)}")
            appendLine("- Current-charge source: ${batteryCurrentSource(batterySelection)}")
            appendLine("- Learned full capacity suspect: ${batterySelection.learnedFullSuspect}")
            if (batterySelection.learnedFullSuspect) {
                appendLine(
                    "- Capacity fallback: raw charge_full is implausibly high " +
                        "relative to charge_full_design; display uses the sane fallback"
                )
            }
            appendLine("- Estimated/display capacity: ${batteryStats.estimatedCapacityMah?.let { "%.1f mAh".format(Locale.US, it) } ?: "unavailable"}")
            batteryStats.lastSession?.let { session ->
                appendLine("- Last sleep started: ${formatter.format(Date(session.startedAt))}")
                appendLine("- Last sleep ended: ${formatter.format(Date(session.endedAt))}")
                appendLine(
                    "- Last sleep duration: " +
                        "${formatDurationMs(session.durationMs)} (${session.durationMs} ms)"
                )
                appendLine("- Last sleep drain: ${session.drainPerHour?.let { "%.3f%%/h".format(Locale.US, it) } ?: "unavailable"}")
                appendLine("- Last sleep false wakes: ${session.falseWakeCount}")
                appendLine(
                    "- Last deep sleep duration: " +
                        (
                            session.deepSleepMs
                                ?.let { "${formatDurationMs(it)} (${it} ms)" }
                                ?: "unavailable"
                        )
                )
                appendLine("- Last deep sleep percentage: ${session.deepSleepPercent?.let { "%.1f%%".format(Locale.US, it) } ?: "unavailable"}")
            } ?: appendLine("- Last sleep session: none")
            appendLine("- Average deep sleep: ${batteryStats.averageDeepSleepPercent?.let { "%.1f%%".format(Locale.US, it) } ?: "unavailable"}")
            appendLine()
            appendLine("Device")
            appendLine("- Model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Device/product: ${Build.DEVICE} / ${Build.PRODUCT}")
            appendLine("- Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("- Build: ${Build.ID}")
            appendLine("- Fingerprint: ${Build.FINGERPRINT}")
            appendLine("- Lid support: $lidSupported")
            appendLine("- Lid detection: $lidDetection")
            if (!lidSupported) {
                appendLine(
                    "- Lid access note: sensor is not readable; on AYN Thor, " +
                        "Force SELinux can block the required input-device access"
                )
            }
            appendLine("- PServer: ${if (deviceCapabilities.pServerAvailable) "available" else "unavailable"}")
            appendLine("- Battery Saver control: ${deviceCapabilities.batterySaverControl}")
            appendLine("- Charging Separation control: ${deviceCapabilities.chargingSeparationControl}")
            appendLine()
            appendLine("Selected actions")
            appendLine("- Wi-Fi: ${AppPreferences.manageWifi(context)}")
            appendLine("- Bluetooth: ${AppPreferences.manageBluetooth(context)}")
            appendLine("- Battery Saver during sleep: ${AppPreferences.manageBatterySaver(context)}")
            appendLine("- Charging Separation with lid closed: ${AppPreferences.manageChargingSeparationWithLid(context)}")
            appendLine("- Syncthing-Fork: ${AppPreferences.manageSyncthing(context)}")
            appendLine("- Tailscale: ${AppPreferences.manageTailscale(context)}")
            appendLine("- JamesDSP: ${AppPreferences.manageJamesDsp(context)}")
            appendLine("- BasicSync: ${AppPreferences.manageBasicSync(context)}")
            appendLine("- Closed-lid protection: ${AppPreferences.manageClosedLidProtection(context)}")
            appendLine("- Sleep when external display disconnects: ${AppPreferences.dockDisconnectSleeps(context)}")
            appendLine("- Power button sleeps with lid closed: ${AppPreferences.closedLidPowerSleeps(context)}")
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
            appendLine("- Battery Saver: ${if (DeviceControlController.batterySaverEnabled(context)) "ON" else "OFF"}")
            appendLine(
                "- Charging Separation: " +
                    when (DeviceControlController.chargingSeparationState(context)) {
                        true -> "ON"
                        false -> "OFF"
                        null -> "unavailable"
                    }
            )
            appendLine(
                "- Helper: " +
                    if (helperVersion != null) {
                        "installed • $helperVersion" +
                            (helperVersionCode?.let { " ($it)" } ?: "")
                    } else {
                        "not installed"
                    }
            )
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
            appendLine("- Closed-lid false wakes recorded: $falseWakeCount")
            appendLine(
                "- Last false wake: " +
                    if (lastFalseWakeTime > 0L) {
                        formatter.format(Date(lastFalseWakeTime))
                    } else {
                        "none"
                    }
            )
            appendLine("- Cycle id: ${cycle.cycleId}")
            appendLine("- Helper expected: ${cycle.helperExpected}")
            appendLine("- Helper sleep requested: ${cycle.helperSleepRequested}")
            appendLine("- Helper restored: ${cycle.helperRestored}")
            appendLine("- Wi-Fi managed: ${cycle.wifiManaged}")
            appendLine("- Bluetooth managed: ${cycle.bluetoothManaged}")
            appendLine("- Battery Saver restore owned: ${batterySaverOwned.owned} · previous=${batterySaverOwned.previous}")
            appendLine(
                "- Battery Saver deferred for external power: " +
                    DeviceControlStore.batterySaverDeferredForExternalPower(context)
            )
            appendLine("- Charging Separation restore owned: ${chargingSeparationOwned.owned} · previous=${chargingSeparationOwned.previous}")
            appendLine("- Last service recovery: ${DeviceControlStore.lastServiceRecovery(context) ?: "none"}")
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

            appendLine()
            appendLine("Structured transitions")
            if (transitions.isEmpty()) {
                appendLine("- none")
            } else {
                transitions.forEach { transition ->
                    appendLine(
                        "- ${DiagnosticsTransitionStore.label(transition.componentId)} · " +
                            formatter.format(Date(transition.updatedAt))
                    )
                    appendLine(
                        "  initial=${transition.initialState ?: "unknown"} -> " +
                            "request=${transition.sleepRequest ?: "none"} -> " +
                            "sleepResult=${transition.sleepResult ?: "unknown"} -> " +
                            "sleepState=${transition.sleepState ?: "unknown"}"
                    )
                    appendLine(
                        "  restoreTarget=${transition.restoreTarget ?: "none"} -> " +
                            "restoreResult=${transition.restoreResult ?: "not run"} -> " +
                            "final=${transition.finalState ?: "unknown"}"
                    )
                    transition.note?.let {
                        appendLine("  note=$it")
                    }
                }
            }

            appendLine()
            appendLine(
                "Cycle history (showing ${cycleHistory.size} of $storedCycleCount stored)"
            )
            if (cycleHistory.isEmpty()) {
                appendLine("- none")
            } else {
                cycleHistory.forEachIndexed { index, record ->
                    appendLine()
                    appendLine(
                        "[Cycle ${index + 1}] session=${record.sessionId} · status=${record.status}"
                    )
                    appendLine(
                        "- Sleep started: ${formatter.format(Date(record.startedAt))}"
                    )
                    appendLine(
                        "- Real wake: " +
                            if (record.wakeAt > 0L) {
                                formatter.format(Date(record.wakeAt))
                            } else {
                                "not recorded"
                            }
                    )
                    appendLine(
                        "- Diagnostics cycle completed: " +
                            if (record.completedAt > 0L) {
                                formatter.format(Date(record.completedAt))
                            } else {
                                "pending"
                            }
                    )
                    appendLine(
                        "- Transaction cycle id: " +
                            record.transactionCycleId.takeIf { it > 0L }
                                ?.toString()
                                .orEmpty()
                                .ifBlank { "none" }
                    )
                    appendLine(
                        "- Managed settings: " +
                            "Wi-Fi=${record.settings.wifi} · " +
                            "Bluetooth=${record.settings.bluetooth} · " +
                            "BatterySaver=${record.settings.batterySaver} · " +
                            "ChargingSeparation=${record.settings.chargingSeparation} · " +
                            "Syncthing=${record.settings.syncthing} · " +
                            "Tailscale=${record.settings.tailscale} · " +
                            "JamesDSP=${record.settings.jamesDsp} · " +
                            "BasicSync=${record.settings.basicSync} · " +
                            "ClosedLid=${record.settings.closedLidProtection}"
                    )
                    appendLine(
                        "- Helper: expected=${record.helperExpected} · " +
                            "sleepRequested=${record.helperSleepRequested} · " +
                            "restored=${record.helperRestored} · " +
                            "wifiManaged=${record.wifiManaged} · " +
                            "bluetoothManaged=${record.bluetoothManaged}"
                    )
                    appendLine("- False wakes: ${record.falseWakeCount}")
                    appendLine(
                        "- Restore problem: ${record.restoreProblem ?: "none"}"
                    )

                    val battery = record.battery
                    if (battery == null) {
                        appendLine("- Battery session: unavailable/pending")
                    } else {
                        appendLine(
                            "- Battery session: " +
                                "${battery.startPercent}% → ${battery.endPercent}% · " +
                                "duration=${formatDurationMs(battery.durationMs)} · " +
                                "deepSleep=" +
                                (
                                    battery.deepSleepMs
                                        ?.let { formatDurationMs(it) }
                                        ?: "unavailable"
                                ) +
                                " · falseWakes=${battery.falseWakeCount} · " +
                                "charged=${battery.chargedDuringSleep}"
                        )
                        appendLine(
                            "- Battery drain: " +
                                "${battery.preciseDrainPercent?.let { "%.3f%%".format(Locale.US, it) } ?: "${battery.drainPercent}%"}" +
                                (
                                    battery.drainMah
                                        ?.let { " · %.1f mAh".format(Locale.US, it) }
                                        ?: ""
                                )
                        )
                    }

                    if (record.connectors.isEmpty()) {
                        appendLine("- Connector ownership: none")
                    } else {
                        appendLine("- Connector ownership:")
                        record.connectors.forEach { connector ->
                            appendLine(
                                "  • ${connector.connectorId}: " +
                                    if (connector.pending) {
                                        "pending"
                                    } else {
                                        "cleared"
                                    } +
                                    " · restoreTokenPresent=${connector.restoreTokenPresent}"
                            )
                        }
                    }

                    appendLine(
                        "- System snapshots: ${record.systemSnapshots.size}"
                    )
                    record.systemSnapshots.forEach { snapshot ->
                        appendLine(
                            "  • ${formatter.format(Date(snapshot.timestamp))} · ${snapshot.phase}" +
                                (snapshot.trimMemoryLevel?.let { " · trimLevel=$it" } ?: "")
                        )
                        appendLine(
                            "    RAM: " +
                                "avail=${snapshot.availableRamBytes?.let(::formatBytesAsMiB) ?: "unknown"} / " +
                                "total=${snapshot.totalRamBytes?.let(::formatBytesAsMiB) ?: "unknown"} · " +
                                "lowMemory=${snapshot.lowMemory ?: "unknown"} · " +
                                "threshold=${snapshot.lowMemoryThresholdBytes?.let(::formatBytesAsMiB) ?: "unknown"} · " +
                                "lowRamDevice=${snapshot.lowRamDevice ?: "unknown"}"
                        )
                        appendLine(
                            "    App memory: " +
                                "PSS=${snapshot.processPssKb?.let { "$it kB" } ?: "not sampled"} · " +
                                "privateDirty=${snapshot.processPrivateDirtyKb?.let { "$it kB" } ?: "not sampled"} · " +
                                "heap=${formatBytesAsMiB(snapshot.heapUsedBytes)}/${formatBytesAsMiB(snapshot.heapMaxBytes)} · " +
                                "nativeHeap=${formatBytesAsMiB(snapshot.nativeHeapAllocatedBytes)} · " +
                                "importance=${snapshot.processImportance?.let(ProcessExitReasonFormatter::importanceLabel) ?: "unknown"} · " +
                                "processCpu=${snapshot.processCpuTimeMs} ms · " +
                                "capture=${snapshot.captureDurationMs} ms"
                        )
                        appendLine(
                            "    Power: " +
                                "interactive=${snapshot.interactive ?: "unknown"} · " +
                                "batterySaver=${snapshot.powerSaveMode ?: "unknown"} · " +
                                "idle=${snapshot.deviceIdleMode ?: "unknown"} · " +
                                "batteryOptimizationsIgnored=${snapshot.ignoringBatteryOptimizations ?: "unknown"} · " +
                                "backgroundRestricted=${snapshot.backgroundRestricted ?: "unknown"} · " +
                                "thermal=${snapshot.thermalStatus?.toString() ?: "unknown"}"
                        )
                        appendLine(
                            "    Battery/network: " +
                                "battery=${snapshot.batteryPercent?.let { "$it%" } ?: "unknown"} · " +
                                "status=${snapshot.batteryStatus ?: "unknown"} · " +
                                "health=${snapshot.batteryHealth ?: "unknown"} · " +
                                "temp=${snapshot.batteryTemperatureTenthsC?.let { "%.1f°C".format(Locale.US, it / 10.0) } ?: "unknown"} · " +
                                "plugged=${snapshot.pluggedType ?: "unknown"} · " +
                                "networkActive=${snapshot.activeNetwork ?: "unknown"} · " +
                                "validated=${snapshot.networkValidated ?: "unknown"} · " +
                                "internet=${snapshot.networkInternet ?: "unknown"} · " +
                                "wifi=${snapshot.networkWifi ?: "unknown"} · " +
                                "cellular=${snapshot.networkCellular ?: "unknown"}"
                        )
                        snapshot.latestProcessExit?.let { exit ->
                            appendLine(
                                "    Previous SleepManager exit: " +
                                    "${formatter.format(Date(exit.timestamp))} · " +
                                    ProcessExitReasonFormatter.reasonLabel(exit.reason) +
                                    " · status=${exit.status} · importance=" +
                                    ProcessExitReasonFormatter.importanceLabel(exit.importance) +
                                    " · PSS=${exit.pssKb} kB · RSS=${exit.rssKb} kB"
                            )
                            exit.description?.let {
                                appendLine("      Description: $it")
                            }
                        }
                    }

                    appendLine("- Cycle events (${record.events.size}):")
                    if (record.events.isEmpty()) {
                        appendLine("  • none")
                    } else {
                        record.events.forEach { event ->
                            appendLine(
                                "  • ${formatter.format(Date(event.timestamp))} · ${event.message}"
                            )
                        }
                    }
                }
            }

            val events = EventHistoryStore.diagnosticHistory(context)
            appendLine()
            appendLine("Extended activity history (${events.size})")
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

    private fun formatBytesAsMiB(bytes: Long): String =
        "%.1f MiB".format(Locale.US, bytes.toDouble() / (1024.0 * 1024.0))

    private fun formatDurationMs(durationMs: Long): String {
        val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return "${hours}h ${minutes}m"
    }

    private fun batteryCapacitySource(
        selection: BatteryCapacitySelection
    ): String =
        when (selection.capacitySource) {
            BatteryCapacitySource.LEARNED_FULL -> "charge_full"
            BatteryCapacitySource.DESIGN_FULL -> "charge_full_design"
            BatteryCapacitySource.COUNTER_DERIVED -> "charge_counter / percent estimate"
            BatteryCapacitySource.UNAVAILABLE -> "unavailable"
        }

    private fun batteryCurrentSource(
        selection: BatteryCapacitySelection
    ): String =
        when (selection.currentSource) {
            BatteryCurrentSource.RAW_COUNTER -> "charge_counter"
            BatteryCurrentSource.PERCENT_DERIVED -> "percent × selected full capacity"
            BatteryCurrentSource.UNAVAILABLE -> "unavailable"
        }
}
