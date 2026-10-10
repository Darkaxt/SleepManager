package com.med.sleepmanager.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.Parcel
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.nio.charset.Charset

object DeviceControlController {
    private const val TAG = "SleepManagerDeviceControl"
    private const val PSERVER_SERVICE = "PServerBinder"
    private const val CHARGING_SEPARATION_KEY = "is_charging_separation"

    @Volatile
    private var directRadioControlCached: Boolean? = null

    data class ControlCapabilities(
        val pServerAvailable: Boolean,
        val batterySaverControl: Boolean,
        val chargingSeparationControl: Boolean,
        val pServerRadioControl: Boolean = false
    )

    fun capabilities(context: Context): ControlCapabilities {
        val pServer = findPServerBinder() != null
        val privilegedBatterySaverRead =
            if (pServer) {
                executePrivileged("settings get global low_power")
                    .getOrNull()
                    ?.trim()
                    ?.takeIf { it == "0" || it == "1" }
            } else {
                null
            }
        val privilegedChargingRead =
            if (pServer) {
                executePrivileged(
                    "settings get system $CHARGING_SEPARATION_KEY"
                )
                    .getOrNull()
                    ?.trim()
                    ?.takeIf { it == "0" || it == "1" }
            } else {
                null
            }

        return ControlCapabilities(
            pServerAvailable = pServer,
            batterySaverControl = privilegedBatterySaverRead != null,
            chargingSeparationControl =
                privilegedChargingRead != null &&
                    chargingSeparationState(context) != null,
            pServerRadioControl = supportsDirectRadioControl()
        )
    }

    fun isPServerAvailable(): Boolean = findPServerBinder() != null

    /**
     * Harmless capability probe for the OEM PServer backend. Brand/model names
     * are intentionally not used: if the binder can execute as uid 0, the
     * documented radio commands validated by SleepManager are available.
     */
    fun supportsDirectRadioControl(): Boolean {
        directRadioControlCached?.let { return it }

        val ready =
            executePrivileged("id -u")
                .getOrNull()
                ?.lineSequence()
                ?.map(String::trim)
                ?.any { it == "0" } == true

        directRadioControlCached = ready
        return ready
    }

    internal fun wifiEnabledPrivileged(): Boolean? =
        executePrivileged("cmd wifi status")
            .getOrNull()
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstNotNullOfOrNull { line ->
                when {
                    line.equals("Wifi is enabled", ignoreCase = true) -> true
                    line.equals("Wifi is disabled", ignoreCase = true) -> false
                    else -> null
                }
            }

    internal fun bluetoothEnabledPrivileged(): Boolean? =
        executePrivileged("settings get global bluetooth_on")
            .getOrNull()
            ?.let(::parseBooleanSetting)

    internal fun setWifiEnabledPrivileged(enabled: Boolean): Boolean =
        executePrivileged(
            "cmd wifi set-wifi-enabled " +
                if (enabled) "enabled" else "disabled"
        ).isSuccess

    internal fun setBluetoothEnabledPrivileged(enabled: Boolean): Boolean =
        executePrivileged(
            "cmd bluetooth_manager " +
                if (enabled) "enable" else "disable"
        ).isSuccess

    fun supportsBatterySaverControl(context: Context): Boolean =
        capabilities(context).batterySaverControl

    fun batterySaverEnabled(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }

    fun externalPowerConnected(context: Context): Boolean {
        val batteryIntent =
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
        val plugged =
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }

    fun setBatterySaverEnabled(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        val commandResult = executePrivileged("cmd power set-mode $value")
        if (commandResult.isFailure) {
            Log.e(
                TAG,
                "Battery Saver privileged write failed enabled=$enabled",
                commandResult.exceptionOrNull()
            )
            return false
        }

        val actual = readPrivilegedBoolean("settings get global low_power")
        if (actual != enabled) {
            Log.w(
                TAG,
                "Battery Saver verification failed expected=$enabled actual=$actual"
            )
        }
        return actual == enabled
    }

    fun chargingSeparationState(context: Context): Boolean? {
        val regularRead =
            runCatching {
                Settings.System.getString(
                    context.contentResolver,
                    CHARGING_SEPARATION_KEY
                )
            }.getOrNull()
                ?.let(::parseBooleanSetting)

        return regularRead
            ?: readPrivilegedBoolean(
                "settings get system $CHARGING_SEPARATION_KEY"
            )
    }

    fun supportsChargingSeparationControl(context: Context): Boolean =
        capabilities(context).chargingSeparationControl

    fun setChargingSeparationEnabled(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        val commandResult =
            executePrivileged(
                "settings put system $CHARGING_SEPARATION_KEY $value"
            )
        if (commandResult.isFailure) {
            Log.e(
                TAG,
                "Charging Separation privileged write failed enabled=$enabled",
                commandResult.exceptionOrNull()
            )
            return false
        }

        val actual =
            readPrivilegedBoolean(
                "settings get system $CHARGING_SEPARATION_KEY"
            )
        if (actual != enabled) {
            Log.w(
                TAG,
                "Charging Separation verification failed expected=$enabled actual=$actual"
            )
        }
        return actual == enabled
    }

    private fun readPrivilegedBoolean(command: String): Boolean? =
        executePrivileged(command)
            .getOrNull()
            ?.let(::parseBooleanSetting)

    private fun parseBooleanSetting(raw: String): Boolean? =
        when (raw.trim()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }

    @SuppressLint("PrivateApi")
    private fun findPServerBinder(): IBinder? =
        runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService =
                serviceManager.getDeclaredMethod(
                    "getService",
                    String::class.java
                )
            getService.invoke(null, PSERVER_SERVICE) as? IBinder
        }.getOrNull()

    internal fun executePrivileged(command: String): Result<String?> {
        val binder =
            findPServerBinder()
                ?: return Result.failure(
                    IllegalStateException("PServerBinder unavailable")
                )

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, "1"))
            val sent = binder.transact(0, data, reply, 0)
            if (!sent) {
                return Result.failure(
                    IllegalStateException("PServerBinder command was not accepted")
                )
            }
            val output =
                reply.createByteArray()
                    ?.toString(Charset.defaultCharset())
                    ?.trim()
                    ?.takeUnless { it == "null" }
            Result.success(output)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
