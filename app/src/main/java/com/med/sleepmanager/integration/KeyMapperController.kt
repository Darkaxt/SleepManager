package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Key Mapper 4.7+ sends paused/active state whenever it changes, its process
 * starts or the accessibility service starts (keymapperorg/KeyMapper#2279).
 *
 * The last observation is valid only for the Android boot it was received in.
 * If SleepManager missed an initial broadcast, state remains UNKNOWN and no
 * pause command is sent. No polling or wakelock is used.
 */
internal object KeyMapperController {
    const val PACKAGE = "io.github.sds100.keymapper"
    const val CONNECTOR_ID = "keymapper"
    const val TOKEN_RESUME = "resume"
    const val ACTION_STATE_CHANGED =
        "io.github.sds100.keymapper.ACTION_MAPPINGS_STATE_CHANGED"
    const val EXTRA_PAUSED =
        "io.github.sds100.keymapper.EXTRA_MAPPINGS_PAUSED"

    private const val ACTION_PAUSE = "io.github.sds100.keymapper.ACTION_PAUSE_MAPPINGS"
    private const val ACTION_RESUME = "io.github.sds100.keymapper.ACTION_RESUME_MAPPINGS"
    private const val RECEIVER_CLASS =
        "io.github.sds100.keymapper.api.PauseMappingsBroadcastReceiver"
    private const val PREFS = "key_mapper_state"
    private const val KEY_PAUSED = "paused"
    private const val KEY_BOOT = "boot_count"
    private const val KEY_SEEN = "observed"

    enum class PauseOutcome { SENT, ALREADY_PAUSED, UNKNOWN, FAILED }

    private fun packageInfo(context: Context) =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }.getOrNull()

    fun isInstalled(context: Context) = packageInfo(context) != null
    fun versionName(context: Context): String? = packageInfo(context)?.versionName

    fun isCompatible(context: Context): Boolean =
        KeyMapperPolicy.supportsStateApi(versionName(context))

    fun open(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    private fun bootCount(context: Context): Int =
        runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)

    fun observeState(context: Context, paused: Boolean) {
        if (!isCompatible(context)) return
        val boot = bootCount(context)
        if (boot < 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BOOT, boot)
            .putBoolean(KEY_SEEN, true)
            .putBoolean(KEY_PAUSED, paused)
            .apply()
    }

    fun currentState(context: Context): KeyMapperMappingsState {
        if (!isCompatible(context)) return KeyMapperMappingsState.UNKNOWN
        val boot = bootCount(context)
        if (boot < 0) return KeyMapperMappingsState.UNKNOWN
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SEEN, false) ||
            prefs.getInt(KEY_BOOT, -1) != boot
        ) {
            return KeyMapperMappingsState.UNKNOWN
        }
        return if (prefs.getBoolean(KEY_PAUSED, false)) {
            KeyMapperMappingsState.PAUSED
        } else {
            KeyMapperMappingsState.ACTIVE
        }
    }

    fun pauseIfActive(context: Context): PauseOutcome {
        if (!isCompatible(context)) return PauseOutcome.UNKNOWN
        return when (currentState(context)) {
            KeyMapperMappingsState.UNKNOWN -> PauseOutcome.UNKNOWN
            KeyMapperMappingsState.PAUSED -> PauseOutcome.ALREADY_PAUSED
            KeyMapperMappingsState.ACTIVE ->
                if (send(context, ACTION_PAUSE)) PauseOutcome.SENT
                else PauseOutcome.FAILED
        }
    }

    /** Only called when SleepManager owns a previous pause request. */
    fun resumeIfOwned(context: Context, token: String?): Boolean {
        if (token != TOKEN_RESUME) return false
        if (!isInstalled(context)) return true
        val state = currentState(context)
        if (!KeyMapperPolicy.shouldResume(owned = true, state = state)) return true
        return send(context, ACTION_RESUME)
    }

    private fun send(context: Context, action: String): Boolean =
        runCatching {
            context.sendBroadcast(
                Intent(action)
                    .setClassName(PACKAGE, RECEIVER_CLASS)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            )
            true
        }.onFailure {
            Log.w("SleepManager", "Unable to send Key Mapper action $action", it)
        }.getOrDefault(false)
}
