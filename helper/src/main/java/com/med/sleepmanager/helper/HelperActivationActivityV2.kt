package com.med.sleepmanager.helper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.med.sleepmanager.protocol.HelperProtocol

/**
 * One-shot activation trampoline for the Helper-owned v2 transport.
 */
class HelperActivationActivityV2 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Compatibility Helper v2 activation trampoline")
        SleepManagerHelperReceiverV2().onReceive(
            this,
            Intent(HelperProtocol.ACTION_QUERY)
        )
        finish()
    }

    private companion object {
        const val TAG = "SleepManagerHelper"
    }
}
