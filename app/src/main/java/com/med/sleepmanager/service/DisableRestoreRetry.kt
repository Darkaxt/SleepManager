package com.med.sleepmanager.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.data.PendingRestoreState

/**
 * Backoff for restore work that is still pending after SleepManager was
 * disabled. Delays double from one minute up to one hour; retries stop after
 * [MAX_ATTEMPTS] rounds (roughly a day) until the next boot, disable or Forget.
 */
internal object DisableRestoreRetryPolicy {
    const val BASE_DELAY_MS = 60_000L
    const val MAX_DELAY_MS = 60 * 60_000L
    const val MAX_ATTEMPTS = 30

    fun delayMs(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 20)
        return (BASE_DELAY_MS shl shift).coerceAtMost(MAX_DELAY_MS)
    }

    fun shouldRetry(attempt: Int): Boolean = attempt < MAX_ATTEMPTS
}

/**
 * Persists and arms the disabled-state restore retry. The alarm only starts
 * SleepManagerService with ACTION_DISABLE_AND_RESTORE, which restores and never
 * applies sleep actions while the manager is disabled.
 */
object DisableRestoreRetryScheduler {
    private const val TAG = "SleepManager"
    private const val PREFS = "disable_restore_retry"
    private const val KEY_ATTEMPT = "attempt"
    private const val REQUEST_CODE = 5223

    const val ACTION_RETRY = "com.med.sleepmanager.action.DISABLE_RESTORE_RETRY"

    /** Marks a service start as an automatic retry, which keeps the backoff. */
    const val EXTRA_AUTOMATIC_RETRY = "com.med.sleepmanager.extra.AUTOMATIC_RESTORE_RETRY"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Arms the retry alarm if restore work is pending while disabled, using the
     * current backoff step. With [advance] the step is consumed, so the next
     * call waits longer; without it the alarm is only a watchdog for a round
     * that is starting now. Returns false when nothing is pending or the retry
     * budget is exhausted.
     */
    fun scheduleIfPending(context: Context, advance: Boolean = true): Boolean {
        if (AppPreferences.isEnabled(context) || !PendingRestoreState.hasPendingRestore(context)) {
            cancel(context)
            return false
        }

        val attempt = prefs(context).getInt(KEY_ATTEMPT, 0)
        if (!DisableRestoreRetryPolicy.shouldRetry(attempt)) {
            cancelAlarm(context)
            if (advance) {
                Log.w(TAG, "Disabled restore retries exhausted after $attempt attempts")
                DiagnosticsStateStore.recordEvent(
                    context,
                    "Disable → restore retries exhausted · disable again or restart to retry"
                )
            }
            return false
        }

        val delayMs = DisableRestoreRetryPolicy.delayMs(attempt)
        if (advance) {
            prefs(context).edit().putInt(KEY_ATTEMPT, attempt + 1).commit()
        }

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        // An exact alarm lets the receiver start the foreground service from
        // the background on Android 12+.
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent(context)
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent(context)
            )
        }
        Log.i(TAG, "Disabled restore retry ${attempt + 1} armed in ${delayMs}ms")
        return true
    }

    /** Clears the alarm and the backoff counter. */
    fun cancel(context: Context) {
        cancelAlarm(context)
        prefs(context).edit().clear().commit()
    }

    /** Starts a fresh backoff budget, e.g. after boot. */
    fun resetAttempts(context: Context) {
        prefs(context).edit().clear().commit()
    }

    private fun cancelAlarm(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
            ?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DisableRestoreRetryReceiver::class.java)
                .setAction(ACTION_RETRY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Starts the service in restore-only mode as an automatic retry. */
    fun startRestoreService(context: Context): Boolean {
        val service =
            Intent(context, SleepManagerService::class.java)
                .setAction(SleepManagerService.ACTION_DISABLE_AND_RESTORE)
                .putExtra(EXTRA_AUTOMATIC_RETRY, true)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to start disabled restore retry", t)
            false
        }
    }
}

class DisableRestoreRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DisableRestoreRetryScheduler.ACTION_RETRY) return

        if (
            AppPreferences.isEnabled(context) ||
            !PendingRestoreState.hasPendingRestore(context)
        ) {
            DisableRestoreRetryScheduler.cancel(context)
            return
        }

        if (!DisableRestoreRetryScheduler.startRestoreService(context)) {
            DisableRestoreRetryScheduler.scheduleIfPending(context)
        }
    }
}
