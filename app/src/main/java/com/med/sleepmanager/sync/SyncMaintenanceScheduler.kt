package com.med.sleepmanager.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.service.PeriodicSyncReceiver

object SyncMaintenanceScheduler {
    private const val TAG = "SleepManagerSync"
    private const val REQUEST_CODE = 5221

    const val PERIOD_MS = 24L * 60L * 60L * 1000L
    const val CLOSED_LID_FALSE_WAKE_RETRY_MS = 60_000L

    fun canArm(context: Context): Boolean {
        if (!AppPreferences.isEnabled(context)) return false
        if (!AppPreferences.periodicSyncWhileSleeping(context)) return false

        return ManagedSyncProviders.completionReady(context)
    }

    fun scheduleNext(context: Context): Boolean =
        scheduleAfter(
            context = context,
            delayMs = PERIOD_MS,
            logMessage = "Periodic sleep sync scheduled in 24h"
        )

    fun scheduleClosedLidFalseWakeRetry(context: Context): Boolean =
        scheduleAfter(
            context = context,
            delayMs = CLOSED_LID_FALSE_WAKE_RETRY_MS,
            logMessage = "Periodic sleep sync deferred after closed-lid false wake"
        )

    private fun scheduleAfter(
        context: Context,
        delayMs: Long,
        logMessage: String
    ): Boolean {
        val appContext = context.applicationContext
        if (!canArm(appContext)) {
            cancel(appContext)
            return false
        }

        val alarmManager =
            appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false

        val triggerAt = SystemClock.elapsedRealtime() + delayMs

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent(appContext)
        )

        Log.i(TAG, logMessage)
        return true
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager =
            appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(pendingIntent(appContext))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, PeriodicSyncReceiver::class.java)
                .setAction(PeriodicSyncReceiver.ACTION_PERIODIC_SYNC),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
