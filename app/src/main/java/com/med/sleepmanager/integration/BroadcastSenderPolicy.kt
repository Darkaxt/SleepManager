package com.med.sleepmanager.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.util.Log

internal enum class BroadcastSenderVerdict {
    /** Android attributed the broadcast to the expected app. */
    VERIFIED,

    /**
     * Android did not attribute the broadcast. This is the normal case for
     * BasicSync and Key Mapper: neither opts in with
     * BroadcastOptions.setShareIdentityEnabled(true), so Android cannot tell
     * SleepManager who sent it. Such state is accepted only after strict
     * payload validation and is treated as an unauthenticated hint.
     */
    UNATTRIBUTED,

    /** Proven not to come from the expected app, or that app is not installed. */
    REJECTED
}

/**
 * Sender checks for the exported state receivers of third-party integrations.
 *
 * Neither BasicSync (plain sendBroadcast) nor Key Mapper 4.7+ (sendBroadcast plus
 * explicit copies to manifest receivers) protects its state broadcast with a
 * permission or shares its identity, so the receivers must stay exported without
 * a permission and genuine broadcasts arrive UNATTRIBUTED. What can be enforced:
 * a broadcast that Android attributes to any other app (API 34+, when that
 * sender opted in to sharing identity) is rejected, and nothing is accepted
 * while the expected app is not installed.
 *
 * Residual limit: an installed app that does not share its identity can still
 * forge these broadcasts. It gains little: both integrations also accept public,
 * unauthenticated control broadcasts, so such an app could already pause/resume
 * Key Mapper or stop/start BasicSync directly. Forged state can only skew
 * SleepManager's own decisions (whether to pause/stop at sleep, when a
 * maintenance sync looks complete, when a restore looks confirmed).
 */
internal object BroadcastSenderPolicy {
    const val INVALID_UID = -1

    fun evaluate(
        expectedPackage: String,
        expectedUid: Int?,
        sentFromUid: Int,
        sentFromPackage: String?
    ): BroadcastSenderVerdict {
        if (expectedUid == null) return BroadcastSenderVerdict.REJECTED
        if (sentFromPackage != null && sentFromPackage != expectedPackage) {
            return BroadcastSenderVerdict.REJECTED
        }
        if (sentFromUid != INVALID_UID && sentFromUid != expectedUid) {
            return BroadcastSenderVerdict.REJECTED
        }
        return if (sentFromUid == expectedUid || sentFromPackage == expectedPackage) {
            BroadcastSenderVerdict.VERIFIED
        } else {
            BroadcastSenderVerdict.UNATTRIBUTED
        }
    }
}

internal object BroadcastSenderGuard {
    /** Must be called from [BroadcastReceiver.onReceive]. */
    fun evaluate(
        context: Context,
        receiver: BroadcastReceiver,
        expectedPackage: String,
        tag: String
    ): BroadcastSenderVerdict {
        val expectedUid =
            runCatching {
                context.packageManager.getApplicationInfo(expectedPackage, 0).uid
            }.getOrNull()

        var sentFromUid = BroadcastSenderPolicy.INVALID_UID
        var sentFromPackage: String? = null
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                sentFromUid = receiver.sentFromUid
                sentFromPackage = receiver.sentFromPackage
            }
        }

        val verdict =
            BroadcastSenderPolicy.evaluate(
                expectedPackage = expectedPackage,
                expectedUid = expectedUid,
                sentFromUid = sentFromUid,
                sentFromPackage = sentFromPackage
            )
        if (verdict == BroadcastSenderVerdict.REJECTED) {
            Log.w(
                tag,
                "Ignored state broadcast not sent by $expectedPackage " +
                    "(sender uid=$sentFromUid package=$sentFromPackage " +
                    "installed=${expectedUid != null})"
            )
        }
        return verdict
    }
}
