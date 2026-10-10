package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.BasicSyncController.Mode
import com.med.sleepmanager.integration.BasicSyncController.RunState

object BasicSyncConnector : AppConnector {
    override val id: String = "basicsync"
    override val wakeRequiresNetwork: Boolean = false

    const val TOKEN_AUTO_MODE = "auto_mode"
    const val TOKEN_MANUAL_MODE_STARTED = "manual_mode_started"
    const val TOKEN_MANUAL_MODE_STOPPED = "manual_mode_stopped"

    override fun isInstalled(context: Context): Boolean =
        BasicSyncController.isInstalled(context)

    override fun availability(context: Context): ConnectorAvailability =
        if (isInstalled(context)) {
            ConnectorAvailability.Available
        } else {
            ConnectorAvailability.Unavailable("BasicSync is not installed")
        }

    override fun currentState(context: Context): ConnectorState {
        val state = BasicSyncController.lastObservedState()
            ?: return ConnectorState.UNKNOWN

        return when (state.runState) {
            RunState.RUNNING,
            RunState.PAUSED,
            RunState.STARTING -> ConnectorState.ACTIVE

            RunState.NOT_RUNNING,
            RunState.STOPPING,
            RunState.IMPORTING,
            RunState.EXPORTING -> ConnectorState.INACTIVE
        }
    }

    override fun sleep(context: Context): ConnectorSleepResult {
        if (!isInstalled(context)) {
            return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "BasicSync is not installed"
            )
        }

        if (!BasicSyncController.supportsStateApi(context)) {
            val sent = BasicSyncController.sendStop(context)
            return ConnectorSleepResult(
                attempted = sent,
                changed = sent,
                restoreToken = if (sent) TOKEN_AUTO_MODE else null,
                detail =
                    if (sent) {
                        "Legacy STOP sent; restore target is AUTO_MODE"
                    } else {
                        "Legacy STOP not sent"
                    }
            )
        }

        val state =
            BasicSyncController.lastObservedState()
                ?: return ConnectorSleepResult(
                    attempted = false,
                    changed = false,
                    detail = "No pre-sleep BasicSync state observed yet; leaving it untouched"
                )

        if (!shouldStop(state.runState)) {
            return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "BasicSync already inactive (${state.mode}/${state.runState})"
            )
        }

        val restoreToken = when (state.mode) {
            Mode.AUTO_MODE -> TOKEN_AUTO_MODE
            Mode.MANUAL_MODE_STARTED -> TOKEN_MANUAL_MODE_STARTED
            Mode.MANUAL_MODE_STOPPED -> null
        }

        if (restoreToken == null) {
            return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "BasicSync is manually stopped; leaving it untouched"
            )
        }

        val sent = BasicSyncController.sendStop(context)
        return ConnectorSleepResult(
            attempted = sent,
            changed = sent,
            restoreToken = if (sent) restoreToken else null,
            detail =
                if (sent) {
                    "STOP sent; restore target=${restoreTargetName(restoreToken)}"
                } else {
                    "STOP not sent"
                }
        )
    }

    override fun wake(
        context: Context,
        restoreToken: String?
    ): ConnectorWakeResult {
        val sent = when (restoreToken) {
            TOKEN_AUTO_MODE ->
                BasicSyncController.sendAutoMode(context)

            TOKEN_MANUAL_MODE_STARTED ->
                BasicSyncController.sendStart(context)

            TOKEN_MANUAL_MODE_STOPPED ->
                BasicSyncController.sendStop(context)

            else ->
                return ConnectorWakeResult(
                    attempted = false,
                    success = false,
                    detail = "Missing or unknown BasicSync restore token"
                )
        }

        return ConnectorWakeResult(
            attempted = sent,
            success = sent,
            detail =
                if (sent) {
                    "${restoreTargetName(restoreToken)} sent"
                } else {
                    "${restoreTargetName(restoreToken)} not sent"
                }
        )
    }

    fun restoreTargetName(restoreToken: String?): String =
        when (restoreToken) {
            TOKEN_AUTO_MODE -> "AUTO_MODE"
            TOKEN_MANUAL_MODE_STARTED -> "START"
            TOKEN_MANUAL_MODE_STOPPED -> "STOP"
            else -> "UNKNOWN"
        }

    fun restoreConfirmed(
        restoreToken: String?,
        state: BasicSyncController.RemoteState?
    ): Boolean =
        when (restoreToken) {
            TOKEN_AUTO_MODE ->
                state?.mode == Mode.AUTO_MODE

            TOKEN_MANUAL_MODE_STARTED ->
                state?.mode == Mode.MANUAL_MODE_STARTED

            TOKEN_MANUAL_MODE_STOPPED ->
                state?.mode == Mode.MANUAL_MODE_STOPPED &&
                    (
                        state.runState == RunState.NOT_RUNNING ||
                            state.runState == RunState.PAUSED
                    )

            else -> false
        }

    private fun shouldStop(runState: RunState): Boolean =
        when (runState) {
            RunState.RUNNING,
            RunState.PAUSED,
            RunState.STARTING -> true

            RunState.NOT_RUNNING,
            RunState.STOPPING,
            RunState.IMPORTING,
            RunState.EXPORTING -> false
        }
}
