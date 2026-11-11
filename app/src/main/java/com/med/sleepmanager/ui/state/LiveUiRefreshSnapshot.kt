package com.med.sleepmanager.ui.state

import android.content.Context
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.device.RadioController
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.KeyMapperController
import com.med.sleepmanager.integration.RaOfflineProxyController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.protection.LidMonitor
import com.med.sleepmanager.protection.PmicPowerButtonMonitor
import com.med.sleepmanager.update.UpdateChecker
import com.med.sleepmanager.update.UpdateNotifier

/**
 * Immutable results of a single 3-second UI refresh. read() is intentionally
 * executed on a worker thread, never from a Compose frame or the Activity's
 * main-thread Handler.
 *
 * Equality compares the value of each derived UI datum, not just the
 * presence of a poll. A complete metadata scan preserves external changes
 * such as a newly installed helper, revoked permission or app update.
 */
internal data class LiveUiRefreshSnapshot(
    val metadata: List<Any?>,
    val dashboard: BatterySleepStore.Dashboard,
    val stats: BatterySleepStore.Stats,
    val events: List<EventHistoryStore.Event>,
    val directRadioAvailable: Boolean,
    val wifiEnabled: Boolean?,
    val bluetoothEnabled: Boolean?,
    val tailscaleConnected: Boolean?
) {
    fun signature(): UiRefreshSignature =
        UiRefreshSignature(metadata, dashboard, stats, events)

    companion object {
        fun read(context: Context): LiveUiRefreshSnapshot {
            val directRadio = RadioController.directAvailable()
            val helperInstalled = HelperController.isInstalled(context)
            val basicSyncInstalled = BasicSyncController.isInstalled(context)
            val tailscaleInstalled = TailscaleController.isInstalled(context)
            val raInstalled = RaOfflineProxyController.isInstalled(context)

            val metadata: List<Any?> = listOf(
                AppPreferences.isSetupComplete(context),
                directRadio,
                helperInstalled,
                if (helperInstalled && !directRadio) {
                    HelperController.isBatteryUnrestricted(context)
                } else true,
                if (helperInstalled && !directRadio) {
                    HelperController.wifiControlAccess(context)
                } else null,
                runCatching {
                    context.packageManager.getPackageInfo(HelperController.PACKAGE, 0)
                        .versionName
                }.getOrNull(),
                SyncthingController.installedTargets(context),
                SyncthingController.selectedTarget(context),
                tailscaleInstalled,
                if (tailscaleInstalled) TailscaleController.versionName(context) else null,
                JamesDspController.selectedTarget(context),
                KeyMapperController.versionName(context),
                KeyMapperController.currentState(context),
                basicSyncInstalled,
                if (basicSyncInstalled) BasicSyncController.versionName(context) else null,
                if (basicSyncInstalled) BasicSyncController.isStopped(context) else false,
                if (basicSyncInstalled) BasicSyncController.supportsStateApi(context) else false,
                raInstalled,
                if (raInstalled) RaOfflineProxyController.versionName(context) else null,
                if (raInstalled) RaOfflineProxyController.providerAvailable(context) else false,
                if (raInstalled) RaOfflineProxyController.hasControlPermission(context) else false,
                if (raInstalled) RaOfflineProxyController.canRequestControlPermission(context) else false,
                if (raInstalled) RaOfflineProxyController.isBatteryUnrestricted(context) else false,
                LidMonitor.isSupported(),
                PmicPowerButtonMonitor.isSupported(),
                SleepCycleStore.restoreProblem(context),
                UpdateChecker.cachedUpdate(context),
                UpdateChecker.cachedHelperUpdate(context),
                UpdateNotifier.notificationsAllowed(context)
            )
            return LiveUiRefreshSnapshot(
                metadata = metadata,
                dashboard = BatterySleepStore.dashboard(context),
                stats = BatterySleepStore.stats(context),
                events = EventHistoryStore.recent(context),
                directRadioAvailable = directRadio,
                wifiEnabled = if (directRadio) RadioController.currentWifiEnabled(context) else null,
                bluetoothEnabled = if (directRadio) RadioController.currentBluetoothEnabled(context) else null,
                tailscaleConnected = if (tailscaleInstalled) {
                    TailscaleController.isConnected(context)
                } else null
            )
        }
    }
}

/** Pure decision model, independently unit-testable without Android. */
internal data class UiRefreshSignature(
    val metadata: Any?,
    val dashboard: Any?,
    val stats: Any?,
    val events: Any?
) {
    fun changesSince(previous: UiRefreshSignature?): UiRefreshChanges =
        UiRefreshChanges(
            metadataChanged = previous == null || previous.metadata != metadata,
            dashboardChanged = previous == null || previous.dashboard != dashboard,
            statsChanged = previous == null || previous.stats != stats,
            eventsChanged = previous == null || previous.events != events
        )
}

internal data class UiRefreshChanges(
    val metadataChanged: Boolean,
    val dashboardChanged: Boolean,
    val statsChanged: Boolean,
    val eventsChanged: Boolean
)
