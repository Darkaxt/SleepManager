package com.med.sleepmanager.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.med.sleepmanager.MainActivity
import com.med.sleepmanager.R
import com.med.sleepmanager.data.UpdateStateStore

object UpdateNotifier {
    private const val CHANNEL_ID = "sleepmanager_updates"
    private const val NOTIFICATION_ID = 5221

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    fun notifyCombinedIfNeeded(
        context: Context,
        update: UpdateInfo,
        helperUpdate: HelperUpdateInfo
    ) {
        if (!notificationsAllowed(context)) return
        if (
            UpdateStateStore.lastNotifiedUpdateVersion(context) == update.versionName &&
            UpdateStateStore.lastNotifiedHelperUpdateVersion(context) ==
            helperUpdate.versionName
        ) {
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SleepManager updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when SleepManager or Helper updates are available."
            }
        )

        val releaseIntent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val releasePendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            releaseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_sleepmanager)
                .setContentTitle("SleepManager updates available")
                .setContentText(
                    "SleepManager ${update.versionName} and Helper ${helperUpdate.versionName} are ready to install."
                )
                .setStyle(
                    Notification.BigTextStyle().bigText(
                        "SleepManager ${update.versionName} and SleepManager Helper ${helperUpdate.versionName} are available. Tap to open the in-app updater."
                    )
                )
                .setContentIntent(releasePendingIntent)
                .setAutoCancel(true)
                .build()

        manager.cancel(NOTIFICATION_ID + 1)
        manager.notify(NOTIFICATION_ID, notification)
        UpdateStateStore.setLastNotifiedUpdateVersion(context, update.versionName)
        UpdateStateStore.setLastNotifiedHelperUpdateVersion(
            context,
            helperUpdate.versionName
        )
    }

    fun notifyHelperIfNeeded(context: Context, update: HelperUpdateInfo) {
        if (!notificationsAllowed(context)) return
        if (
            UpdateStateStore.lastNotifiedHelperUpdateVersion(context) ==
            update.versionName
        ) {
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SleepManager updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when SleepManager or Helper updates are available."
            }
        )

        val releaseIntent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val releasePendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            releaseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_sleepmanager)
                .setContentTitle("SleepManager Helper update available")
                .setContentText("Update the Helper to keep Wi-Fi and Bluetooth control fully compatible.")
                .setStyle(
                    Notification.BigTextStyle().bigText(
                        "SleepManager Helper ${update.versionName} is required for full Wi-Fi and Bluetooth compatibility. Tap to open the in-app updater."
                    )
                )
                .setContentIntent(releasePendingIntent)
                .setAutoCancel(true)
                .build()

        manager.notify(NOTIFICATION_ID + 1, notification)
        UpdateStateStore.setLastNotifiedHelperUpdateVersion(
            context,
            update.versionName
        )
    }

    fun notifyIfNeeded(context: Context, update: UpdateInfo) {
        if (!notificationsAllowed(context)) return
        if (
            UpdateStateStore.lastNotifiedUpdateVersion(context) ==
            update.versionName
        ) {
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SleepManager updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when SleepManager or Helper updates are available."
            }
        )

        val releaseIntent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val releasePendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            releaseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_sleepmanager)
                .setContentTitle("SleepManager update available")
                .setContentText("Version ${update.versionName} is ready to install.")
                .setStyle(
                    Notification.BigTextStyle().bigText(
                        "SleepManager ${update.versionName} is available. Tap to open the in-app updater."
                    )
                )
                .setContentIntent(releasePendingIntent)
                .setAutoCancel(true)
                .build()

        manager.notify(NOTIFICATION_ID, notification)
        UpdateStateStore.setLastNotifiedUpdateVersion(context, update.versionName)
    }
}
