package com.med.sleepmanager.ui.state

import com.med.sleepmanager.device.BackgroundReliability
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyStatus

internal data class SleepManagerUiState(
    val activityRefreshToken: Int = 0,
    val managerEnabled: Boolean = false,
    val manageWifiEnabled: Boolean = false,
    val manageBluetoothEnabled: Boolean = false,
    val manageBatterySaverEnabled: Boolean = false,
    val chargingSeparationWithLidEnabled: Boolean = false,
    val manageSyncthingEnabled: Boolean = false,
    val manageTailscaleEnabled: Boolean = false,
    val manageJamesDspEnabled: Boolean = false,
    val manageBasicSyncEnabled: Boolean = false,
    val manageRaOfflineProxyEnabled: Boolean = false,
    val closedLidProtectionEnabled: Boolean = false,
    val dockDisconnectSleeps: Boolean = false,
    val closedLidPowerSleeps: Boolean = false,
    val periodicSyncWhileSleeping: Boolean = false,
    val syncThenStopOnSleepWake: Boolean = false,
    val sleepGraceMs: Long = 0L,
    val customDelayEnabled: Boolean = false,
    val customDelayMs: Long = 60_000L,
    val batteryConditionEnabled: Boolean = false,
    val batteryBelowPercent: Int = 30,
    val notChargingOnly: Boolean = false,
    val batterySaverMode: String = "ignore",
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinutes: Int = 23 * 60,
    val scheduleEndMinutes: Int = 7 * 60,
    val automaticUpdateChecks: Boolean = true,
    val currentWifiState: Boolean? = null,
    val currentBluetoothState: Boolean? = null,
    val currentSyncthingState: SyncthingController.RuntimeState? = null,
    val currentTailscaleConnected: Boolean? = null,
    val currentBasicSyncState: BasicSyncController.RemoteState? = null,
    val basicSyncReadinessProbeComplete: Boolean = false,
    val currentRaOfflineProxyStatus: RaOfflineProxyStatus? = null,
    val raOfflineProxyStatusProbeComplete: Boolean = false,
    val currentBackgroundReliability: BackgroundReliability.Snapshot? = null,
    val currentDeviceControlCapabilities: DeviceControlController.ControlCapabilities? = null,
    val currentBatterySaverState: Boolean = false,
    val installerReturnToken: Int = 0
)
