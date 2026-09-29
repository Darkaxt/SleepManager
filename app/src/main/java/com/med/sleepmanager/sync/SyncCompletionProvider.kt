package com.med.sleepmanager.sync

import android.content.Context
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.SyncthingController

enum class SyncCompletionState {
    SYNCING,
    SYNCED,
    UNKNOWN
}

data class SyncControlResult(
    val attempted: Boolean,
    val success: Boolean,
    val detail: String
)

interface SyncCompletionProvider {
    val id: String
    val displayName: String
    val completionStateAvailable: Boolean

    /**
     * Force the managed client into a running state for a maintenance sync.
     * This does not imply that synchronization has completed.
     */
    fun startSync(): SyncControlResult

    /**
     * Force the managed client to stop after maintenance work.
     */
    fun stopSync(): SyncControlResult

    /**
     * Must describe synchronization completion, not merely process/runtime state.
     */
    fun currentSyncState(): SyncCompletionState

    /**
     * Return true/false only when the provider can reliably confirm that the
     * managed client has fully stopped. Null means no reliable stop-state API is
     * available and callers must use their bounded fallback policy.
     */
    fun isStopConfirmed(): Boolean? = null
}

internal fun basicSyncCompletionState(
    state: BasicSyncController.RemoteState?
): SyncCompletionState {
    state ?: return SyncCompletionState.UNKNOWN

    if (state.runState != BasicSyncController.RunState.RUNNING) {
        return SyncCompletionState.UNKNOWN
    }

    val counters =
        state.syncCounters
            ?: return SyncCompletionState.UNKNOWN

    if (
        state.blockedReasons.isNotEmpty() ||
        counters.foldersErrored > 0
    ) {
        return SyncCompletionState.UNKNOWN
    }

    if (
        counters.foldersScanning > 0 ||
        counters.foldersSyncing > 0 ||
        counters.foldersCleaning > 0 ||
        counters.foldersStarting > 0 ||
        counters.devicesSyncing > 0 ||
        counters.devicesPending > 0
    ) {
        return SyncCompletionState.SYNCING
    }

    return if (
        counters.foldersIdle > 0 &&
        counters.devicesConnected > 0
    ) {
        SyncCompletionState.SYNCED
    } else {
        SyncCompletionState.UNKNOWN
    }
}

class BasicSyncCompletionProvider(
    context: Context
) : SyncCompletionProvider {
    private val appContext = context.applicationContext

    override val id: String = "basicsync"
    override val displayName: String = "BasicSync"
    override val completionStateAvailable: Boolean =
        BasicSyncController.supportsSyncCounters(appContext)

    override fun startSync(): SyncControlResult {
        val installed = BasicSyncController.isInstalled(appContext)
        if (!installed) {
            return SyncControlResult(
                attempted = false,
                success = false,
                detail = "BasicSync is not installed"
            )
        }

        if (!completionStateAvailable) {
            return SyncControlResult(
                attempted = false,
                success = false,
                detail = "BasicSync 3.19+ is required for sync counters"
            )
        }

        // Do not allow a stale pre-start STATE_CHANGED snapshot to satisfy the
        // coordinator's stability window. BasicSync will publish fresh state as
        // START takes effect; REQUEST_STATE also asks for an immediate snapshot.
        BasicSyncController.clearObservedState()
        val sent = BasicSyncController.sendStart(appContext)
        if (sent) {
            BasicSyncController.requestStateBroadcast(appContext)
        }

        return SyncControlResult(
            attempted = true,
            success = sent,
            detail = if (sent) "START sent" else "START not sent"
        )
    }

    override fun stopSync(): SyncControlResult {
        val installed = BasicSyncController.isInstalled(appContext)
        val sent = installed && BasicSyncController.sendStop(appContext)
        if (sent) {
            BasicSyncController.requestStateBroadcast(appContext)
        }
        return SyncControlResult(
            attempted = installed,
            success = sent,
            detail = if (sent) "STOP sent" else "STOP not sent"
        )
    }

    override fun currentSyncState(): SyncCompletionState =
        if (completionStateAvailable) {
            basicSyncCompletionState(
                BasicSyncController.lastObservedState()
            )
        } else {
            SyncCompletionState.UNKNOWN
        }

    override fun isStopConfirmed(): Boolean? =
        if (BasicSyncController.supportsStateApi(appContext)) {
            BasicSyncController.isConfirmedStopped()
        } else {
            null
        }
}

class SyncthingCompletionProvider(
    context: Context
) : SyncCompletionProvider {
    private val appContext = context.applicationContext
    // Keep one target for the whole maintenance session even if the UI selection
    // changes while the operation is running.
    private val target = SyncthingController.selectedTarget(appContext)

    override val id: String = "syncthing"
    override val displayName: String = "Syncthing-Fork"
    override val completionStateAvailable: Boolean = false

    override fun startSync(): SyncControlResult {
        val packageName = target?.packageName
            ?: return SyncControlResult(
                attempted = false,
                success = false,
                detail = "No Syncthing target installed"
            )

        val sent = SyncthingController.sendStartTo(appContext, packageName)
        return SyncControlResult(
            attempted = true,
            success = sent,
            detail = if (sent) "START sent" else "START not sent"
        )
    }

    override fun stopSync(): SyncControlResult {
        val packageName = target?.packageName
            ?: return SyncControlResult(
                attempted = false,
                success = false,
                detail = "No Syncthing target installed"
            )

        val sent = SyncthingController.sendStopTo(appContext, packageName)
        return SyncControlResult(
            attempted = true,
            success = sent,
            detail = if (sent) "STOP sent" else "STOP not sent"
        )
    }

    override fun currentSyncState(): SyncCompletionState =
        SyncCompletionState.UNKNOWN
}

object ManagedSyncProviders {
    fun completionReady(context: Context): Boolean {
        val providers = selected(context)
        return SyncMaintenancePolicy.completionReady(
            selectedProviderCount = providers.size,
            allSelectedProvidersCompletionAware =
                providers.all { it.completionStateAvailable }
        )
    }

    fun selected(context: Context): List<SyncCompletionProvider> = buildList {
        // Advanced completion-aware maintenance is intentionally BasicSync-only.
        // Syncthing-Fork keeps its independent STOP/FOLLOW sleep/wake behavior
        // because it does not expose a supported sync-completion API.
        if (
            AppPreferences.manageBasicSync(context) &&
            BasicSyncController.isInstalled(context)
        ) {
            add(BasicSyncCompletionProvider(context))
        }
    }
}
