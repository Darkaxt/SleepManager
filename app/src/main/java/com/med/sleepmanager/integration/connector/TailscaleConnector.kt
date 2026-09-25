package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.integration.TailscaleTransactionToken

object TailscaleConnector : AppConnector {
    override val id: String = "tailscale"
    override val wakeRequiresNetwork: Boolean = true

    override fun isInstalled(context: Context): Boolean =
        TailscaleController.isInstalled(context)

    override fun availability(context: Context): ConnectorAvailability =
        if (isInstalled(context)) {
            ConnectorAvailability.Available
        } else {
            ConnectorAvailability.Unavailable("Tailscale is not installed")
        }

    override fun currentState(context: Context): ConnectorState {
        if (!isInstalled(context)) return ConnectorState.UNKNOWN

        return if (TailscaleController.isConnected(context)) {
            ConnectorState.ACTIVE
        } else {
            ConnectorState.INACTIVE
        }
    }

    override fun sleep(context: Context): ConnectorSleepResult {
        if (!isInstalled(context)) {
            return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "Tailscale not installed"
            )
        }

        val targetPackage = TailscaleController.activePackage(context)
        if (targetPackage == null) {
            return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "No supported Tailscale VPN active"
            )
        }

        val sent = TailscaleController.sendDisconnect(context, targetPackage)
        return ConnectorSleepResult(
            attempted = sent,
            changed = false,
            restoreToken =
                if (sent) {
                    TailscaleTransactionToken.disconnectVerification(targetPackage)
                } else {
                    null
                },
            detail = if (sent) {
                "DISCONNECT sent to $targetPackage; verification pending"
            } else {
                "DISCONNECT not sent to $targetPackage"
            }
        )
    }

    override fun wake(
        context: Context,
        restoreToken: String?
    ): ConnectorWakeResult {
        val targetPackage = TailscaleTransactionToken.restoreTarget(restoreToken)
        if (targetPackage == null) {
            return ConnectorWakeResult(
                attempted = false,
                success = false,
                detail = "Tailscale disconnect target was not verified"
            )
        }

        val sent = TailscaleController.sendConnect(context, targetPackage)
        return ConnectorWakeResult(
            attempted = sent,
            success = sent,
            detail =
                if (sent) {
                    "CONNECT sent to $targetPackage"
                } else {
                    "CONNECT not sent to $targetPackage"
                }
        )
    }
}
