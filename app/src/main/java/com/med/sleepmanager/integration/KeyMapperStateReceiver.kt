package com.med.sleepmanager.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives Key Mapper state changes without starting a long-running service.
 *
 * It stays exported without a permission because Key Mapper 4.7+ sends
 * ACTION_MAPPINGS_STATE_CHANGED with plain sendBroadcast (and explicit copies to
 * manifest receivers), with no permission and without sharing its identity.
 * See [BroadcastSenderPolicy] for what is and is not enforced.
 */
class KeyMapperStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != KeyMapperController.ACTION_STATE_CHANGED) return
        val paused =
            KeyMapperStatePolicy.pausedExtra(
                runCatching {
                    @Suppress("DEPRECATION")
                    intent.extras?.get(KeyMapperController.EXTRA_PAUSED)
                }.getOrNull()
            )
        if (paused == null) {
            Log.w(TAG, "Ignored malformed Key Mapper state broadcast")
            return
        }
        val verdict =
            BroadcastSenderGuard.evaluate(
                context,
                this,
                KeyMapperController.PACKAGE,
                TAG
            )
        if (verdict == BroadcastSenderVerdict.REJECTED) return
        KeyMapperController.observeState(context, paused)
    }

    private companion object {
        const val TAG = "SleepManagerKeyMapper"
    }
}

internal object KeyMapperStatePolicy {
    /** Key Mapper sends a Boolean extra; anything else is malformed, never "active". */
    fun pausedExtra(value: Any?): Boolean? = value as? Boolean
}
