package com.med.sleepmanager.data

import android.content.Context

object AppPreferences {
    private const val PREFS = "sleep_manager"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WIFI = "wifi"
    private const val KEY_BLUETOOTH = "bluetooth"
    private const val KEY_BATTERY_SAVER_ACTION = "battery_saver_action"
    private const val KEY_CHARGING_SEPARATION_LID = "charging_separation_lid"
    private const val KEY_SYNCTHING = "syncthing"
    private const val KEY_TAILSCALE = "tailscale"
    private const val KEY_JAMES_DSP = "james_dsp"
    private const val KEY_BASICSYNC = "basicsync"
    private const val KEY_PERIODIC_SYNC_WHILE_SLEEPING = "periodic_sync_while_sleeping"
    private const val KEY_SYNC_THEN_STOP_ON_SLEEP_WAKE = "sync_then_stop_on_sleep_wake"
    private const val KEY_THOR_PROTECTION = "thor_protection"
    private const val KEY_THOR_DOCK_DISCONNECT_SLEEP = "thor_dock_disconnect_sleep"
    private const val KEY_THOR_CLOSED_POWER_SLEEP = "thor_closed_power_sleep"
    private const val KEY_THOR_LID_CLOSED_LAST_KNOWN = "thor_lid_closed_last_known"
    private const val KEY_THOR_LID_STATE_KNOWN = "thor_lid_state_known"
    private const val KEY_SLEEP_GRACE_MS = "sleep_grace_ms"
    private const val KEY_CUSTOM_DELAY_ENABLED = "custom_delay_enabled"
    private const val KEY_CUSTOM_DELAY_MS = "custom_delay_ms"
    private const val KEY_BATTERY_CONDITION_ENABLED = "battery_condition_enabled"
    private const val KEY_BATTERY_BELOW_PERCENT = "battery_below_percent"
    private const val KEY_NOT_CHARGING_ONLY = "not_charging_only"
    private const val KEY_BATTERY_SAVER_MODE = "battery_saver_mode"
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_SCHEDULE_START_MINUTES = "schedule_start_minutes"
    private const val KEY_SCHEDULE_END_MINUTES = "schedule_end_minutes"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
    private const val KEY_SELECTED_SYNCTHING = "selected_syncthing"
    private const val KEY_LAST_EVENT = "last_event"
    private const val KEY_LAST_EVENT_TIME = "last_event_time"
    private const val KEY_AUTOMATIC_UPDATE_CHECKS = "automatic_update_checks"
    private const val KEY_USE_SYSTEM_COLORS = "use_system_colors"
    private const val KEY_LAST_UPDATE_CHECK_ATTEMPT = "last_update_check_attempt"
    private const val KEY_LAST_UPDATE_CHECK_SUCCESS = "last_update_check_success"
    private const val KEY_LATEST_RELEASE_VERSION = "latest_release_version"
    private const val KEY_LATEST_RELEASE_VERSION_CODE = "latest_release_version_code"
    private const val KEY_LATEST_RELEASE_URL = "latest_release_url"
    private const val KEY_LATEST_RELEASE_APK_URL = "latest_release_apk_url"
    private const val KEY_LATEST_RELEASE_SHA256 = "latest_release_sha256"
    private const val KEY_LATEST_HELPER_VERSION = "latest_helper_version"
    private const val KEY_LATEST_HELPER_VERSION_CODE = "latest_helper_version_code"
    private const val KEY_LATEST_HELPER_APK_URL = "latest_helper_apk_url"
    private const val KEY_LATEST_HELPER_SHA256 = "latest_helper_sha256"
    private const val KEY_LAST_NOTIFIED_UPDATE_VERSION = "last_notified_update_version"
    private const val KEY_LAST_NOTIFIED_HELPER_UPDATE_VERSION =
        "last_notified_helper_update_version"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_KNOWN = "last_wifi_diagnostic_known"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_PHASE = "last_wifi_diagnostic_phase"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_ACTION = "last_wifi_diagnostic_action"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_ATTEMPTED = "last_wifi_diagnostic_attempted"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_SUCCESS = "last_wifi_diagnostic_success"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_AIRPLANE = "last_wifi_diagnostic_airplane"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_TIME = "last_wifi_diagnostic_time"

    data class WifiToggleDiagnostic(
        val phase: String,
        val action: String,
        val attempted: Boolean,
        val success: Boolean,
        val airplaneMode: Boolean,
        val timestamp: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun manageWifi(context: Context) = prefs(context).getBoolean(KEY_WIFI, false)
    fun setManageWifi(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_WIFI, value).apply()

    fun manageBluetooth(context: Context) = prefs(context).getBoolean(KEY_BLUETOOTH, false)
    fun setManageBluetooth(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BLUETOOTH, value).apply()

    fun manageBatterySaver(context: Context) =
        prefs(context).getBoolean(KEY_BATTERY_SAVER_ACTION, false)

    fun setManageBatterySaver(context: Context, value: Boolean) {
        val editor = prefs(context).edit()
            .putBoolean(KEY_BATTERY_SAVER_ACTION, value)
        if (value) {
            editor.putString(KEY_BATTERY_SAVER_MODE, BATTERY_SAVER_IGNORE)
        }
        editor.apply()
    }

    fun manageChargingSeparationWithLid(context: Context) =
        prefs(context).getBoolean(KEY_CHARGING_SEPARATION_LID, false)

    fun setManageChargingSeparationWithLid(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_CHARGING_SEPARATION_LID, value).apply()

    fun manageSyncthing(context: Context) = prefs(context).getBoolean(KEY_SYNCTHING, false)
    fun setManageSyncthing(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SYNCTHING, value).apply()

    fun manageTailscale(context: Context) =
        prefs(context).getBoolean(KEY_TAILSCALE, false)

    fun setManageTailscale(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_TAILSCALE, value).apply()

    fun manageJamesDsp(context: Context) =
        prefs(context).getBoolean(KEY_JAMES_DSP, false)

    fun setManageJamesDsp(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_JAMES_DSP, value).apply()

    fun manageBasicSync(context: Context) =
        prefs(context).getBoolean(KEY_BASICSYNC, false)

    fun setManageBasicSync(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BASICSYNC, value).apply()

    fun periodicSyncWhileSleeping(context: Context) =
        prefs(context).getBoolean(KEY_PERIODIC_SYNC_WHILE_SLEEPING, false)

    fun setPeriodicSyncWhileSleeping(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_PERIODIC_SYNC_WHILE_SLEEPING, value).apply()

    fun syncThenStopOnSleepWake(context: Context) =
        prefs(context).getBoolean(KEY_SYNC_THEN_STOP_ON_SLEEP_WAKE, false)

    fun setSyncThenStopOnSleepWake(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SYNC_THEN_STOP_ON_SLEEP_WAKE, value).apply()

    fun manageThorProtection(context: Context) =
        prefs(context).getBoolean(KEY_THOR_PROTECTION, false)

    fun setManageThorProtection(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_THOR_PROTECTION, value).apply()

    fun thorDockDisconnectSleeps(context: Context) =
        prefs(context).getBoolean(KEY_THOR_DOCK_DISCONNECT_SLEEP, false)

    fun setThorDockDisconnectSleeps(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_THOR_DOCK_DISCONNECT_SLEEP, value).apply()

    fun thorClosedPowerSleeps(context: Context) =
        prefs(context).getBoolean(KEY_THOR_CLOSED_POWER_SLEEP, false)

    fun setThorClosedPowerSleeps(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_THOR_CLOSED_POWER_SLEEP, value).apply()

    fun lastKnownThorLidClosed(context: Context): Boolean? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_THOR_LID_STATE_KNOWN, false)) return null
        return p.getBoolean(KEY_THOR_LID_CLOSED_LAST_KNOWN, false)
    }

    fun setLastKnownThorLidClosed(context: Context, closed: Boolean) =
        prefs(context).edit()
            .putBoolean(KEY_THOR_LID_CLOSED_LAST_KNOWN, closed)
            .putBoolean(KEY_THOR_LID_STATE_KNOWN, true)
            .commit()

    fun sleepGraceMs(context: Context): Long {
        val value = prefs(context).getLong(KEY_SLEEP_GRACE_MS, 0L)
        return if (value in setOf(0L, 3000L, 5000L, 10000L)) value else 0L
    }

    fun setSleepGraceMs(context: Context, value: Long) {
        val safeValue = if (value in setOf(0L, 3000L, 5000L, 10000L)) value else 0L
        prefs(context).edit().putLong(KEY_SLEEP_GRACE_MS, safeValue).apply()
    }

    fun customDelayEnabled(context: Context) =
        prefs(context).getBoolean(KEY_CUSTOM_DELAY_ENABLED, false)

    fun setCustomDelayEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_CUSTOM_DELAY_ENABLED, value).apply()

    fun customDelayMs(context: Context): Long {
        val value = prefs(context).getLong(KEY_CUSTOM_DELAY_MS, 60_000L)
        return if (value in CUSTOM_DELAY_VALUES) value else 60_000L
    }

    fun setCustomDelayMs(context: Context, value: Long) {
        val safeValue = if (value in CUSTOM_DELAY_VALUES) value else 60_000L
        prefs(context).edit().putLong(KEY_CUSTOM_DELAY_MS, safeValue).apply()
    }

    fun effectiveSleepDelayMs(context: Context): Long =
        if (customDelayEnabled(context)) customDelayMs(context) else sleepGraceMs(context)

    fun batteryConditionEnabled(context: Context) =
        prefs(context).getBoolean(KEY_BATTERY_CONDITION_ENABLED, false)

    fun setBatteryConditionEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BATTERY_CONDITION_ENABLED, value).apply()

    fun batteryBelowPercent(context: Context): Int =
        prefs(context).getInt(KEY_BATTERY_BELOW_PERCENT, 30).coerceIn(5, 95)

    fun setBatteryBelowPercent(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_BATTERY_BELOW_PERCENT, value.coerceIn(5, 95)).apply()

    fun notChargingOnly(context: Context) =
        prefs(context).getBoolean(KEY_NOT_CHARGING_ONLY, false)

    fun setNotChargingOnly(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_NOT_CHARGING_ONLY, value).apply()

    fun batterySaverMode(context: Context): String {
        val value = prefs(context).getString(KEY_BATTERY_SAVER_MODE, BATTERY_SAVER_IGNORE)
        return if (value in BATTERY_SAVER_MODES) value!! else BATTERY_SAVER_IGNORE
    }

    fun setBatterySaverMode(context: Context, value: String) {
        val safeValue = if (value in BATTERY_SAVER_MODES) value else BATTERY_SAVER_IGNORE
        val editor =
            prefs(context).edit()
                .putString(KEY_BATTERY_SAVER_MODE, safeValue)
        if (safeValue != BATTERY_SAVER_IGNORE) {
            editor.putBoolean(KEY_BATTERY_SAVER_ACTION, false)
        }
        editor.apply()
    }

    fun scheduleEnabled(context: Context) =
        prefs(context).getBoolean(KEY_SCHEDULE_ENABLED, false)

    fun setScheduleEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    fun scheduleStartMinutes(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_START_MINUTES, 23 * 60).coerceIn(0, 1439)

    fun setScheduleStartMinutes(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_SCHEDULE_START_MINUTES, value.coerceIn(0, 1439)).apply()

    fun scheduleEndMinutes(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_END_MINUTES, 7 * 60).coerceIn(0, 1439)

    fun setScheduleEndMinutes(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_SCHEDULE_END_MINUTES, value.coerceIn(0, 1439)).apply()

    fun hasAdvancedConditions(context: Context): Boolean =
        batteryConditionEnabled(context) ||
            notChargingOnly(context) ||
            batterySaverMode(context) != BATTERY_SAVER_IGNORE ||
            scheduleEnabled(context)

    const val BATTERY_SAVER_IGNORE = "ignore"
    const val BATTERY_SAVER_ON = "on"
    const val BATTERY_SAVER_OFF = "off"

    private val CUSTOM_DELAY_VALUES =
        setOf(60_000L, 300_000L, 600_000L, 1_800_000L)
    private val BATTERY_SAVER_MODES =
        setOf(BATTERY_SAVER_IGNORE, BATTERY_SAVER_ON, BATTERY_SAVER_OFF)

    fun isSetupComplete(context: Context) =
        prefs(context).getBoolean(KEY_SETUP_COMPLETE, false)

    fun setSetupComplete(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    fun getSelectedSyncthing(context: Context): String? =
        prefs(context).getString(KEY_SELECTED_SYNCTHING, null)

    fun setSelectedSyncthing(context: Context, packageName: String?) {
        val editor = prefs(context).edit()
        if (packageName.isNullOrBlank()) editor.remove(KEY_SELECTED_SYNCTHING)
        else editor.putString(KEY_SELECTED_SYNCTHING, packageName)
        editor.apply()
    }

    fun recordEvent(context: Context, event: String) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putString(KEY_LAST_EVENT, event)
            .putLong(KEY_LAST_EVENT_TIME, now)
            .apply()
        EventHistoryStore.record(context, event, now)
    }

    fun lastEvent(context: Context): String =
        prefs(context).getString(KEY_LAST_EVENT, "No activity yet") ?: "No activity yet"

    fun lastEventTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_EVENT_TIME, 0L)

    fun automaticUpdateChecks(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, true)

    fun setAutomaticUpdateChecks(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, value).apply()

    fun useSystemColors(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_SYSTEM_COLORS, false)

    fun setUseSystemColors(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_USE_SYSTEM_COLORS, value).apply()

    fun lastUpdateCheckAttempt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_UPDATE_CHECK_ATTEMPT, 0L)

    fun setLastUpdateCheckAttempt(context: Context, value: Long) =
        prefs(context).edit().putLong(KEY_LAST_UPDATE_CHECK_ATTEMPT, value).apply()

    fun lastUpdateCheckSuccess(context: Context): Long =
        prefs(context).getLong(KEY_LAST_UPDATE_CHECK_SUCCESS, 0L)

    fun setLastUpdateCheckSuccess(context: Context, value: Long) =
        prefs(context).edit().putLong(KEY_LAST_UPDATE_CHECK_SUCCESS, value).apply()

    fun latestReleaseVersion(context: Context): String? =
        prefs(context).getString(KEY_LATEST_RELEASE_VERSION, null)

    fun latestReleaseVersionCode(context: Context): Long? =
        prefs(context)
            .getLong(KEY_LATEST_RELEASE_VERSION_CODE, -1L)
            .takeIf { it >= 0L }

    fun latestReleaseUrl(context: Context): String? =
        prefs(context).getString(KEY_LATEST_RELEASE_URL, null)

    fun latestReleaseApkUrl(context: Context): String? =
        prefs(context).getString(KEY_LATEST_RELEASE_APK_URL, null)

    fun latestReleaseSha256(context: Context): String? =
        prefs(context).getString(KEY_LATEST_RELEASE_SHA256, null)

    fun latestHelperVersion(context: Context): String? =
        prefs(context).getString(KEY_LATEST_HELPER_VERSION, null)

    fun latestHelperVersionCode(context: Context): Long? =
        prefs(context)
            .getLong(KEY_LATEST_HELPER_VERSION_CODE, -1L)
            .takeIf { it >= 0L }

    fun latestHelperApkUrl(context: Context): String? =
        prefs(context).getString(KEY_LATEST_HELPER_APK_URL, null)

    fun latestHelperSha256(context: Context): String? =
        prefs(context).getString(KEY_LATEST_HELPER_SHA256, null)

    fun setLatestRelease(
        context: Context,
        version: String,
        versionCode: Long?,
        url: String,
        apkUrl: String?,
        sha256: String?,
        helperVersion: String?,
        helperVersionCode: Long?,
        helperApkUrl: String?,
        helperSha256: String?
    ) =
        prefs(context).edit()
            .putString(KEY_LATEST_RELEASE_VERSION, version)
            .apply {
                if (versionCode != null) {
                    putLong(KEY_LATEST_RELEASE_VERSION_CODE, versionCode)
                } else {
                    remove(KEY_LATEST_RELEASE_VERSION_CODE)
                }
                if (apkUrl != null) {
                    putString(KEY_LATEST_RELEASE_APK_URL, apkUrl)
                } else {
                    remove(KEY_LATEST_RELEASE_APK_URL)
                }
                if (sha256 != null) {
                    putString(KEY_LATEST_RELEASE_SHA256, sha256)
                } else {
                    remove(KEY_LATEST_RELEASE_SHA256)
                }
                if (helperVersion != null) {
                    putString(KEY_LATEST_HELPER_VERSION, helperVersion)
                } else {
                    remove(KEY_LATEST_HELPER_VERSION)
                }
                if (helperVersionCode != null) {
                    putLong(KEY_LATEST_HELPER_VERSION_CODE, helperVersionCode)
                } else {
                    remove(KEY_LATEST_HELPER_VERSION_CODE)
                }
                if (helperApkUrl != null) {
                    putString(KEY_LATEST_HELPER_APK_URL, helperApkUrl)
                } else {
                    remove(KEY_LATEST_HELPER_APK_URL)
                }
                if (helperSha256 != null) {
                    putString(KEY_LATEST_HELPER_SHA256, helperSha256)
                } else {
                    remove(KEY_LATEST_HELPER_SHA256)
                }
            }
            .putString(KEY_LATEST_RELEASE_URL, url)
            .apply()

    fun lastNotifiedUpdateVersion(context: Context): String? =
        prefs(context).getString(KEY_LAST_NOTIFIED_UPDATE_VERSION, null)

    fun setLastNotifiedUpdateVersion(context: Context, version: String) =
        prefs(context).edit().putString(KEY_LAST_NOTIFIED_UPDATE_VERSION, version).apply()

    fun lastNotifiedHelperUpdateVersion(context: Context): String? =
        prefs(context).getString(KEY_LAST_NOTIFIED_HELPER_UPDATE_VERSION, null)

    fun setLastNotifiedHelperUpdateVersion(context: Context, version: String) =
        prefs(context).edit()
            .putString(KEY_LAST_NOTIFIED_HELPER_UPDATE_VERSION, version)
            .apply()

    fun recordWifiToggleDiagnostic(
        context: Context,
        phase: String,
        action: String,
        attempted: Boolean,
        success: Boolean,
        airplaneMode: Boolean
    ) {
        prefs(context).edit()
            .putBoolean(KEY_LAST_WIFI_DIAGNOSTIC_KNOWN, true)
            .putString(KEY_LAST_WIFI_DIAGNOSTIC_PHASE, phase)
            .putString(KEY_LAST_WIFI_DIAGNOSTIC_ACTION, action)
            .putBoolean(KEY_LAST_WIFI_DIAGNOSTIC_ATTEMPTED, attempted)
            .putBoolean(KEY_LAST_WIFI_DIAGNOSTIC_SUCCESS, success)
            .putBoolean(KEY_LAST_WIFI_DIAGNOSTIC_AIRPLANE, airplaneMode)
            .putLong(KEY_LAST_WIFI_DIAGNOSTIC_TIME, System.currentTimeMillis())
            .apply()
    }

    fun lastWifiToggleDiagnostic(context: Context): WifiToggleDiagnostic? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_LAST_WIFI_DIAGNOSTIC_KNOWN, false)) return null

        return WifiToggleDiagnostic(
            phase = p.getString(KEY_LAST_WIFI_DIAGNOSTIC_PHASE, "unknown") ?: "unknown",
            action = p.getString(KEY_LAST_WIFI_DIAGNOSTIC_ACTION, "unknown") ?: "unknown",
            attempted = p.getBoolean(KEY_LAST_WIFI_DIAGNOSTIC_ATTEMPTED, false),
            success = p.getBoolean(KEY_LAST_WIFI_DIAGNOSTIC_SUCCESS, false),
            airplaneMode = p.getBoolean(KEY_LAST_WIFI_DIAGNOSTIC_AIRPLANE, false),
            timestamp = p.getLong(KEY_LAST_WIFI_DIAGNOSTIC_TIME, 0L)
        )
    }
}
