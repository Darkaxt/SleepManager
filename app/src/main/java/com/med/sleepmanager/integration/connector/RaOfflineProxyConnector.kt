package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.RaOfflineProxyController
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyCommandResult
import com.med.sleepmanager.rules.RaOfflineProxyPolicy
import com.med.sleepmanager.rules.RaOfflineProxyPreSleepDecision

/**
 * Ownership-safe connector for RAOfflineProxy Automation API v1.
 *
 * sleep()/wake() perform ContentProvider calls and must be invoked off the
 * main thread by SleepManager's service orchestration.
 */
object RaOfflineProxyConnector : AppConnector {
    override val id: String = "raofflineproxy"
    // The proxy must be available for offline RetroAchievements play too.
    // Internet is only needed by RAOfflineProxy for upstream queue work.
    override val wakeRequiresNetwork: Boolean = false

    const val TOKEN_RESTART = "restart"

    override fun isInstalled(context: Context): Boolean =
        RaOfflineProxyController.isInstalled(context)

    override fun availability(context: Context): ConnectorAvailability {
        if (!isInstalled(context)) {
            return ConnectorAvailability.Unavailable(
                "RAOfflineProxy is not installed"
            )
        }

        if (!RaOfflineProxyController.providerAvailable(context)) {
            return ConnectorAvailability.Unavailable(
                "RAOfflineProxy Automation API provider is unavailable"
            )
        }

        if (!RaOfflineProxyController.hasControlPermission(context)) {
            return ConnectorAvailability.Unavailable(
                "RAOfflineProxy control permission is missing"
            )
        }

        if (!RaOfflineProxyController.isBatteryUnrestricted(context)) {
            return ConnectorAvailability.Unavailable(
                "RAOfflineProxy battery usage must be Unrestricted"
            )
        }

        return ConnectorAvailability.Available
    }

    override fun currentState(context: Context): ConnectorState {
        val status =
            RaOfflineProxyController.lastObservedStatus()
                ?: return ConnectorState.UNKNOWN

        return if (status.shouldBeRunning || status.running) {
            ConnectorState.ACTIVE
        } else {
            ConnectorState.INACTIVE
        }
    }

    override fun sleep(context: Context): ConnectorSleepResult {
        val initial = RaOfflineProxyController.status(context)
        return when (RaOfflineProxyPolicy.preSleepDecision(initial)) {
            RaOfflineProxyPreSleepDecision.LEAVE_UNTOUCHED ->
                ConnectorSleepResult(
                    attempted = false,
                    changed = false,
                    detail = "RAOfflineProxy already intentionally stopped"
                )

            RaOfflineProxyPreSleepDecision.WAIT_FOR_QUEUE ->
                ConnectorSleepResult(
                    attempted = false,
                    changed = false,
                    detail =
                        "RAOfflineProxy queue is busy; keep proxy and Wi-Fi available"
                )

            RaOfflineProxyPreSleepDecision.WAIT_FOR_SAFE_STATUS ->
                ConnectorSleepResult(
                    attempted = false,
                    changed = false,
                    detail =
                        "RAOfflineProxy status is unavailable or unsafe to change"
                )

            RaOfflineProxyPreSleepDecision.UNSUPPORTED_API ->
                ConnectorSleepResult(
                    attempted = false,
                    changed = false,
                    detail =
                        "Unsupported RAOfflineProxy Automation API version"
                )

            RaOfflineProxyPreSleepDecision.STOP_NOW -> {
                val result = RaOfflineProxyController.stop(context)
                val owned =
                    RaOfflineProxyPolicy.shouldTakeStopOwnership(
                        initialStatus = initial,
                        stopResult = result
                    )
                ConnectorSleepResult(
                    attempted = true,
                    changed = owned,
                    restoreToken = if (owned) TOKEN_RESTART else null,
                    detail =
                        "RAOfflineProxy STOP result=${result.code ?: "unknown"}"
                )
            }
        }
    }

    override fun wake(
        context: Context,
        restoreToken: String?
    ): ConnectorWakeResult {
        if (restoreToken != TOKEN_RESTART) {
            return ConnectorWakeResult(
                attempted = false,
                success = false,
                detail = "Missing or unknown RAOfflineProxy restore token"
            )
        }

        val result = RaOfflineProxyController.start(context)
        return ConnectorWakeResult(
            attempted = true,
            success = result.code == RaOfflineProxyCommandResult.RESULT_OK,
            detail =
                "RAOfflineProxy START result=${result.code ?: "unknown"}"
        )
    }
}
