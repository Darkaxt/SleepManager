package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.SyncthingController

object SyncthingConnector : AppConnector {
    private const val TOKEN_CONFIRMED_RUNNING_PREFIX = "confirmed:"
    private const val TOKEN_UNVERIFIED_PREFIX = "unverified:"

    enum class PreSleepState {
        CONFIRMED_RUNNING,
        UNKNOWN
    }

    data class PreSleepProbe(
        val packageName: String,
        val state: PreSleepState
    )

    override val id: String = "syncthing"
    override val wakeRequiresNetwork: Boolean = true

    override fun isInstalled(context: Context): Boolean =
        SyncthingController.installedTargets(context).isNotEmpty()

    override fun availability(context: Context): ConnectorAvailability =
        if (SyncthingController.selectedTarget(context) != null) {
            ConnectorAvailability.Available
        } else {
            ConnectorAvailability.Unavailable("Syncthing-Fork is not installed")
        }

    // Syncthing-Fork exposes STOP/FOLLOW control broadcasts, but no reliable
    // public state query that SleepManager can use to distinguish running from
    // already-paused. Keep this explicit rather than pretending the state is known.
    override fun currentState(context: Context): ConnectorState =
        ConnectorState.UNKNOWN

    /**
     * Network probe intended for a background executor only.
     *
     * An unreachable local health endpoint cannot prove Syncthing was already stopped,
     * so that outcome remains UNKNOWN instead of being treated as false/running=false.
     */
    fun probeBeforeSleep(packageName: String): PreSleepProbe =
        PreSleepProbe(
            packageName = packageName,
            state =
                when (SyncthingController.healthProbeState()) {
                    SyncthingController.HealthProbeState.RUNNING ->
                        PreSleepState.CONFIRMED_RUNNING
                    SyncthingController.HealthProbeState.UNAVAILABLE ->
                        PreSleepState.UNKNOWN
                }
        )

    /**
     * Safe fallback for generic connector callers: send STOP without doing network I/O.
     */
    override fun sleep(context: Context): ConnectorSleepResult {
        val target = SyncthingController.selectedTarget(context)
            ?: return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "No Syncthing target installed"
            )

        return sleep(
            context = context,
            preSleepProbe = PreSleepProbe(
                packageName = target.packageName,
                state = PreSleepState.UNKNOWN
            )
        )
    }

    fun sleep(
        context: Context,
        preSleepProbe: PreSleepProbe
    ): ConnectorSleepResult {
        val sent =
            SyncthingController.sendStopTo(
                context,
                preSleepProbe.packageName
            )

        return resultAfterStop(
            packageName = preSleepProbe.packageName,
            preSleepState = preSleepProbe.state,
            stopSent = sent
        )
    }

    internal fun resultAfterStop(
        packageName: String,
        preSleepState: PreSleepState,
        stopSent: Boolean
    ): ConnectorSleepResult {
        val token =
            when {
                !stopSent -> null
                preSleepState == PreSleepState.CONFIRMED_RUNNING ->
                    TOKEN_CONFIRMED_RUNNING_PREFIX + packageName
                else ->
                    TOKEN_UNVERIFIED_PREFIX + packageName
            }

        return ConnectorSleepResult(
            attempted = true,
            changed = stopSent,
            restoreToken = token,
            detail = when {
                !stopSent -> "STOP not sent"
                preSleepState == PreSleepState.CONFIRMED_RUNNING ->
                    "STOP sent; running state was confirmed"
                else ->
                    "STOP sent; previous state could not be confirmed"
            }
        )
    }

    override fun wake(context: Context, restoreToken: String?): ConnectorWakeResult {
        val packageName = restoreTargetPackage(restoreToken)
            ?: return ConnectorWakeResult(
                attempted = false,
                success = false,
                detail = "Missing restore target"
            )

        val sent = SyncthingController.sendFollowTo(context, packageName)
        return ConnectorWakeResult(
            attempted = true,
            success = sent,
            detail = if (sent) "FOLLOW sent" else "FOLLOW not sent"
        )
    }

    fun restoreTargetPackage(restoreToken: String?): String? =
        when {
            restoreToken.isNullOrBlank() -> null
            restoreToken.startsWith(TOKEN_CONFIRMED_RUNNING_PREFIX) ->
                restoreToken.removePrefix(TOKEN_CONFIRMED_RUNNING_PREFIX)
            restoreToken.startsWith(TOKEN_UNVERIFIED_PREFIX) ->
                restoreToken.removePrefix(TOKEN_UNVERIFIED_PREFIX)
            else -> restoreToken
        }

    /**
     * Returns false only when STOP can be proven to have failed.
     * Null means the previous state was not verifiable, so legacy behavior is kept.
     */
    fun verifyStopAfterGrace(
        restoreToken: String?
    ): Boolean? {
        if (
            restoreToken == null ||
            !restoreToken.startsWith(TOKEN_CONFIRMED_RUNNING_PREFIX)
        ) {
            return null
        }

        return when (SyncthingController.healthProbeState()) {
            SyncthingController.HealthProbeState.RUNNING -> false
            SyncthingController.HealthProbeState.UNAVAILABLE -> true
        }
    }
}
