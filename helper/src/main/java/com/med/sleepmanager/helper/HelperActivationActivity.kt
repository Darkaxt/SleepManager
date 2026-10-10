package com.med.sleepmanager.helper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.med.sleepmanager.protocol.HelperProtocol

/**
 * One-shot trampoline used after a fresh sideload.
 *
 * Some handheld firmware keeps a newly installed companion APK in Android's
 * stopped/notLaunched state and ignores broadcasts to its manifest receiver,
 * even when FLAG_INCLUDE_STOPPED_PACKAGES is present. Starting this explicit,
 * signature-protected no-display Activity clears that package state. It also
 * reports the current radio state immediately, then finishes.
 */
class HelperActivationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Compatibility Helper activation trampoline")
        SleepManagerHelperReceiver().onReceive(
            this,
            Intent(HelperProtocol.ACTION_QUERY)
        )
        finish()
    }

    private companion object {
        const val TAG = "SleepManagerHelper"
    }
}
