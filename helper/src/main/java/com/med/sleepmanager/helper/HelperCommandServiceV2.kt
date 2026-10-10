package com.med.sleepmanager.helper

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Explicit command transport for current SleepManager versions.
 *
 * Some handheld firmware skips manifest BroadcastReceiver delivery when the
 * Helper process is not already alive, even for explicit signature-protected
 * broadcasts. An explicit short-lived Service gives Android a normal component
 * start path while keeping the same command payload and reply protocol.
 */
class HelperCommandServiceV2 : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent != null) {
            Log.i(TAG, "Helper v2 service command: ${intent.action}")
            SleepManagerHelperReceiverV2().onReceive(this, intent)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "SleepManagerHelper"
    }
}
