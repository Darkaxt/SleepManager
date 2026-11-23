package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.JamesDspController

object JamesDspConnector : AppConnector {
    override val id: String = "jamesdsp"
    override val wakeRequiresNetwork: Boolean = false

    override fun isInstalled(context: Context): Boolean =
        JamesDspController.isInstalled(context)

    override fun availability(context: Context): ConnectorAvailability =
        if (isInstalled(context)) {
            ConnectorAvailability.Available
        } else {
            ConnectorAvailability.Unavailable("JamesDSP is not installed")
        }

    // JamesDSP has no state readback, so its commands cannot be verified and
    // the state stays UNKNOWN. Checked against ThePBone/RootlessJamesDSP master
    // 60d25ae8a53c (2026-07-24), the source of both packages SleepManager
    // drives (flavour "rootless" = me.timschneeberger.rootlessjamesdsp, flavour
    // "root" = james.dsp, see app/build.gradle.kts):
    // - The only exported entry points are PowerStateReceiver (SET_POWER_STATE,
    //   write-only: it calls toggleEnginePower and returns no result),
    //   SessionReceiver (audio-effect session open/close), BootCompletedReceiver,
    //   the QS tile and notification-listener services (system-bound only) and
    //   ShizukuProvider (INTERACT_ACROSS_USERS_FULL). Every other provider and
    //   service is exported="false".
    // - Engine state lives in the app's private preferences (root: key_powered_on)
    //   or in its non-exported RootlessAudioProcessorService (MediaProjection).
    // - ACTION_SERVICE_STARTED / ACTION_SERVICE_STOPPED are sent with
    //   LocalBroadcastManager, which never leaves the JamesDSP process.
    // - An ordered broadcast cannot confirm delivery either: PowerStateReceiver
    //   never sets a result, so the result callback looks the same whether or
    //   not it ran. Android does not let SleepManager list another app's running
    //   services or processes (API 26+), or read its notifications without
    //   notification-listener access.
    // Restore therefore stays pending only when the command cannot be sent;
    // verifying the result needs an upstream state API (for example a state
    // broadcast or an ordered-broadcast result from PowerStateReceiver).
    override fun currentState(context: Context): ConnectorState =
        ConnectorState.UNKNOWN

    override fun sleep(context: Context): ConnectorSleepResult {
        val target =
            JamesDspController.selectedTarget(context)
                ?: return ConnectorSleepResult(
                    attempted = false,
                    changed = false,
                    detail = "JamesDSP is not installed"
                )

        val sent =
            JamesDspController.setPowered(
                context = context,
                packageName = target.packageName,
                enabled = false
            )

        return ConnectorSleepResult(
            attempted = sent,
            changed = sent,
            restoreToken = if (sent) target.packageName else null,
            detail =
                if (sent) {
                    "Power OFF sent; previous state could not be queried"
                } else {
                    "Power OFF not sent"
                }
        )
    }

    override fun wake(
        context: Context,
        restoreToken: String?
    ): ConnectorWakeResult {
        val packageName =
            restoreToken?.takeIf { it.isNotBlank() }
                ?: return ConnectorWakeResult(
                    attempted = false,
                    success = false,
                    detail = "Missing JamesDSP restore target"
                )

        val sent =
            JamesDspController.setPowered(
                context = context,
                packageName = packageName,
                enabled = true
            )

        return ConnectorWakeResult(
            attempted = sent,
            success = sent,
            detail = if (sent) "Power ON sent" else "Power ON not sent"
        )
    }
}
