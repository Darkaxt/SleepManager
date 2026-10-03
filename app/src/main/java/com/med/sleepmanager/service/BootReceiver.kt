package com.med.sleepmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.update.UpdateCheckScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        UpdateCheckScheduler.sync(context)
        if (!AppPreferences.isEnabled(context)) return
        val service = Intent(context, SleepManagerService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service)
            else context.startService(service)
            DiagnosticsStateStore.recordEvent(context, "Started after ${intent?.action}")
        } catch (t: Throwable) {
            Log.e("SleepManager", "Unable to restart after boot", t)
        }
    }
}
