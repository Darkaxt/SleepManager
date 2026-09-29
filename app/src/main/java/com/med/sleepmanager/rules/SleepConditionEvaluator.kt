package com.med.sleepmanager.rules

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.med.sleepmanager.data.AppPreferences
import java.util.Calendar

data class SleepConditionResult(
    val met: Boolean,
    val failedReasons: List<String>
)

object SleepConditionEvaluator {

    fun evaluate(context: Context): SleepConditionResult {
        val failures = buildList {
            if (AppPreferences.batteryConditionEnabled(context)) {
                val level = currentBatteryPercent(context)
                val threshold = AppPreferences.batteryBelowPercent(context)

                if (level == null) {
                    add("battery level unavailable")
                } else if (level >= threshold) {
                    add("battery is ${level}% (needs below ${threshold}%)")
                }
            }

            if (AppPreferences.notChargingOnly(context) && isCharging(context)) {
                add("device is charging")
            }

            when (AppPreferences.batterySaverMode(context)) {
                AppPreferences.BATTERY_SAVER_ON -> {
                    if (!isBatterySaverOn(context)) {
                        add("Battery Saver is OFF")
                    }
                }
                AppPreferences.BATTERY_SAVER_OFF -> {
                    if (isBatterySaverOn(context)) {
                        add("Battery Saver is ON")
                    }
                }
            }

            if (AppPreferences.scheduleEnabled(context)) {
                val start = AppPreferences.scheduleStartMinutes(context)
                val end = AppPreferences.scheduleEndMinutes(context)
                val now = currentMinutesOfDay()

                if (!isWithinSchedule(now, start, end)) {
                    add("outside scheduled time")
                }
            }
        }

        return SleepConditionResult(
            met = failures.isEmpty(),
            failedReasons = failures
        )
    }

    private fun currentBatteryPercent(context: Context): Int? {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

            if (level >= 0 && scale > 0) {
                return ((level * 100f) / scale)
                    .toInt()
                    .coerceIn(0, 100)
            }
        }

        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val value = batteryManager?.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        ) ?: return null

        return value.takeIf { it in 0..100 }
    }

    private fun isCharging(context: Context): Boolean {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return false

        return when (
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
        ) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            else -> false
        }
    }

    private fun isBatterySaverOn(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }

    private fun currentMinutesOfDay(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(Calendar.MINUTE)
    }

    private fun isWithinSchedule(
        currentMinutes: Int,
        startMinutes: Int,
        endMinutes: Int
    ): Boolean {
        if (startMinutes == endMinutes) return true

        return if (startMinutes < endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }
}
