package com.med.sleepmanager

import android.app.admin.DevicePolicyManager
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.IntentCompat
import androidx.core.content.ContextCompat
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.device.BackgroundReliability
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.diagnostics.DiagnosticsBuilder
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.RaOfflineProxyController
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyStatus
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.protection.ClosedLidAdmin
import com.med.sleepmanager.protection.LidMonitor
import com.med.sleepmanager.qs.SleepManagerTileService
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.ui.screens.SleepManagerScreen
import com.med.sleepmanager.ui.state.SleepManagerUiState
import com.med.sleepmanager.ui.state.SleepManagerViewModel
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import com.med.sleepmanager.update.UpdateCheckScheduler
import com.med.sleepmanager.update.UpdateInstaller
import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val uiViewModel: SleepManagerViewModel by viewModels()

    internal val uiState: SleepManagerUiState
        get() = uiViewModel.uiState

    // Compatibility delegates keep the current Activity/screen call sites
    // unchanged while Step 6 moves ownership behind an explicit UiState.
    internal var activityRefreshToken: Int
        get() = uiState.activityRefreshToken
        set(value) {
            uiViewModel.update { it.copy(activityRefreshToken = value) }
        }

    internal var managerEnabledState: Boolean
        get() = uiState.managerEnabled
        set(value) {
            uiViewModel.update { it.copy(managerEnabled = value) }
        }

    internal var manageWifiEnabledState: Boolean
        get() = uiState.manageWifiEnabled
        set(value) {
            uiViewModel.update { it.copy(manageWifiEnabled = value) }
        }

    internal var manageBluetoothEnabledState: Boolean
        get() = uiState.manageBluetoothEnabled
        set(value) {
            uiViewModel.update { it.copy(manageBluetoothEnabled = value) }
        }

    internal var manageBatterySaverEnabledState: Boolean
        get() = uiState.manageBatterySaverEnabled
        set(value) {
            uiViewModel.update { it.copy(manageBatterySaverEnabled = value) }
        }

    internal var chargingSeparationWithLidEnabledState: Boolean
        get() = uiState.chargingSeparationWithLidEnabled
        set(value) {
            uiViewModel.update { it.copy(chargingSeparationWithLidEnabled = value) }
        }

    internal var manageSyncthingEnabledState: Boolean
        get() = uiState.manageSyncthingEnabled
        set(value) {
            uiViewModel.update { it.copy(manageSyncthingEnabled = value) }
        }

    internal var manageTailscaleEnabledState: Boolean
        get() = uiState.manageTailscaleEnabled
        set(value) {
            uiViewModel.update { it.copy(manageTailscaleEnabled = value) }
        }

    internal var manageJamesDspEnabledState: Boolean
        get() = uiState.manageJamesDspEnabled
        set(value) {
            uiViewModel.update { it.copy(manageJamesDspEnabled = value) }
        }

    internal var manageBasicSyncEnabledState: Boolean
        get() = uiState.manageBasicSyncEnabled
        set(value) {
            uiViewModel.update { it.copy(manageBasicSyncEnabled = value) }
        }

    internal var manageRaOfflineProxyEnabledState: Boolean
        get() = uiState.manageRaOfflineProxyEnabled
        set(value) {
            uiViewModel.update {
                it.copy(manageRaOfflineProxyEnabled = value)
            }
        }

    internal var closedLidProtectionEnabledState: Boolean
        get() = uiState.closedLidProtectionEnabled
        set(value) {
            uiViewModel.update { it.copy(closedLidProtectionEnabled = value) }
        }

    internal var dockDisconnectSleepsState: Boolean
        get() = uiState.dockDisconnectSleeps
        set(value) {
            uiViewModel.update { it.copy(dockDisconnectSleeps = value) }
        }

    internal var closedLidPowerSleepsState: Boolean
        get() = uiState.closedLidPowerSleeps
        set(value) {
            uiViewModel.update { it.copy(closedLidPowerSleeps = value) }
        }

    internal var periodicSyncWhileSleepingState: Boolean
        get() = uiState.periodicSyncWhileSleeping
        set(value) {
            uiViewModel.update { it.copy(periodicSyncWhileSleeping = value) }
        }

    internal var syncThenStopOnSleepWakeState: Boolean
        get() = uiState.syncThenStopOnSleepWake
        set(value) {
            uiViewModel.update { it.copy(syncThenStopOnSleepWake = value) }
        }

    internal var sleepGraceMsState: Long
        get() = uiState.sleepGraceMs
        set(value) {
            uiViewModel.update { it.copy(sleepGraceMs = value) }
        }

    internal var customDelayEnabledState: Boolean
        get() = uiState.customDelayEnabled
        set(value) {
            uiViewModel.update { it.copy(customDelayEnabled = value) }
        }

    internal var customDelayMsState: Long
        get() = uiState.customDelayMs
        set(value) {
            uiViewModel.update { it.copy(customDelayMs = value) }
        }

    internal var batteryConditionEnabledState: Boolean
        get() = uiState.batteryConditionEnabled
        set(value) {
            uiViewModel.update { it.copy(batteryConditionEnabled = value) }
        }

    internal var batteryBelowPercentState: Int
        get() = uiState.batteryBelowPercent
        set(value) {
            uiViewModel.update { it.copy(batteryBelowPercent = value) }
        }

    internal var notChargingOnlyState: Boolean
        get() = uiState.notChargingOnly
        set(value) {
            uiViewModel.update { it.copy(notChargingOnly = value) }
        }

    internal var batterySaverModeState: String
        get() = uiState.batterySaverMode
        set(value) {
            uiViewModel.update { it.copy(batterySaverMode = value) }
        }

    internal var scheduleEnabledState: Boolean
        get() = uiState.scheduleEnabled
        set(value) {
            uiViewModel.update { it.copy(scheduleEnabled = value) }
        }

    internal var scheduleStartMinutesState: Int
        get() = uiState.scheduleStartMinutes
        set(value) {
            uiViewModel.update { it.copy(scheduleStartMinutes = value) }
        }

    internal var scheduleEndMinutesState: Int
        get() = uiState.scheduleEndMinutes
        set(value) {
            uiViewModel.update { it.copy(scheduleEndMinutes = value) }
        }

    internal var automaticUpdateChecksState: Boolean
        get() = uiState.automaticUpdateChecks
        set(value) {
            uiViewModel.update { it.copy(automaticUpdateChecks = value) }
        }

    internal var currentWifiState: Boolean?
        get() = uiState.currentWifiState
        set(value) {
            uiViewModel.update { it.copy(currentWifiState = value) }
        }

    internal var currentBluetoothState: Boolean?
        get() = uiState.currentBluetoothState
        set(value) {
            uiViewModel.update { it.copy(currentBluetoothState = value) }
        }

    internal var currentSyncthingState: SyncthingController.RuntimeState?
        get() = uiState.currentSyncthingState
        set(value) {
            uiViewModel.update { it.copy(currentSyncthingState = value) }
        }

    internal var currentTailscaleConnected: Boolean?
        get() = uiState.currentTailscaleConnected
        set(value) {
            uiViewModel.update { it.copy(currentTailscaleConnected = value) }
        }

    internal var currentBasicSyncState: BasicSyncController.RemoteState?
        get() = uiState.currentBasicSyncState
        set(value) {
            uiViewModel.update { it.copy(currentBasicSyncState = value) }
        }

    internal var currentRaOfflineProxyStatus: RaOfflineProxyStatus?
        get() = uiState.currentRaOfflineProxyStatus
        set(value) {
            uiViewModel.update {
                it.copy(currentRaOfflineProxyStatus = value)
            }
        }

    internal var raOfflineProxyStatusProbeComplete: Boolean
        get() = uiState.raOfflineProxyStatusProbeComplete
        set(value) {
            uiViewModel.update {
                it.copy(raOfflineProxyStatusProbeComplete = value)
            }
        }

    internal var currentBackgroundReliability: BackgroundReliability.Snapshot?
        get() = uiState.currentBackgroundReliability
        set(value) {
            uiViewModel.update { it.copy(currentBackgroundReliability = value) }
        }

    internal var currentDeviceControlCapabilities: DeviceControlController.ControlCapabilities?
        get() = uiState.currentDeviceControlCapabilities
        set(value) {
            uiViewModel.update { it.copy(currentDeviceControlCapabilities = value) }
        }

    internal var currentBatterySaverState: Boolean
        get() = uiState.currentBatterySaverState
        set(value) {
            uiViewModel.update { it.copy(currentBatterySaverState = value) }
        }

    @Volatile
    private var syncthingStateProbeRunning = false
    @Volatile
    private var raOfflineProxyStateProbeRunning = false
    @Volatile
    private var backgroundReliabilityProbeRunning = false
    @Volatile
    private var deviceCapabilitiesProbeRunning = false
    private var helperStateReceiverRegistered = false
    private var batterySaverStateReceiverRegistered = false
    private var pendingClosedLidAdminEnable = false
    private var pendingExactAlarmEnable = false
    private var pendingExternalNavigation = false
    private var pendingUpdateInstallPath: String? = null
    private var pendingPackageInstallerReturn = false
    internal var installerReturnToken: Int
        get() = uiState.installerReturnToken
        set(value) {
            uiViewModel.update { it.copy(installerReturnToken = value) }
        }
    internal var openUpdatesOnLaunch = false

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            pendingExternalNavigation = false
            activityRefreshToken++
        }

    private val unusedAppRestrictionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            pendingExternalNavigation = false
            refreshBackgroundReliabilityAsync()
        }

    private val statusRefreshHandler = Handler(Looper.getMainLooper())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                // Refresh the real radio states through the compatibility helper.
                HelperController.requestState(this@MainActivity)
                refreshIntegrationRuntimeStates()
                refreshManagerEnabledState()
                refreshManagedRadioSettingsState()
                refreshManagedDeviceActionSettingsState()
                refreshManagedIntegrationSettingsState()
                refreshManagedClamshellSettingsState()
                refreshManagedSyncSettingsState()
                refreshSleepDelaySettingsState()
                refreshAdvancedBatteryConditionSettingsState()
                refreshScheduleSettingsState()
                refreshUpdateSettingsState()

                // Re-read every UI-facing state while the Activity is visible:
                // manager/service state, enabled actions, helper availability,
                // Syncthing targets/selection/version, behavior recap and last activity.
                activityRefreshToken++

                statusRefreshHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refreshIntegrationRuntimeStates() {
        currentTailscaleConnected =
            if (TailscaleController.isInstalled(this)) {
                TailscaleController.isConnected(this)
            } else {
                null
            }

        currentBasicSyncState =
            if (
                BasicSyncController.isInstalled(this) &&
                BasicSyncController.supportsStateApi(this)
            ) {
                BasicSyncController.startStateObserver(this)
                BasicSyncController.lastObservedState()
            } else {
                null
            }

        if (!RaOfflineProxyController.isInstalled(this)) {
            currentRaOfflineProxyStatus = null
            raOfflineProxyStatusProbeComplete = true
        } else if (!raOfflineProxyStateProbeRunning) {
            raOfflineProxyStateProbeRunning = true
            val appContext = applicationContext
            Thread {
                val status =
                    if (
                        RaOfflineProxyController
                            .providerAvailable(appContext)
                    ) {
                        RaOfflineProxyController.status(appContext)
                    } else {
                        null
                    }
                runOnUiThread {
                    currentRaOfflineProxyStatus = status
                    raOfflineProxyStatusProbeComplete = true
                    raOfflineProxyStateProbeRunning = false
                }
            }.apply {
                name = "SleepManagerRAOfflineProxy"
                isDaemon = true
                start()
            }
        }

        if (SyncthingController.selectedTarget(this) == null) {
            currentSyncthingState = null
            return
        }

        if (syncthingStateProbeRunning) return
        syncthingStateProbeRunning = true

        Thread {
            val state = SyncthingController.runtimeState(this)
            runOnUiThread {
                currentSyncthingState = state
                syncthingStateProbeRunning = false
            }
        }.start()
    }

    private fun refreshBackgroundReliabilityAsync() {
        if (backgroundReliabilityProbeRunning) return
        backgroundReliabilityProbeRunning = true
        val appContext = applicationContext

        Thread {
            val snapshot = BackgroundReliability.snapshot(appContext)
            runOnUiThread {
                currentBackgroundReliability = snapshot
                backgroundReliabilityProbeRunning = false
            }
        }.apply {
            name = "SleepManagerReliability"
            isDaemon = true
            start()
        }
    }

    private fun refreshDeviceCapabilitiesAsync() {
        if (deviceCapabilitiesProbeRunning) return
        deviceCapabilitiesProbeRunning = true
        val appContext = applicationContext

        Thread {
            val capabilities =
                DeviceControlController.capabilities(appContext)
            runOnUiThread {
                currentDeviceControlCapabilities = capabilities
                deviceCapabilitiesProbeRunning = false

                // Battery Saver conditions only read PowerManager state and
                // stay valid on every Android device. Privileged capability
                // detection gates only the Home Battery Saver sleep action.
            }
        }.apply {
            name = "SleepManagerCapabilities"
            isDaemon = true
            start()
        }
    }

    private val helperStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HelperController.ACTION_STATE) return
            currentWifiState = intent.getBooleanExtra(
                HelperController.EXTRA_WIFI_STATE,
                false
            )
            currentBluetoothState = intent.getBooleanExtra(
                HelperController.EXTRA_BLUETOOTH_STATE,
                false
            )
        }
    }

    private val batterySaverStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return
            refreshBatterySaverState()
        }
    }

    private fun refreshBatterySaverState() {
        currentBatterySaverState =
            DeviceControlController.batterySaverEnabled(this)
    }

    private fun refreshManagerEnabledState() {
        managerEnabledState = AppPreferences.isEnabled(this)
    }

    private fun refreshManagedRadioSettingsState() {
        manageWifiEnabledState = AppPreferences.manageWifi(this)
        manageBluetoothEnabledState = AppPreferences.manageBluetooth(this)
    }

    private fun refreshManagedDeviceActionSettingsState() {
        manageBatterySaverEnabledState = AppPreferences.manageBatterySaver(this)
        chargingSeparationWithLidEnabledState =
            AppPreferences.manageChargingSeparationWithLid(this)
    }

    private fun refreshManagedIntegrationSettingsState() {
        manageSyncthingEnabledState = AppPreferences.manageSyncthing(this)
        manageTailscaleEnabledState = AppPreferences.manageTailscale(this)
        manageJamesDspEnabledState = AppPreferences.manageJamesDsp(this)
        manageBasicSyncEnabledState = AppPreferences.manageBasicSync(this)
        manageRaOfflineProxyEnabledState =
            AppPreferences.manageRaOfflineProxy(this)
    }

    private fun refreshManagedClamshellSettingsState() {
        closedLidProtectionEnabledState =
            AppPreferences.manageClosedLidProtection(this)
        dockDisconnectSleepsState = AppPreferences.dockDisconnectSleeps(this)
        closedLidPowerSleepsState = AppPreferences.closedLidPowerSleeps(this)
    }

    private fun refreshManagedSyncSettingsState() {
        periodicSyncWhileSleepingState =
            AppPreferences.periodicSyncWhileSleeping(this)
        syncThenStopOnSleepWakeState =
            AppPreferences.syncThenStopOnSleepWake(this)
    }

    private fun refreshSleepDelaySettingsState() {
        sleepGraceMsState = AppPreferences.sleepGraceMs(this)
        customDelayEnabledState = AppPreferences.customDelayEnabled(this)
        customDelayMsState = AppPreferences.customDelayMs(this)
    }

    private fun refreshAdvancedBatteryConditionSettingsState() {
        batteryConditionEnabledState =
            AppPreferences.batteryConditionEnabled(this)
        batteryBelowPercentState = AppPreferences.batteryBelowPercent(this)
        notChargingOnlyState = AppPreferences.notChargingOnly(this)
        batterySaverModeState = AppPreferences.batterySaverMode(this)
    }

    private fun refreshScheduleSettingsState() {
        scheduleEnabledState = AppPreferences.scheduleEnabled(this)
        scheduleStartMinutesState = AppPreferences.scheduleStartMinutes(this)
        scheduleEndMinutesState = AppPreferences.scheduleEndMinutes(this)
    }

    private fun refreshUpdateSettingsState() {
        automaticUpdateChecksState =
            AppPreferences.automaticUpdateChecks(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openUpdatesOnLaunch =
            intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true
        UpdateCheckScheduler.sync(this)
        refreshBatterySaverState()
        refreshManagerEnabledState()
        refreshManagedRadioSettingsState()
        refreshManagedDeviceActionSettingsState()
        refreshManagedIntegrationSettingsState()
        refreshManagedClamshellSettingsState()
        refreshManagedSyncSettingsState()
        refreshSleepDelaySettingsState()
        refreshAdvancedBatteryConditionSettingsState()
        refreshScheduleSettingsState()
        refreshUpdateSettingsState()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            var useSystemColors by rememberSaveable {
                mutableStateOf(
                    AppPreferences.useSystemColors(this@MainActivity)
                )
            }

            SleepManagerTheme(
                useSystemColors = useSystemColors
            ) {
                SleepManagerScreen(
                    uiState = uiState,
                    context = this@MainActivity,
                    openUpdatesOnLaunch = openUpdatesOnLaunch,
                    useSystemColors = useSystemColors,
                    onRefreshRequested = {
                        activityRefreshToken++
                    },
                    onServiceRefreshRequested = {
                        refreshRunningService()
                    },
                    onManagerEnabledChange = { enabled ->
                        setManagerEnabled(enabled)
                    },
                    onManageWifiChange = { enabled ->
                        AppPreferences.setManageWifi(this@MainActivity, enabled)
                        manageWifiEnabledState = enabled
                    },
                    onManageBluetoothChange = { enabled ->
                        AppPreferences.setManageBluetooth(this@MainActivity, enabled)
                        manageBluetoothEnabledState = enabled
                    },
                    onManageBatterySaverChange = { enabled ->
                        AppPreferences.setManageBatterySaver(this@MainActivity, enabled)
                        manageBatterySaverEnabledState = enabled
                    },
                    onChargingSeparationWithLidChange = { enabled ->
                        AppPreferences.setManageChargingSeparationWithLid(
                            this@MainActivity,
                            enabled
                        )
                        chargingSeparationWithLidEnabledState = enabled
                    },
                    onManageSyncthingChange = { enabled ->
                        AppPreferences.setManageSyncthing(this@MainActivity, enabled)
                        manageSyncthingEnabledState = enabled
                    },
                    onManageTailscaleChange = { enabled ->
                        AppPreferences.setManageTailscale(this@MainActivity, enabled)
                        manageTailscaleEnabledState = enabled
                    },
                    onManageJamesDspChange = { enabled ->
                        AppPreferences.setManageJamesDsp(this@MainActivity, enabled)
                        manageJamesDspEnabledState = enabled
                    },
                    onManageBasicSyncChange = { enabled ->
                        AppPreferences.setManageBasicSync(this@MainActivity, enabled)
                        manageBasicSyncEnabledState = enabled
                    },
                    onManageRaOfflineProxyChange = { enabled ->
                        AppPreferences.setManageRaOfflineProxy(
                            this@MainActivity,
                            enabled
                        )
                        manageRaOfflineProxyEnabledState = enabled
                        if (managerEnabledState) {
                            refreshRunningService()
                        }
                    },
                    onPeriodicSyncWhileSleepingChange = { enabled ->
                        AppPreferences.setPeriodicSyncWhileSleeping(
                            this@MainActivity,
                            enabled
                        )
                        periodicSyncWhileSleepingState = enabled
                    },
                    onSyncThenStopOnSleepWakeChange = { enabled ->
                        AppPreferences.setSyncThenStopOnSleepWake(
                            this@MainActivity,
                            enabled
                        )
                        syncThenStopOnSleepWakeState = enabled
                    },
                    onSleepGraceChange = { value ->
                        AppPreferences.setSleepGraceMs(this@MainActivity, value)
                        sleepGraceMsState = value
                    },
                    onCustomDelayEnabledChange = { enabled ->
                        AppPreferences.setCustomDelayEnabled(this@MainActivity, enabled)
                        customDelayEnabledState = enabled
                    },
                    onCustomDelayChange = { value ->
                        AppPreferences.setCustomDelayMs(this@MainActivity, value)
                        customDelayMsState = value
                    },
                    onBatteryConditionEnabledChange = { enabled ->
                        AppPreferences.setBatteryConditionEnabled(
                            this@MainActivity,
                            enabled
                        )
                        batteryConditionEnabledState = enabled
                    },
                    onBatteryBelowPercentChange = { value ->
                        AppPreferences.setBatteryBelowPercent(
                            this@MainActivity,
                            value
                        )
                        batteryBelowPercentState = value.coerceIn(5, 95)
                    },
                    onNotChargingOnlyChange = { enabled ->
                        AppPreferences.setNotChargingOnly(
                            this@MainActivity,
                            enabled
                        )
                        notChargingOnlyState = enabled
                    },
                    onBatterySaverModeChange = { mode ->
                        AppPreferences.setBatterySaverMode(
                            this@MainActivity,
                            mode
                        )
                        batterySaverModeState =
                            AppPreferences.batterySaverMode(this@MainActivity)
                    },
                    onScheduleEnabledChange = { enabled ->
                        AppPreferences.setScheduleEnabled(
                            this@MainActivity,
                            enabled
                        )
                        scheduleEnabledState = enabled
                    },
                    onScheduleStartMinutesChange = { value ->
                        AppPreferences.setScheduleStartMinutes(
                            this@MainActivity,
                            value
                        )
                        scheduleStartMinutesState = value.coerceIn(0, 1439)
                    },
                    onScheduleEndMinutesChange = { value ->
                        AppPreferences.setScheduleEndMinutes(
                            this@MainActivity,
                            value
                        )
                        scheduleEndMinutesState = value.coerceIn(0, 1439)
                    },
                    onAutomaticUpdateChecksChange = { enabled ->
                        AppPreferences.setAutomaticUpdateChecks(
                            this@MainActivity,
                            enabled
                        )
                        automaticUpdateChecksState = enabled
                    },
                    onFinishSetupRequested = {
                        finishSetup()
                    },
                    onFinishAppRequested = {
                        finishAndRemoveTask()
                    },
                    onClosedLidAdminActiveRequested = {
                        isClosedLidAdminActive()
                    },
                    onClosedLidProtectionChangeRequested = { enabled ->
                        setClosedLidProtectionEnabled(enabled)
                        closedLidProtectionEnabledState =
                            AppPreferences.manageClosedLidProtection(this@MainActivity)
                    },
                    onDockDisconnectSleepsChange = { enabled ->
                        AppPreferences.setDockDisconnectSleeps(this@MainActivity, enabled)
                        dockDisconnectSleepsState = enabled
                    },
                    onClosedLidPowerSleepsChange = { enabled ->
                        AppPreferences.setClosedLidPowerSleeps(this@MainActivity, enabled)
                        closedLidPowerSleepsState = enabled
                    },
                    onCopyDiagnosticsRequested = {
                        copyDiagnostics()
                    },
                    onOpenExternalUrlRequested = { url ->
                        openReleaseUrl(url)
                    },
                    onOpenAppInfoRequested = {
                        openAppInfo()
                    },
                    onOpenBatteryOptimizationRequested = {
                        openBatteryOptimizationSettings()
                    },
                    onOpenRaOfflineProxySettingsRequested = {
                        if (
                            !RaOfflineProxyController
                                .openAppSettings(this@MainActivity)
                        ) {
                            Toast.makeText(
                                this@MainActivity,
                                "Unable to open RAOfflineProxy app settings",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onOpenUnusedAppRestrictionsRequested = {
                        openUnusedAppRestrictionsSettings()
                    },
                    onRequestUpdateNotificationPermissionRequested = {
                        requestUpdateNotificationPermission()
                    },
                    onInstallVerifiedUpdateRequested = { apkPath ->
                        installVerifiedUpdate(apkPath)
                    },
                    onCanScheduleExactAlarmsRequested = {
                        canScheduleExactAlarms()
                    },
                    onRequestExactAlarmAccessRequested = {
                        requestExactAlarmAccess()
                    },
                    onShowTimePickerRequested = { initialMinutes, onSelected ->
                        showTimePicker(initialMinutes, onSelected)
                    },
                    onRestoreSyncthingRequested = {
                        restoreSyncthingTransactionNow()
                    },
                    onRestoreJamesDspRequested = {
                        restoreJamesDspTransactionNow()
                    },
                    onRestoreBasicSyncRequested = {
                        restoreBasicSyncTransactionNow()
                    },
                    onUseSystemColorsChanged = { value ->
                        AppPreferences.setUseSystemColors(
                            this@MainActivity,
                            value
                        )
                        useSystemColors = value
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerHelperStateReceiver()
        registerBatterySaverStateReceiver()
        HelperController.requestState(this)
        refreshBatterySaverState()
    }

    override fun onResume() {
        super.onResume()

        pendingExternalNavigation = false

        if (pendingPackageInstallerReturn) {
            pendingPackageInstallerReturn = false
            installerReturnToken++
        }

        pendingUpdateInstallPath?.let { apkPath ->
            pendingUpdateInstallPath = null
            if (UpdateInstaller.canRequestPackageInstalls(this)) {
                launchVerifiedUpdateInstaller(apkPath)
            } else {
                Toast.makeText(
                    this,
                    "Install unknown apps permission was not enabled",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        if (pendingClosedLidAdminEnable) {
            pendingClosedLidAdminEnable = false
            val granted = isClosedLidAdminActive()
            AppPreferences.setManageClosedLidProtection(this, granted)
            if (!granted) {
                Toast.makeText(
                    this,
                    "Closed-lid protection permission was not enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            activityRefreshToken++
        }

        if (
            AppPreferences.manageClosedLidProtection(this) &&
            !isClosedLidAdminActive()
        ) {
            AppPreferences.setManageClosedLidProtection(this, false)
            activityRefreshToken++
        }

        if (pendingExactAlarmEnable) {
            pendingExactAlarmEnable = false
            val granted = canScheduleExactAlarms()
            if (granted) {
                AppPreferences.setCustomDelayEnabled(this, true)
                customDelayEnabledState = true
                AppPreferences.setSleepGraceMs(this, 0L)
                sleepGraceMsState = 0L
                Toast.makeText(
                    this,
                    "Precise custom delay enabled",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Precise timing permission is required for Custom delay",
                    Toast.LENGTH_LONG
                ).show()
            }
            activityRefreshToken++
        }

        ensureServiceRunning()
        refreshRunningService()
        refreshBackgroundReliabilityAsync()
        refreshDeviceCapabilitiesAsync()

        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        statusRefreshRunnable.run()
    }

    override fun onPause() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        if (!AppPreferences.manageBasicSync(this)) {
            BasicSyncController.stopStateObserver()
        }
        super.onPause()
    }

    override fun onStop() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)

        if (helperStateReceiverRegistered) {
            try {
                unregisterReceiver(helperStateReceiver)
            } catch (_: IllegalArgumentException) {
            }
            helperStateReceiverRegistered = false
        }

        if (batterySaverStateReceiverRegistered) {
            try {
                unregisterReceiver(batterySaverStateReceiver)
            } catch (_: IllegalArgumentException) {
            }
            batterySaverStateReceiverRegistered = false
        }
        super.onStop()
    }

    private fun registerBatterySaverStateReceiver() {
        if (batterySaverStateReceiverRegistered) return

        ContextCompat.registerReceiver(
            this,
            batterySaverStateReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        batterySaverStateReceiverRegistered = true
    }

    private fun registerHelperStateReceiver() {
        if (helperStateReceiverRegistered) return

        val filter = IntentFilter(HelperController.ACTION_STATE)
        ContextCompat.registerReceiver(
            this,
            helperStateReceiver,
            filter,
            HelperController.PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
        helperStateReceiverRegistered = true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        // Opening Android's Device Admin confirmation also causes the Activity
        // to lose focus. Do not treat that internal permission flow like the
        // user pressing Home, otherwise the SleepManager task is removed before
        // the confirmation screen can be shown.
        if (
            pendingClosedLidAdminEnable ||
            pendingExactAlarmEnable ||
            pendingExternalNavigation
        ) {
            return
        }

        // SleepManager's foreground service is independent from the Activity.
        // When the user genuinely leaves via Home / gesture navigation, remove
        // only the UI task. The automation service keeps running in background.
        if (AppPreferences.isEnabled(this)) {
            finishAndRemoveTask()
        }
    }

    private fun closedLidAdminComponent(): ComponentName =
        ClosedLidAdmin.component(this)

    internal fun isClosedLidAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(closedLidAdminComponent())
    }

    private fun refreshBackgroundReliabilityAfterClosedLidAdminRemoval(
        attempt: Int = 0
    ) {
        val adminStillActive = isClosedLidAdminActive()

        if (!adminStillActive || attempt >= 5) {
            refreshBackgroundReliabilityAsync()
            activityRefreshToken++
            return
        }

        statusRefreshHandler.postDelayed(
            {
                refreshBackgroundReliabilityAfterClosedLidAdminRemoval(
                    attempt = attempt + 1
                )
            },
            250L
        )
    }

    private fun requestClosedLidAdmin() {
        pendingClosedLidAdminEnable = true
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, closedLidAdminComponent())
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allows SleepManager to immediately return the device to sleep if it wakes while the lid is still closed."
            )
        }
        startActivity(intent)
    }

    internal fun setClosedLidProtectionEnabled(enabled: Boolean) {
        if (!enabled) {
            AppPreferences.setManageClosedLidProtection(this, false)
            refreshRunningService()

            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(closedLidAdminComponent())) {
                runCatching { dpm.removeActiveAdmin(closedLidAdminComponent()) }
                refreshBackgroundReliabilityAfterClosedLidAdminRemoval()
            } else {
                refreshBackgroundReliabilityAsync()
            }
            return
        }

        if (!LidMonitor.isSupported()) {
            Toast.makeText(
                this,
                "Compatible lid sensor not detected",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isClosedLidAdminActive()) {
            requestClosedLidAdmin()
            return
        }

        AppPreferences.setManageClosedLidProtection(this, true)
        refreshRunningService()
    }

    internal fun refreshRunningService() {
        if (!AppPreferences.isEnabled(this)) return

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
        } catch (t: Throwable) {
            Log.e("SleepManager", "Unable to refresh running service", t)
        }
    }

    internal fun finishSetup() {
        if (!AppPreferences.isEnabled(this)) {
            Toast.makeText(
                this,
                "Enable SleepManager first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AppPreferences.setSetupComplete(this, true)
        DiagnosticsStateStore.recordEvent(this, "Setup finished • background automation active")
        finishAndRemoveTask()
    }

    internal fun setManagerEnabled(enabled: Boolean) {
        if (!enabled) {
            AppPreferences.setEnabled(this, false)
            managerEnabledState = false

            val service = Intent(this, SleepManagerService::class.java)
                .setAction(SleepManagerService.ACTION_DISABLE_AND_RESTORE)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)

            SleepManagerTileService.requestRefresh(this)
            return
        }

        val helperNeeded =
            AppPreferences.manageWifi(this) || AppPreferences.manageBluetooth(this)

        if (helperNeeded && !HelperController.isInstalled(this)) {
            Toast.makeText(
                this,
                "Install the SleepManager compatibility helper first",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (
            AppPreferences.manageClosedLidProtection(this) &&
            (!LidMonitor.isSupported() || !isClosedLidAdminActive())
        ) {
            Toast.makeText(
                this,
                "Enable closed-lid protection permission first",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AppPreferences.setEnabled(this, true)
        managerEnabledState = true

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)

            DiagnosticsStateStore.recordEvent(this, "SleepManager enabled")
            SleepManagerTileService.requestRefresh(this)
        } catch (t: Throwable) {
            AppPreferences.setEnabled(this, false)
            managerEnabledState = false
            SleepManagerTileService.requestRefresh(this)
            Toast.makeText(
                this,
                "Unable to start: ${t.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    internal fun restoreSyncthingTransactionNow() {
        val change =
            SleepCycleStore.connectorChange(this, SyncthingConnector.id)
                ?: return

        val result = SyncthingConnector.wake(this, change.restoreToken)

        if (result.success) {
            SleepCycleStore.clearConnectorChange(this, SyncthingConnector.id)
        } else {
            DiagnosticsStateStore.recordEvent(this, "Syncthing restore pending")
        }
    }

    internal fun restoreJamesDspTransactionNow() {
        val change =
            SleepCycleStore.connectorChange(this, JamesDspConnector.id)
                ?: return

        val result = JamesDspConnector.wake(this, change.restoreToken)

        if (result.success) {
            SleepCycleStore.clearConnectorChange(this, JamesDspConnector.id)
        } else {
            DiagnosticsStateStore.recordEvent(this, "JamesDSP restore pending")
        }
    }

    internal fun restoreBasicSyncTransactionNow() {
        val change =
            SleepCycleStore.connectorChange(this, BasicSyncConnector.id)
                ?: return

        val result = BasicSyncConnector.wake(this, change.restoreToken)

        if (result.success) {
            SleepCycleStore.clearConnectorChange(this, BasicSyncConnector.id)
        } else {
            DiagnosticsStateStore.recordEvent(this, "BasicSync restore pending")
        }
    }

    private fun launchExternalActivity(
        intent: Intent,
        failureMessage: String? = null
    ) {
        pendingExternalNavigation = true
        runCatching {
            startActivity(intent)
        }.onFailure {
            pendingExternalNavigation = false
            failureMessage?.let { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    internal fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        val alarmManager =
            getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false

        return alarmManager.canScheduleExactAlarms()
    }

    internal fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        pendingExactAlarmEnable = true

        val intent =
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName")
            )

        runCatching {
            startActivity(intent)
        }.onFailure {
            pendingExactAlarmEnable = false
            Toast.makeText(
                this,
                "Open Alarms & reminders and allow SleepManager",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    internal fun showTimePicker(
        initialMinutes: Int,
        onSelected: (Int) -> Unit
    ) {
        val hour = initialMinutes / 60
        val minute = initialMinutes % 60

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                onSelected(selectedHour * 60 + selectedMinute)
            },
            hour,
            minute,
            true
        ).show()
    }

    internal fun openReleaseUrl(url: String) {
        launchExternalActivity(
            intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            failureMessage = "No app is available to open this link"
        )
    }

    internal fun openAppInfo() {
        launchExternalActivity(
            intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ),
            failureMessage = "Unable to open Android app info"
        )
    }

    internal fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(
                this,
                "Battery optimization is not available on this Android version",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val powerManager =
            getSystemService(Context.POWER_SERVICE) as? PowerManager
        val alreadyExempt =
            powerManager?.isIgnoringBatteryOptimizations(packageName) == true

        val intent =
            if (alreadyExempt) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            }

        launchExternalActivity(
            intent = intent,
            failureMessage = "Unable to open battery optimization settings"
        )
    }

    internal fun openUnusedAppRestrictionsSettings() {
        val intent =
            runCatching {
                IntentCompat.createManageUnusedAppRestrictionsIntent(
                    this,
                    packageName
                )
            }.getOrNull()

        if (intent == null) {
            openAppInfo()
            return
        }

        pendingExternalNavigation = true
        runCatching {
            unusedAppRestrictionsLauncher.launch(intent)
        }.onFailure {
            pendingExternalNavigation = false
            openAppInfo()
        }
    }

    internal fun installVerifiedUpdate(apkPath: String) {
        val apk = File(apkPath)
        if (!apk.isFile) {
            Toast.makeText(
                this,
                "Verified update APK is no longer available",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!UpdateInstaller.canRequestPackageInstalls(this)) {
            pendingUpdateInstallPath = apkPath
            launchExternalActivity(
                intent = UpdateInstaller.unknownSourcesIntent(this),
                failureMessage = "Unable to open Install unknown apps settings"
            )
            return
        }

        launchVerifiedUpdateInstaller(apkPath)
    }

    private fun launchVerifiedUpdateInstaller(apkPath: String) {
        val apk = File(apkPath)
        if (!apk.isFile) {
            Toast.makeText(
                this,
                "Verified update APK is no longer available",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        pendingPackageInstallerReturn = true
        launchExternalActivity(
            intent = UpdateInstaller.installIntent(this, apk),
            failureMessage = "Unable to open Android's package installer"
        )
    }

    private fun openProjectReleases() {
        openReleaseUrl("https://github.com/Darkaxt/SleepManager/releases")
    }

    internal fun requestUpdateNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            pendingExternalNavigation = true
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    internal fun copyDiagnostics() {
        val diagnostics = DiagnosticsBuilder.build(
            context = this,
            wifiState = currentWifiState,
            bluetoothState = currentBluetoothState,
            syncthingState = currentSyncthingState,
            tailscaleConnected = currentTailscaleConnected
        )
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("SleepManager log", diagnostics)
        )
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun ensureServiceRunning() {
        val enabled = AppPreferences.isEnabled(this)
        val running = SleepManagerService.running

        if (!enabled || running) {
            Log.d(
                "SleepManager",
                "ensureServiceRunning skipped: enabled=$enabled running=$running"
            )
            return
        }

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
            Log.i("SleepManager", "ensureServiceRunning requested service start")
        } catch (error: Throwable) {
            Log.e("SleepManager", "ensureServiceRunning failed", error)
        }
    }


    companion object {
        const val EXTRA_OPEN_UPDATES = "com.med.sleepmanager.extra.OPEN_UPDATES"
        private const val STATUS_REFRESH_INTERVAL_MS = 3000L
    }
}
