package com.med.sleepmanager.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded, persisted diagnostics history for real screen-off/screen-on cycles.
 *
 * This is deliberately separate from [SleepCycleStore]: the ownership
 * transaction may legitimately be cleared while the device is still asleep,
 * whereas diagnostics must keep the complete sleep/wake story across several
 * cycles.
 *
 * Each cycle is stored under its own SharedPreferences key. Runtime event
 * updates therefore rewrite only the current cycle instead of reserializing
 * the full multi-cycle history on the main thread.
 */
object DiagnosticsCycleStore {
    private const val PREFS = "diagnostics_cycle_history"
    private const val KEY_CYCLE_IDS = "cycle_ids"
    private const val KEY_CURRENT_SESSION_ID = "current_session_id"
    private const val CYCLE_KEY_PREFIX = "cycle."

    const val MAX_STORED_CYCLES = 30
    private const val MAX_EVENTS_PER_CYCLE = 40
    private const val MAX_SYSTEM_SNAPSHOTS_PER_CYCLE = 6
    const val DEFAULT_DIAGNOSTIC_CYCLES = 20

    const val PHASE_SLEEP_START = "SLEEP_START"
    const val PHASE_FALSE_WAKE = "FALSE_WAKE"
    const val PHASE_REAL_WAKE = "REAL_WAKE"
    const val PHASE_SERVICE_RECOVERY = "SERVICE_RECOVERY"
    const val PHASE_MEMORY_PRESSURE = "MEMORY_PRESSURE"

    const val STATUS_SLEEPING = "SLEEPING"
    const val STATUS_AWAKE = "AWAKE"
    const val STATUS_COMPLETE = "COMPLETE"
    const val STATUS_INCOMPLETE_RESTORE = "INCOMPLETE_RESTORE"

    data class SettingsSnapshot(
        val wifi: Boolean,
        val bluetooth: Boolean,
        val batterySaver: Boolean,
        val chargingSeparation: Boolean,
        val syncthing: Boolean,
        val tailscale: Boolean,
        val jamesDsp: Boolean,
        val basicSync: Boolean,
        val raOfflineProxy: Boolean,
        val closedLidProtection: Boolean
    )

    data class Event(
        val timestamp: Long,
        val message: String
    )

    data class ProcessExitSnapshot(
        val timestamp: Long,
        val reason: Int,
        val status: Int,
        val importance: Int,
        val pssKb: Long,
        val rssKb: Long,
        val description: String?
    )

    data class SystemSnapshot(
        val timestamp: Long,
        val phase: String,
        val totalRamBytes: Long?,
        val availableRamBytes: Long?,
        val lowMemory: Boolean?,
        val lowMemoryThresholdBytes: Long?,
        val lowRamDevice: Boolean?,
        val processPssKb: Int?,
        val processPrivateDirtyKb: Int?,
        val processImportance: Int?,
        val heapUsedBytes: Long,
        val heapMaxBytes: Long,
        val nativeHeapAllocatedBytes: Long,
        val backgroundRestricted: Boolean?,
        val interactive: Boolean?,
        val powerSaveMode: Boolean?,
        val deviceIdleMode: Boolean?,
        val ignoringBatteryOptimizations: Boolean?,
        val thermalStatus: Int?,
        val batteryPercent: Int?,
        val batteryStatus: Int?,
        val batteryHealth: Int?,
        val batteryTemperatureTenthsC: Int?,
        val pluggedType: Int?,
        val activeNetwork: Boolean?,
        val networkValidated: Boolean?,
        val networkInternet: Boolean?,
        val networkWifi: Boolean?,
        val networkCellular: Boolean?,
        val uptimeMs: Long,
        val elapsedRealtimeMs: Long,
        val processCpuTimeMs: Long,
        val captureDurationMs: Long,
        val trimMemoryLevel: Int?,
        val latestProcessExit: ProcessExitSnapshot?
    )

    data class ConnectorOwnership(
        val connectorId: String,
        val takenAt: Long,
        val clearedAt: Long,
        val restoreTokenPresent: Boolean
    ) {
        val pending: Boolean
            get() = clearedAt <= 0L
    }

    data class BatterySummary(
        val startedAt: Long,
        val endedAt: Long,
        val startPercent: Int,
        val endPercent: Int,
        val drainPercent: Int,
        val durationMs: Long,
        val chargedDuringSleep: Boolean,
        val drainMah: Double?,
        val deepSleepMs: Long?,
        val preciseDrainPercent: Double?,
        val falseWakeCount: Int
    )

    data class CycleRecord(
        val sessionId: Long,
        val startedAt: Long,
        val wakeAt: Long,
        val completedAt: Long,
        val status: String,
        val settings: SettingsSnapshot,
        val transactionCycleId: Long,
        val helperExpected: Boolean,
        val helperSleepRequested: Boolean,
        val helperRestored: Boolean,
        val wifiManaged: Boolean,
        val bluetoothManaged: Boolean,
        val falseWakeCount: Int,
        val restoreProblem: String?,
        val connectors: List<ConnectorOwnership>,
        val battery: BatterySummary?,
        val systemSnapshots: List<SystemSnapshot>,
        val events: List<Event>
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun begin(context: Context): CycleRecord {
        val p = prefs(context)
        val currentId = p.getLong(KEY_CURRENT_SESSION_ID, 0L)
        val current = readRecord(p, currentId)

        if (current != null && current.wakeAt <= 0L) {
            return current
        }

        if (current?.status == STATUS_AWAKE) {
            writeRecord(
                prefs = p,
                record =
                    current.copy(
                        status = STATUS_INCOMPLETE_RESTORE,
                        completedAt =
                            current.completedAt.takeIf { it > 0L }
                                ?: System.currentTimeMillis()
                    ),
                synchronous = true
            )
        }

        val ids = readIds(p)
        val now = System.currentTimeMillis()
        val nextId =
            maxOf(
                now,
                (ids.maxOrNull() ?: 0L).let {
                    if (it == Long.MAX_VALUE) it else it + 1L
                }
            )

        val record =
            CycleRecord(
                sessionId = nextId,
                startedAt = now,
                wakeAt = 0L,
                completedAt = 0L,
                status = STATUS_SLEEPING,
                settings = settingsSnapshot(context),
                transactionCycleId = 0L,
                helperExpected = false,
                helperSleepRequested = false,
                helperRestored = true,
                wifiManaged = false,
                bluetoothManaged = false,
                falseWakeCount = 0,
                restoreProblem = null,
                connectors = emptyList(),
                battery = null,
                systemSnapshots = emptyList(),
                events =
                    listOf(
                        Event(
                            timestamp = now,
                            message = "Screen OFF → diagnostics cycle started"
                        )
                    )
            )

        val updatedIds =
            (listOf(record.sessionId) + ids.filterNot { it == record.sessionId })
                .take(MAX_STORED_CYCLES)
        val removedIds = ids.filterNot { it in updatedIds }

        val editor =
            p.edit()
                .putString(cycleKey(record.sessionId), record.toJson().toString())
                .putString(KEY_CYCLE_IDS, idsToJson(updatedIds))
                .putLong(KEY_CURRENT_SESSION_ID, record.sessionId)
        removedIds.forEach { editor.remove(cycleKey(it)) }
        editor.commit()

        return record
    }

    @Synchronized
    fun attachTransaction(
        context: Context,
        snapshot: SleepCycleStore.Snapshot
    ) {
        updateCurrent(context, synchronous = true) { current ->
            current.copy(
                transactionCycleId = snapshot.cycleId,
                helperExpected = snapshot.helperExpected,
                helperSleepRequested = snapshot.helperSleepRequested,
                helperRestored = snapshot.helperRestored,
                wifiManaged = snapshot.wifiManaged,
                bluetoothManaged = snapshot.bluetoothManaged
            )
        }
    }

    @Synchronized
    fun markHelperSleepRequested(context: Context) {
        updateCurrent(context, synchronous = false) {
            it.copy(helperSleepRequested = true)
        }
    }

    @Synchronized
    fun markHelperRestored(context: Context) {
        updateCurrent(context, synchronous = false) {
            it.copy(helperRestored = true)
        }
    }

    @Synchronized
    fun recordConnectorOwnership(
        context: Context,
        connectorId: String,
        restoreTokenPresent: Boolean
    ) {
        val now = System.currentTimeMillis()
        updateCurrent(context, synchronous = true) { current ->
            val updated =
                current.connectors
                    .filterNot { it.connectorId == connectorId } +
                    ConnectorOwnership(
                        connectorId = connectorId,
                        takenAt = now,
                        clearedAt = 0L,
                        restoreTokenPresent = restoreTokenPresent
                    )
            current.copy(connectors = updated.sortedBy { it.connectorId })
        }
    }

    @Synchronized
    fun clearConnectorOwnership(
        context: Context,
        connectorId: String
    ) {
        val now = System.currentTimeMillis()
        updateCurrent(context, synchronous = true) { current ->
            current.copy(
                connectors =
                    current.connectors.map {
                        if (it.connectorId == connectorId && it.clearedAt <= 0L) {
                            it.copy(clearedAt = now)
                        } else {
                            it
                        }
                    }
            )
        }
    }

    @Synchronized
    fun markRestoreProblem(
        context: Context,
        message: String
    ) {
        updateCurrent(context, synchronous = true) {
            it.copy(restoreProblem = message)
        }
    }

    @Synchronized
    fun clearRestoreProblem(context: Context) {
        updateCurrent(context, synchronous = false) {
            it.copy(restoreProblem = null)
        }
    }

    @Synchronized
    fun recordFalseWake(context: Context) {
        updateCurrent(context, synchronous = false) {
            it.copy(falseWakeCount = safeIncrement(it.falseWakeCount))
        }
    }

    @Synchronized
    fun captureSystemSnapshot(
        context: Context,
        phase: String,
        includeDetailedProcessMemory: Boolean = false,
        includeLatestProcessExit: Boolean = false,
        trimMemoryLevel: Int? = null
    ) {
        if (!AppPreferences.advancedDiagnosticsEnabled(context)) return

        val snapshot =
            readSystemSnapshot(
                context = context,
                phase = phase,
                includeDetailedProcessMemory = includeDetailedProcessMemory,
                includeLatestProcessExit = includeLatestProcessExit,
                trimMemoryLevel = trimMemoryLevel
            )

        updateCurrent(context, synchronous = false) { current ->
            val existingSamePhase =
                phase == PHASE_SLEEP_START &&
                    current.systemSnapshots.any { it.phase == phase }
            if (existingSamePhase) {
                current
            } else {
                current.copy(
                    systemSnapshots =
                        appendBoundedSystemSnapshot(
                            current.systemSnapshots,
                            snapshot
                        )
                )
            }
        }
    }

    @Synchronized
    fun recordEvent(
        context: Context,
        message: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (message.isBlank()) return

        updateCurrent(context, synchronous = false) { current ->
            current.copy(
                events =
                    appendBoundedEvent(
                        current.events,
                        Event(
                            timestamp = timestamp,
                            message = message
                        )
                    )
            )
        }
    }

    @Synchronized
    fun markWake(
        context: Context,
        sleepSession: BatterySleepStore.SleepSession?,
        restorationPending: Boolean
    ) {
        val now = System.currentTimeMillis()
        updateCurrent(context, synchronous = true) { current ->
            current.copy(
                wakeAt = current.wakeAt.takeIf { it > 0L } ?: now,
                completedAt =
                    if (restorationPending) {
                        0L
                    } else {
                        current.completedAt.takeIf { it > 0L } ?: now
                    },
                status =
                    if (restorationPending) {
                        STATUS_AWAKE
                    } else {
                        STATUS_COMPLETE
                    },
                battery = sleepSession?.toBatterySummary() ?: current.battery,
                events =
                    appendBoundedEvent(
                        current.events,
                        Event(now, "Real wake → diagnostics sleep interval ended")
                    )
            )
        }
    }

    @Synchronized
    fun markRestorationComplete(
        context: Context,
        transactionCycleId: Long
    ) {
        if (transactionCycleId <= 0L) return

        val now = System.currentTimeMillis()
        updateCurrent(context, synchronous = true) { current ->
            if (
                current.transactionCycleId != transactionCycleId ||
                current.wakeAt <= 0L
            ) {
                current
            } else {
                current.copy(
                    completedAt =
                        current.completedAt.takeIf { it > 0L } ?: now,
                    status = STATUS_COMPLETE,
                    events =
                        appendBoundedEvent(
                            current.events,
                            Event(now, "Wake restoration transaction complete")
                        )
                )
            }
        }
    }

    fun diagnosticHistory(
        context: Context,
        limit: Int = DEFAULT_DIAGNOSTIC_CYCLES
    ): List<CycleRecord> {
        val p = prefs(context)
        return readIds(p)
            .asSequence()
            .mapNotNull { readRecord(p, it) }
            .sortedByDescending { it.startedAt }
            .take(limit.coerceIn(0, MAX_STORED_CYCLES))
            .toList()
    }

    fun storedCount(context: Context): Int =
        readIds(prefs(context)).size

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    private fun updateCurrent(
        context: Context,
        synchronous: Boolean,
        transform: (CycleRecord) -> CycleRecord
    ) {
        if (!AppPreferences.advancedDiagnosticsEnabled(context)) return

        val p = prefs(context)
        val currentId = p.getLong(KEY_CURRENT_SESSION_ID, 0L)
        if (currentId <= 0L) return

        val current = readRecord(p, currentId) ?: return
        val updated = transform(current)
        if (updated == current) return

        writeRecord(
            prefs = p,
            record = updated,
            synchronous = synchronous
        )
    }

    private fun writeRecord(
        prefs: SharedPreferences,
        record: CycleRecord,
        synchronous: Boolean
    ) {
        val editor =
            prefs.edit()
                .putString(cycleKey(record.sessionId), record.toJson().toString())
        if (synchronous) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    private fun readRecord(
        prefs: SharedPreferences,
        sessionId: Long
    ): CycleRecord? {
        if (sessionId <= 0L) return null
        val raw = prefs.getString(cycleKey(sessionId), null) ?: return null
        return runCatching {
            parseRecord(JSONObject(raw))
        }.getOrNull()
    }

    private fun readIds(prefs: SharedPreferences): List<Long> {
        val raw = prefs.getString(KEY_CYCLE_IDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val id = array.optLong(index, 0L)
                    if (id > 0L) add(id)
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun idsToJson(ids: List<Long>): String =
        JSONArray().apply {
            ids.forEach(::put)
        }.toString()

    private fun cycleKey(sessionId: Long) =
        "$CYCLE_KEY_PREFIX$sessionId"

    private fun readSystemSnapshot(
        context: Context,
        phase: String,
        includeDetailedProcessMemory: Boolean,
        includeLatestProcessExit: Boolean,
        trimMemoryLevel: Int?
    ): SystemSnapshot {
        val captureStartedNs = SystemClock.elapsedRealtimeNanos()
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo =
            activityManager?.let { manager ->
                runCatching {
                    ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
                }.getOrNull()
            }
        val processInfo =
            runCatching {
                ActivityManager.RunningAppProcessInfo().also(
                    ActivityManager::getMyMemoryState
                )
            }.getOrNull()
        val debugMemory =
            if (includeDetailedProcessMemory) {
                runCatching {
                    Debug.MemoryInfo().also(Debug::getMemoryInfo)
                }.getOrNull()
            } else {
                null
            }

        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)

        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val batteryIntent =
            runCatching {
                context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
            }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent =
            if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                null
            }

        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork =
            runCatching { connectivity?.activeNetwork }.getOrNull()
        val capabilities =
            runCatching {
                activeNetwork?.let { connectivity?.getNetworkCapabilities(it) }
            }.getOrNull()

        val latestExit =
            if (includeLatestProcessExit && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    activityManager
                        ?.getHistoricalProcessExitReasons(
                            context.packageName,
                            0,
                            1
                        )
                        ?.firstOrNull()
                        ?.let { info ->
                            ProcessExitSnapshot(
                                timestamp = info.timestamp,
                                reason = info.reason,
                                status = info.status,
                                importance = info.importance,
                                pssKb = info.pss,
                                rssKb = info.rss,
                                description =
                                    info.description
                                        ?.replace(Regex("\\s+"), " ")
                                        ?.trim()
                                        ?.take(240)
                                        ?.takeIf { it.isNotBlank() }
                            )
                        }
                }.getOrNull()
            } else {
                null
            }

        return SystemSnapshot(
            timestamp = System.currentTimeMillis(),
            phase = phase,
            totalRamBytes = memoryInfo?.totalMem,
            availableRamBytes = memoryInfo?.availMem,
            lowMemory = memoryInfo?.lowMemory,
            lowMemoryThresholdBytes = memoryInfo?.threshold,
            lowRamDevice = runCatching { activityManager?.isLowRamDevice }.getOrNull(),
            processPssKb = debugMemory?.totalPss,
            processPrivateDirtyKb = debugMemory?.totalPrivateDirty,
            processImportance = processInfo?.importance,
            heapUsedBytes = heapUsed,
            heapMaxBytes = runtime.maxMemory(),
            nativeHeapAllocatedBytes = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(0L),
            backgroundRestricted = runCatching { activityManager?.isBackgroundRestricted }.getOrNull(),
            interactive = runCatching { powerManager?.isInteractive }.getOrNull(),
            powerSaveMode = runCatching { powerManager?.isPowerSaveMode }.getOrNull(),
            deviceIdleMode = runCatching { powerManager?.isDeviceIdleMode }.getOrNull(),
            ignoringBatteryOptimizations =
                runCatching {
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName)
                }.getOrNull(),
            thermalStatus =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { powerManager?.currentThermalStatus }.getOrNull()
                } else {
                    null
                },
            batteryPercent = batteryPercent,
            batteryStatus =
                batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    ?.takeIf { it >= 0 },
            batteryHealth =
                batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                    ?.takeIf { it >= 0 },
            batteryTemperatureTenthsC =
                batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                    ?.takeIf { it != Int.MIN_VALUE },
            pluggedType =
                batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            activeNetwork = connectivity?.let { activeNetwork != null },
            networkValidated =
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            networkInternet =
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            networkWifi =
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            networkCellular =
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            uptimeMs = SystemClock.uptimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            processCpuTimeMs = Process.getElapsedCpuTime(),
            captureDurationMs =
                ((SystemClock.elapsedRealtimeNanos() - captureStartedNs) / 1_000_000L)
                    .coerceAtLeast(0L),
            trimMemoryLevel = trimMemoryLevel,
            latestProcessExit = latestExit
        )
    }

    private fun settingsSnapshot(context: Context) =
        SettingsSnapshot(
            wifi = AppPreferences.manageWifi(context),
            bluetooth = AppPreferences.manageBluetooth(context),
            batterySaver = AppPreferences.manageBatterySaver(context),
            chargingSeparation =
                AppPreferences.manageChargingSeparationWithLid(context),
            syncthing = AppPreferences.manageSyncthing(context),
            tailscale = AppPreferences.manageTailscale(context),
            jamesDsp = AppPreferences.manageJamesDsp(context),
            basicSync = AppPreferences.manageBasicSync(context),
            raOfflineProxy =
                AppPreferences.manageRaOfflineProxy(context),
            closedLidProtection =
                AppPreferences.manageClosedLidProtection(context)
        )

    private fun BatterySleepStore.SleepSession.toBatterySummary() =
        BatterySummary(
            startedAt = startedAt,
            endedAt = endedAt,
            startPercent = startPercent,
            endPercent = endPercent,
            drainPercent = drainPercent,
            durationMs = durationMs,
            chargedDuringSleep = chargedDuringSleep,
            drainMah = drainMah,
            deepSleepMs = deepSleepMs,
            preciseDrainPercent = preciseDrainPercent,
            falseWakeCount = falseWakeCount
        )

    private fun appendBoundedEvent(
        events: List<Event>,
        event: Event
    ): List<Event> =
        (events + event).takeLast(MAX_EVENTS_PER_CYCLE)

    private fun appendBoundedSystemSnapshot(
        snapshots: List<SystemSnapshot>,
        snapshot: SystemSnapshot
    ): List<SystemSnapshot> {
        val combined = snapshots + snapshot
        if (combined.size <= MAX_SYSTEM_SNAPSHOTS_PER_CYCLE) return combined
        return listOf(combined.first()) +
            combined.takeLast(MAX_SYSTEM_SNAPSHOTS_PER_CYCLE - 1)
    }

    private fun safeIncrement(value: Int): Int =
        if (value >= Int.MAX_VALUE) Int.MAX_VALUE else value.coerceAtLeast(0) + 1

    private fun CycleRecord.toJson(): JSONObject =
        JSONObject()
            .put("sessionId", sessionId)
            .put("startedAt", startedAt)
            .put("wakeAt", wakeAt)
            .put("completedAt", completedAt)
            .put("status", status)
            .put("settings", settings.toJson())
            .put("transactionCycleId", transactionCycleId)
            .put("helperExpected", helperExpected)
            .put("helperSleepRequested", helperSleepRequested)
            .put("helperRestored", helperRestored)
            .put("wifiManaged", wifiManaged)
            .put("bluetoothManaged", bluetoothManaged)
            .put("falseWakeCount", falseWakeCount.coerceAtLeast(0))
            .apply {
                restoreProblem?.let { put("restoreProblem", it) }
                battery?.let { put("battery", it.toJson()) }
            }
            .put(
                "connectors",
                JSONArray().apply {
                    connectors.forEach { put(it.toJson()) }
                }
            )
            .put(
                "systemSnapshots",
                JSONArray().apply {
                    systemSnapshots.forEach { put(it.toJson()) }
                }
            )
            .put(
                "events",
                JSONArray().apply {
                    events.forEach { put(it.toJson()) }
                }
            )

    private fun SettingsSnapshot.toJson() =
        JSONObject()
            .put("wifi", wifi)
            .put("bluetooth", bluetooth)
            .put("batterySaver", batterySaver)
            .put("chargingSeparation", chargingSeparation)
            .put("syncthing", syncthing)
            .put("tailscale", tailscale)
            .put("jamesDsp", jamesDsp)
            .put("basicSync", basicSync)
            .put("raOfflineProxy", raOfflineProxy)
            .put("closedLidProtection", closedLidProtection)

    private fun ConnectorOwnership.toJson() =
        JSONObject()
            .put("connectorId", connectorId)
            .put("takenAt", takenAt)
            .put("clearedAt", clearedAt)
            .put("restoreTokenPresent", restoreTokenPresent)

    private fun BatterySummary.toJson() =
        JSONObject()
            .put("startedAt", startedAt)
            .put("endedAt", endedAt)
            .put("startPercent", startPercent)
            .put("endPercent", endPercent)
            .put("drainPercent", drainPercent)
            .put("durationMs", durationMs)
            .put("chargedDuringSleep", chargedDuringSleep)
            .put("falseWakeCount", falseWakeCount.coerceAtLeast(0))
            .apply {
                drainMah?.let { put("drainMah", it) }
                deepSleepMs?.let { put("deepSleepMs", it) }
                preciseDrainPercent?.let { put("preciseDrainPercent", it) }
            }

    private fun ProcessExitSnapshot.toJson() =
        JSONObject()
            .put("timestamp", timestamp)
            .put("reason", reason)
            .put("status", status)
            .put("importance", importance)
            .put("pssKb", pssKb)
            .put("rssKb", rssKb)
            .apply { description?.let { put("description", it) } }

    private fun SystemSnapshot.toJson() =
        JSONObject()
            .put("timestamp", timestamp)
            .put("phase", phase)
            .put("heapUsedBytes", heapUsedBytes)
            .put("heapMaxBytes", heapMaxBytes)
            .put("nativeHeapAllocatedBytes", nativeHeapAllocatedBytes)
            .put("uptimeMs", uptimeMs)
            .put("elapsedRealtimeMs", elapsedRealtimeMs)
            .put("processCpuTimeMs", processCpuTimeMs)
            .put("captureDurationMs", captureDurationMs)
            .apply {
                totalRamBytes?.let { put("totalRamBytes", it) }
                availableRamBytes?.let { put("availableRamBytes", it) }
                lowMemory?.let { put("lowMemory", it) }
                lowMemoryThresholdBytes?.let { put("lowMemoryThresholdBytes", it) }
                lowRamDevice?.let { put("lowRamDevice", it) }
                processPssKb?.let { put("processPssKb", it) }
                processPrivateDirtyKb?.let { put("processPrivateDirtyKb", it) }
                processImportance?.let { put("processImportance", it) }
                backgroundRestricted?.let { put("backgroundRestricted", it) }
                interactive?.let { put("interactive", it) }
                powerSaveMode?.let { put("powerSaveMode", it) }
                deviceIdleMode?.let { put("deviceIdleMode", it) }
                ignoringBatteryOptimizations?.let { put("ignoringBatteryOptimizations", it) }
                thermalStatus?.let { put("thermalStatus", it) }
                batteryPercent?.let { put("batteryPercent", it) }
                batteryStatus?.let { put("batteryStatus", it) }
                batteryHealth?.let { put("batteryHealth", it) }
                batteryTemperatureTenthsC?.let { put("batteryTemperatureTenthsC", it) }
                pluggedType?.let { put("pluggedType", it) }
                activeNetwork?.let { put("activeNetwork", it) }
                networkValidated?.let { put("networkValidated", it) }
                networkInternet?.let { put("networkInternet", it) }
                networkWifi?.let { put("networkWifi", it) }
                networkCellular?.let { put("networkCellular", it) }
                trimMemoryLevel?.let { put("trimMemoryLevel", it) }
                latestProcessExit?.let { put("latestProcessExit", it.toJson()) }
            }

    private fun Event.toJson() =
        JSONObject()
            .put("timestamp", timestamp)
            .put("message", message)

    private fun parseRecord(item: JSONObject): CycleRecord? {
        val sessionId = item.optLong("sessionId", 0L)
        val startedAt = item.optLong("startedAt", 0L)
        if (sessionId <= 0L || startedAt <= 0L) return null

        val settingsJson = item.optJSONObject("settings")
        val settings =
            SettingsSnapshot(
                wifi = settingsJson?.optBoolean("wifi", false) ?: false,
                bluetooth = settingsJson?.optBoolean("bluetooth", false) ?: false,
                batterySaver = settingsJson?.optBoolean("batterySaver", false) ?: false,
                chargingSeparation =
                    settingsJson?.optBoolean("chargingSeparation", false)
                        ?: false,
                syncthing = settingsJson?.optBoolean("syncthing", false) ?: false,
                tailscale = settingsJson?.optBoolean("tailscale", false) ?: false,
                jamesDsp = settingsJson?.optBoolean("jamesDsp", false) ?: false,
                basicSync = settingsJson?.optBoolean("basicSync", false) ?: false,
                raOfflineProxy =
                    settingsJson?.optBoolean("raOfflineProxy", false)
                        ?: false,
                closedLidProtection =
                    settingsJson?.optBoolean("closedLidProtection", false)
                        ?: false
            )

        val connectors =
            buildList {
                val array = item.optJSONArray("connectors") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val connector = array.optJSONObject(index) ?: continue
                    val id = connector.optString("connectorId", "")
                    if (id.isBlank()) continue
                    add(
                        ConnectorOwnership(
                            connectorId = id,
                            takenAt = connector.optLong("takenAt", 0L),
                            clearedAt = connector.optLong("clearedAt", 0L),
                            restoreTokenPresent =
                                connector.optBoolean("restoreTokenPresent", false)
                        )
                    )
                }
            }

        val systemSnapshots =
            buildList {
                val array = item.optJSONArray("systemSnapshots") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val snapshot = array.optJSONObject(index) ?: continue
                    val timestamp = snapshot.optLong("timestamp", 0L)
                    val phase = snapshot.optString("phase", "")
                    if (timestamp <= 0L || phase.isBlank()) continue
                    val exit =
                        snapshot.optJSONObject("latestProcessExit")?.let { raw ->
                            ProcessExitSnapshot(
                                timestamp = raw.optLong("timestamp", 0L),
                                reason = raw.optInt("reason", 0),
                                status = raw.optInt("status", 0),
                                importance = raw.optInt("importance", 0),
                                pssKb = raw.optLong("pssKb", 0L),
                                rssKb = raw.optLong("rssKb", 0L),
                                description =
                                    raw.optString("description", "")
                                        .takeIf { it.isNotBlank() }
                            )
                        }
                    add(
                        SystemSnapshot(
                            timestamp = timestamp,
                            phase = phase,
                            totalRamBytes = snapshot.optLongOrNull("totalRamBytes"),
                            availableRamBytes = snapshot.optLongOrNull("availableRamBytes"),
                            lowMemory = snapshot.optBooleanOrNull("lowMemory"),
                            lowMemoryThresholdBytes = snapshot.optLongOrNull("lowMemoryThresholdBytes"),
                            lowRamDevice = snapshot.optBooleanOrNull("lowRamDevice"),
                            processPssKb = snapshot.optIntOrNull("processPssKb"),
                            processPrivateDirtyKb = snapshot.optIntOrNull("processPrivateDirtyKb"),
                            processImportance = snapshot.optIntOrNull("processImportance"),
                            heapUsedBytes = snapshot.optLong("heapUsedBytes", 0L),
                            heapMaxBytes = snapshot.optLong("heapMaxBytes", 0L),
                            nativeHeapAllocatedBytes = snapshot.optLong("nativeHeapAllocatedBytes", 0L),
                            backgroundRestricted = snapshot.optBooleanOrNull("backgroundRestricted"),
                            interactive = snapshot.optBooleanOrNull("interactive"),
                            powerSaveMode = snapshot.optBooleanOrNull("powerSaveMode"),
                            deviceIdleMode = snapshot.optBooleanOrNull("deviceIdleMode"),
                            ignoringBatteryOptimizations = snapshot.optBooleanOrNull("ignoringBatteryOptimizations"),
                            thermalStatus = snapshot.optIntOrNull("thermalStatus"),
                            batteryPercent = snapshot.optIntOrNull("batteryPercent"),
                            batteryStatus = snapshot.optIntOrNull("batteryStatus"),
                            batteryHealth = snapshot.optIntOrNull("batteryHealth"),
                            batteryTemperatureTenthsC = snapshot.optIntOrNull("batteryTemperatureTenthsC"),
                            pluggedType = snapshot.optIntOrNull("pluggedType"),
                            activeNetwork = snapshot.optBooleanOrNull("activeNetwork"),
                            networkValidated = snapshot.optBooleanOrNull("networkValidated"),
                            networkInternet = snapshot.optBooleanOrNull("networkInternet"),
                            networkWifi = snapshot.optBooleanOrNull("networkWifi"),
                            networkCellular = snapshot.optBooleanOrNull("networkCellular"),
                            uptimeMs = snapshot.optLong("uptimeMs", 0L),
                            elapsedRealtimeMs = snapshot.optLong("elapsedRealtimeMs", 0L),
                            processCpuTimeMs = snapshot.optLong("processCpuTimeMs", 0L),
                            captureDurationMs = snapshot.optLong("captureDurationMs", 0L),
                            trimMemoryLevel = snapshot.optIntOrNull("trimMemoryLevel"),
                            latestProcessExit = exit
                        )
                    )
                }
            }

        val events =
            buildList {
                val array = item.optJSONArray("events") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val event = array.optJSONObject(index) ?: continue
                    val timestamp = event.optLong("timestamp", 0L)
                    val message = event.optString("message", "")
                    if (timestamp > 0L && message.isNotBlank()) {
                        add(Event(timestamp, message))
                    }
                }
            }

        val battery =
            item.optJSONObject("battery")?.let {
                BatterySummary(
                    startedAt = it.optLong("startedAt", 0L),
                    endedAt = it.optLong("endedAt", 0L),
                    startPercent = it.optInt("startPercent", -1),
                    endPercent = it.optInt("endPercent", -1),
                    drainPercent = it.optInt("drainPercent", 0),
                    durationMs = it.optLong("durationMs", 0L),
                    chargedDuringSleep =
                        it.optBoolean("chargedDuringSleep", false),
                    drainMah =
                        if (it.has("drainMah")) it.optDouble("drainMah")
                        else null,
                    deepSleepMs =
                        if (it.has("deepSleepMs")) it.optLong("deepSleepMs")
                        else null,
                    preciseDrainPercent =
                        if (it.has("preciseDrainPercent")) {
                            it.optDouble("preciseDrainPercent")
                                .takeIf { value -> value.isFinite() }
                        } else {
                            null
                        },
                    falseWakeCount =
                        it.optInt("falseWakeCount", 0).coerceAtLeast(0)
                )
            }

        return CycleRecord(
            sessionId = sessionId,
            startedAt = startedAt,
            wakeAt = item.optLong("wakeAt", 0L),
            completedAt = item.optLong("completedAt", 0L),
            status = item.optString("status", STATUS_SLEEPING),
            settings = settings,
            transactionCycleId = item.optLong("transactionCycleId", 0L),
            helperExpected = item.optBoolean("helperExpected", false),
            helperSleepRequested =
                item.optBoolean("helperSleepRequested", false),
            helperRestored = item.optBoolean("helperRestored", true),
            wifiManaged = item.optBoolean("wifiManaged", false),
            bluetoothManaged = item.optBoolean("bluetoothManaged", false),
            falseWakeCount =
                item.optInt("falseWakeCount", 0).coerceAtLeast(0),
            restoreProblem =
                item.optString("restoreProblem", "")
                    .takeIf { it.isNotBlank() },
            connectors = connectors,
            battery = battery,
            systemSnapshots = systemSnapshots,
            events = events
        )
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null
}
