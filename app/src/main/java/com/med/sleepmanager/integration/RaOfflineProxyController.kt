package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyCommandResult
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyQueueState
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyQueueStatus
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyStatus
import org.json.JSONObject

/**
 * Thin Android adapter around RAOfflineProxy's documented Automation API v1.
 *
 * ContentResolver.call() can block. status/start/stop must therefore be invoked
 * from a worker thread by service orchestration; UI callers should only use the
 * cheap install/provider/permission/battery prerequisite helpers.
 */
object RaOfflineProxyController {
    private const val TAG = "RAOfflineProxyControl"

    const val PACKAGE = "com.raofflineproxy"
    const val AUTHORITY = "com.raofflineproxy.config"
    const val CONTROL_PERMISSION =
        "com.raofflineproxy.permission.CONTROL_PROXY"
    const val SUPPORTED_API_VERSION = 1

    private const val METHOD_STATUS = "status"
    private const val METHOD_START = "start"
    private const val METHOD_STOP = "stop"
    private const val EXTRA_RESULT = "result"
    private const val EXTRA_STATUS = "status"

    val URI: Uri = Uri.parse("content://$AUTHORITY")

    @Volatile
    private var cachedStatus: RaOfflineProxyStatus? = null

    fun lastObservedStatus(): RaOfflineProxyStatus? = cachedStatus

    fun isInstalled(context: Context): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }.isSuccess

    fun versionName(context: Context): String? =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0).versionName
        }.getOrNull()

    fun open(context: Context): Boolean {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun openAppSettings(context: Context): Boolean {
        val intent =
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + PACKAGE)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun providerAvailable(context: Context): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.resolveContentProvider(AUTHORITY, 0) != null
        }.getOrDefault(false)

    fun hasControlPermission(context: Context): Boolean =
        context.checkSelfPermission(CONTROL_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun isBatteryUnrestricted(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false
        return powerManager.isIgnoringBatteryOptimizations(PACKAGE)
    }

    fun status(context: Context): RaOfflineProxyStatus? =
        runCatching {
            val bundle =
                context.contentResolver.call(
                    URI,
                    METHOD_STATUS,
                    null,
                    null
                )
            parseStatus(bundle?.getString(EXTRA_STATUS))
        }.onFailure {
            Log.w(TAG, "status call failed", it)
        }.getOrNull()?.also {
            cachedStatus = it
        }

    fun start(context: Context): RaOfflineProxyCommandResult =
        command(context, METHOD_START)

    fun stop(context: Context): RaOfflineProxyCommandResult =
        command(context, METHOD_STOP)

    fun registerStatusObserver(
        context: Context,
        handler: Handler = Handler(Looper.getMainLooper()),
        onChanged: () -> Unit
    ): ContentObserver {
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    onChanged()
                }

                override fun onChange(
                    selfChange: Boolean,
                    uri: Uri?
                ) {
                    onChanged()
                }
            }

        context.contentResolver.registerContentObserver(
            URI,
            false,
            observer
        )
        return observer
    }

    fun unregisterStatusObserver(
        context: Context,
        observer: ContentObserver?
    ) {
        if (observer == null) return
        runCatching {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    internal fun parseStatus(raw: String?): RaOfflineProxyStatus? {
        if (raw.isNullOrBlank()) return null

        return runCatching {
            val json = JSONObject(raw)
            val queue = json.optJSONObject("queue") ?: JSONObject()
            val nextWindowAt =
                if (queue.isNull("nextWindowAt")) {
                    null
                } else {
                    queue.optLong("nextWindowAt")
                }

            RaOfflineProxyStatus(
                version = json.optInt("version", -1),
                running = json.optBoolean("running", false),
                shouldBeRunning =
                    json.optBoolean("shouldBeRunning", false),
                online = json.optBoolean("online", false),
                queue =
                    RaOfflineProxyQueueStatus(
                        count = queue.optInt("count", 0),
                        state =
                            RaOfflineProxyQueueState.fromWire(
                                queue.optString("state", null)
                            ),
                        nextWindowAt = nextWindowAt
                    )
            )
        }.onFailure {
            Log.w(TAG, "Invalid RAOfflineProxy status payload", it)
        }.getOrNull()
    }

    private fun command(
        context: Context,
        method: String
    ): RaOfflineProxyCommandResult =
        try {
            val bundle =
                context.contentResolver.call(
                    URI,
                    method,
                    null,
                    null
                )
            val status = parseStatus(bundle?.getString(EXTRA_STATUS))
            if (status != null) {
                cachedStatus = status
            }
            RaOfflineProxyCommandResult(
                code = bundle?.getString(EXTRA_RESULT),
                status = status
            )
        } catch (security: SecurityException) {
            Log.w(TAG, "$method denied by RAOfflineProxy", security)
            RaOfflineProxyCommandResult(
                code =
                    RaOfflineProxyCommandResult
                        .INTERNAL_SECURITY_EXCEPTION,
                status = cachedStatus,
                detail = security.message
            )
        } catch (error: Exception) {
            Log.w(TAG, "$method call failed", error)
            RaOfflineProxyCommandResult(
                code = RaOfflineProxyCommandResult.INTERNAL_CALL_FAILED,
                status = cachedStatus,
                detail = error.message
            )
        }
}
