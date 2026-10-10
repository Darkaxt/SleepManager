package com.med.sleepmanager.helper

import com.med.sleepmanager.protocol.HelperProtocol

/**
 * Helper-owned signature-permission transport.
 *
 * The legacy receiver remains published for older SleepManager versions.
 * Current SleepManager versions target this endpoint so replacing the main APK
 * cannot invalidate the Helper's command/reply permission grant.
 */
class SleepManagerHelperReceiverV2 : SleepManagerHelperReceiver() {
    override val responsePermission: String
        get() = HelperProtocol.PERMISSION_V2

    override val stateAction: String
        get() = HelperProtocol.ACTION_STATE_V2

    override val resultAction: String
        get() = HelperProtocol.ACTION_RESULT_V2
}
