package com.med.sleepmanager.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives Key Mapper state changes without starting a long-running service. */
class KeyMapperStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != KeyMapperController.ACTION_STATE_CHANGED ||
            !intent.hasExtra(KeyMapperController.EXTRA_PAUSED)
        ) return
        KeyMapperController.observeState(
            context,
            intent.getBooleanExtra(KeyMapperController.EXTRA_PAUSED, false)
        )
    }
}
