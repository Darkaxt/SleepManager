package com.med.sleepmanager.service

import androidx.core.content.ContextCompat
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import java.util.concurrent.Executors
import com.med.sleepmanager.MainActivity
import com.med.sleepmanager.R
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.device.DeviceControlStore
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.integration.TailscaleTransactionToken
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import com.med.sleepmanager.network.NetworkReadyGate
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.protection.ThorPowerButtonMonitor
import com.med.sleepmanager.rules.SleepConditionEvaluator
import com.med.sleepmanager.rules.SleepWakePolicy
import com.med.sleepmanager.sync.ManagedSyncProviders
import com.med.sleepmanager.sync.PeriodicAlarmDeviceDecision
import com.med.sleepmanager.sync.OwnedBasicSyncRestoreResult
import com.med.sleepmanager.sync.SyncMaintenancePolicy
import com.med.sleepmanager.sync.SyncMaintenanceRunner
import com.med.sleepmanager.sync.SyncMaintenanceScheduler
import com.med.sleepmanager.sync.SyncMaintenanceTrigger
import com.med.sleepmanager.sync.SyncStopOwnershipStore
import com.med.sleepmanager.sync.SyncTransitionStore
import com.med.sleepmanager.sync.SyncCompletionState
import com.med.sleepmanager.sync.basicSyncCompletionState

class SleepManagerService : Service() {
    companion object {
        private const val TAG = "SleepManager"
        private const val CHANNEL_ID = "sleep_manager"
        private const val NOTIFICATION_ID = 5217
        private const val NETWORK_READY_TIMEOUT_MS = 15000L
        private const val SYNCTHING_STOP_GRACE_MS = 1000L
        private const val SYNCTHING_UNVERIFIED_STOP_GRACE_MS = 2500L
        private const val SYNC_STOP_POLL_INTERVAL_MS = 250L
        private const val SYNC_STOP_TIMEOUT_MS = 5_000L
        private const val BASIC_SYNC_FRESH_STATE_TIMEOUT_MS = 1_500L
        private const val BASIC_SYNC_ACTIVE_FINISH_TIMEOUT_MS = 120_000L
        private const val BASIC_SYNC_ACTIVE_FINISH_POLL_MS = 500L
        private const val BASIC_SYNC_IDLE_STABILITY_MS = 3_000L
        private const val DEVICE_CONTROL_RESTORE_INTERVAL_MS = 500L
        private const val DEVICE_CONTROL_RESTORE_MAX_ATTEMPTS = 6
        private const val TAILSCALE_VERIFY_INTERVAL_MS = 500L
        private const val TAILSCALE_VERIFY_MAX_ATTEMPTS = 8
        private const val TAILSCALE_WAKE_RETRY_AT_ATTEMPT = 4
        private const val TAILSCALE_WAKE_MAX_ATTEMPTS = 12
        private const val SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS = 8_000L
        private const val OWNED_BASIC_SYNC_RESTORE_INTERVAL_MS = 250L
        private const val OWNED_BASIC_SYNC_RESTORE_MAX_ATTEMPTS = 8
        private const val THOR_CLOSE_GUARD_DELAY_MS = 1500L
        private const val THOR_SCREEN_ON_RECHECK_DELAY_MS = 500L
        private const val THOR_DOCK_DISCONNECT_DEBOUNCE_MS = 500L
        private const val THOR_LOCK_COOLDOWN_MS = 900L
        const val ACTION_DISABLE_AND_RESTORE =
            "com.med.sleepmanager.action.DISABLE_AND_RESTORE"
        const val ACTION_SLEEP_DELAY_ELAPSED =
            "com.med.sleepmanager.action.SLEEP_DELAY_ELAPSED"
        const val ACTION_PERIODIC_SYNC =
            "com.med.sleepmanager.action.PERIODIC_SYNC"
        private const val SLEEP_DELAY_REQUEST_CODE = 5218

        @Volatile
        var running: Boolean = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var helperResultReceiverRegistered = false

    private var lastWakeWifiManaged = false
    private var lastWakeWifiChanged = false
    private var lastWakeWifiAttempted = false
    private var lastWakeWifiToggleSuccess = true
    private var lastWakeWifiAirplaneMode = false
    private var lastWakeBluetoothManaged = false
    private var lastWakeBluetoothChanged = false
    private var sleepActionsApplied = false
    private var sleepSkippedByConditions = false
    private var waitingForActiveBasicSync = false
    private var activeBasicSyncWaitStartedAt = 0L
    private var activeBasicSyncSyncedSince = 0L
    private var basicSyncFreshProbeGeneration = 0L

    private val activeBasicSyncFinishRunnable = Runnable {
        pollActiveBasicSyncBeforeSleep()
    }

    private var pendingSleepWifi = false
    private var pendingSleepBluetooth = false
    private var pendingSleepSyncthing = false
    private var pendingSleepBasicSync = false
    private var pendingSleepPostStopActions = false
    private var sleepStopWaitStartedAtElapsed = 0L
    private var lastBasicSyncStopStateRequestAtElapsed = 0L
    private var syncthingStopProbeInFlight = false
    private var syncthingStopConfirmed = false
    private var sleepStopWaitGeneration = 0L
    private val syncStopProbeExecutor = Executors.newSingleThreadExecutor()
    private var sleepGracePending = false
    private var sleepTransitionWakeLock: PowerManager.WakeLock? = null
    private var networkReadyGate: NetworkReadyGate? = null
    private var pendingNetworkRestoreAfterHelper = false
    private var tailscaleSleepVerifyAttempts = 0
    private var tailscaleWakeVerifyAttempts = 0
    private var tailscaleVerificationNeedsWakeRestore = false
    private var disableRestoreRequested = false
    private var initialScreenStateApplied = false
    private var syncMaintenanceRunner: SyncMaintenanceRunner? = null
    private var pendingWakeTransitionSync = false
    private var ownedBasicSyncRestorePending = false
    private var ownedBasicSyncRestoreAttempts = 0
    private var deviceControlRestorePending = false
    private var deviceControlRestoreAttempts = 0
    private var disableForceStopRequested = false
    private var disableRestoreInitializing = false

    private val ownedBasicSyncRestoreRunnable = Runnable {
        maybeRestoreOwnedBasicSyncState()
    }

    private val ownedDeviceControlRestoreRunnable = Runnable {
        continueOwnedDeviceControlRestoreForDisable()
    }

    private fun syncRunner(): SyncMaintenanceRunner =
        syncMaintenanceRunner
            ?: SyncMaintenanceRunner(this, handler).also {
                syncMaintenanceRunner = it
            }

    private fun cancelSyncMaintenance(restoreSleepWifi: Boolean) {
        syncMaintenanceRunner?.cancel(restoreSleepWifi)
    }

    private fun refreshBasicSyncObserver() {
        if (AppPreferences.manageBasicSync(this)) {
            BasicSyncController.startStateObserver(this)
        } else {
            BasicSyncController.stopStateObserver()
        }
    }

    private fun shouldRestoreOwnedBasicSyncState(): Boolean =
        SyncStopOwnershipStore.hasBasicSyncOwnership(this) &&
            (
                !AppPreferences.isEnabled(this) ||
                    !AppPreferences.manageBasicSync(this) ||
                    !AppPreferences.syncThenStopOnSleepWake(this)
            )

    private fun maybeRestoreOwnedBasicSyncState() {
        handler.removeCallbacks(ownedBasicSyncRestoreRunnable)

        if (!shouldRestoreOwnedBasicSyncState()) {
            ownedBasicSyncRestorePending = false
            ownedBasicSyncRestoreAttempts = 0
            return
        }

        ownedBasicSyncRestorePending = true

        // The integration may have just been disabled, so keep a temporary
        // state observer alive long enough to verify that SleepManager still
        // owns the stopped state before restoring anything.
        BasicSyncController.startStateObserver(this)

        when (SyncStopOwnershipStore.restoreBasicSyncIfOwned(this)) {
            OwnedBasicSyncRestoreResult.PENDING,
            OwnedBasicSyncRestoreResult.FAILED -> {
                ownedBasicSyncRestoreAttempts++
                if (
                    ownedBasicSyncRestoreAttempts <
                    OWNED_BASIC_SYNC_RESTORE_MAX_ATTEMPTS
                ) {
                    handler.postDelayed(
                        ownedBasicSyncRestoreRunnable,
                        OWNED_BASIC_SYNC_RESTORE_INTERVAL_MS
                    )
                    return
                }

                ownedBasicSyncRestorePending = false
                ownedBasicSyncRestoreAttempts = 0
                Log.w(
                    TAG,
                    "BasicSync original state restore remains pending"
                )
                if (disableRestoreRequested) {
                    AppPreferences.recordEvent(
                        this,
                        "Disable → BasicSync original state restore pending"
                    )
                }
            }

            OwnedBasicSyncRestoreResult.NOT_OWNED,
            OwnedBasicSyncRestoreResult.RESTORED,
            OwnedBasicSyncRestoreResult.RELINQUISHED -> {
                ownedBasicSyncRestorePending = false
                ownedBasicSyncRestoreAttempts = 0
            }
        }

        if (!AppPreferences.manageBasicSync(this)) {
            BasicSyncController.stopStateObserver()
        }

        finishDisableRestoreIfRequested()
    }

    @Volatile
    private var thorLidClosed = false

    private var thorLidMonitor: ThorLidMonitor? = null
    private var thorPowerButtonMonitor: ThorPowerButtonMonitor? = null
    private var thorDisplayListenerRegistered = false
    private var thorExternalDisplayConnected = false
    private var thorExternalDisplayActive = false
    private var thorClosedAwakeOverride = false
    private var thorClosedSleepIntent = false
    private var thorSleepRequestPending = false
    private var lastThorLockAt = 0L

    private val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    }

    private val thorAdminComponent by lazy {
        ComponentName(this, ThorDeviceAdminReceiver::class.java)
    }

    private val sleepGraceRunnable = Runnable {
        sleepGracePending = false
        Log.i(TAG, "Sleep delay elapsed -> applying sleep actions")
        performFreshSleepActions()
    }

    private val sleepRadioRunnable = Runnable {
        continueSleepRadioAfterSyncStop()
    }

    private val tailscaleSleepVerifyRunnable = Runnable {
        verifyTailscaleSleepDisconnect()
    }

    private val tailscaleWakeVerifyRunnable = Runnable {
        verifyTailscaleWakeReconnect()
    }

    private val thorCloseGuardRunnable = Runnable {
        maybeReturnThorToSleep("close guard")
    }

    private val thorScreenOnRecheckRunnable = Runnable {
        maybeReturnThorToSleep("closed-lid wake")
    }

    private val thorDockDisconnectRunnable = Runnable {
        handleThorDockDisconnect()
    }

    private val thorDisplayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshThorExternalDisplayState("display added")
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshThorExternalDisplayState("display removed")
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshThorExternalDisplayState("display changed")
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_POWER_CONNECTED -> BatterySleepStore.noteCharging(
                    this@SleepManagerService
                )
            }
        }
    }

    private val helperResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HelperController.ACTION_RESULT) return

            val phase = intent.getStringExtra(HelperController.EXTRA_PHASE) ?: return
            val wifiManaged = intent.getBooleanExtra(HelperController.EXTRA_WIFI_MANAGED, false)
            val wifiChanged = intent.getBooleanExtra(HelperController.EXTRA_WIFI_CHANGED, false)
            val wifiAttempted =
                intent.getBooleanExtra(HelperController.EXTRA_WIFI_ATTEMPTED, false)
            val wifiAction =
                intent.getStringExtra(HelperController.EXTRA_WIFI_ACTION) ?: "NONE"
            val wifiToggleSuccess =
                intent.getBooleanExtra(HelperController.EXTRA_WIFI_TOGGLE_SUCCESS, true)
            val airplaneMode =
                intent.getBooleanExtra(HelperController.EXTRA_AIRPLANE_MODE, false)
            val bluetoothManaged = intent.getBooleanExtra(HelperController.EXTRA_BLUETOOTH_MANAGED, false)
            val bluetoothChanged = intent.getBooleanExtra(HelperController.EXTRA_BLUETOOTH_CHANGED, false)
            val restoreSuccess = intent.getBooleanExtra(
                HelperController.EXTRA_RESTORE_SUCCESS,
                true
            )
            val helperStatus =
                intent.getStringExtra(HelperController.EXTRA_STATUS)
                    ?: HelperController.STATUS_OK

            if (
                wifiManaged &&
                helperStatus != HelperController.STATUS_ALREADY_SLEEPING
            ) {
                AppPreferences.recordWifiToggleDiagnostic(
                    context = this@SleepManagerService,
                    phase = phase,
                    action = wifiAction,
                    attempted = wifiAttempted,
                    success = wifiToggleSuccess,
                    airplaneMode = airplaneMode
                )
            }

            when (phase) {
                HelperController.PHASE_SLEEP -> {
                    if (SleepCycleStore.isActive(this@SleepManagerService)) {
                        SleepCycleStore.markHelperSleepRequested(this@SleepManagerService)
                    }
                    releaseSleepTransitionWakeLock()
                    AppPreferences.recordEvent(
                        this@SleepManagerService,
                        buildSleepSummary(
                            wifiManaged = wifiManaged,
                            wifiChanged = wifiChanged,
                            wifiAttempted = wifiAttempted,
                            wifiToggleSuccess = wifiToggleSuccess,
                            wifiAirplaneMode = airplaneMode,
                            bluetoothManaged = bluetoothManaged,
                            bluetoothChanged = bluetoothChanged,
                            syncthing = AppPreferences.manageSyncthing(this@SleepManagerService)
                        )
                    )
                }

                HelperController.PHASE_WAKE -> {
                    lastWakeWifiManaged = wifiManaged
                    lastWakeWifiChanged = wifiChanged
                    lastWakeWifiAttempted = wifiAttempted
                    lastWakeWifiToggleSuccess = wifiToggleSuccess
                    lastWakeWifiAirplaneMode = airplaneMode
                    lastWakeBluetoothManaged = bluetoothManaged
                    lastWakeBluetoothChanged = bluetoothChanged

                    if (!restoreSuccess) {
                        pendingWakeTransitionSync = false
                        Log.w(
                            TAG,
                            "Helper restore failed status=$helperStatus; preserving sleep transaction"
                        )

                        val wifiRestoreFailed =
                            wifiManaged && wifiAttempted && !wifiToggleSuccess
                        val wifiFailureText =
                            if (wifiRestoreFailed) {
                                "Wi-Fi toggle failed" +
                                    if (airplaneMode) {
                                        " · Airplane mode is enabled"
                                    } else {
                                        ""
                                    }
                            } else {
                                null
                            }

                        SleepCycleStore.markRestoreProblem(
                            this@SleepManagerService,
                            when {
                                helperStatus == HelperController.STATUS_NO_ACTIVE_CYCLE ->
                                    "Compatibility Helper no longer has the pending sleep state."
                                wifiFailureText != null ->
                                    "$wifiFailureText. Wi-Fi restore is still pending."
                                else ->
                                    "Compatibility Helper could not restore Wi-Fi / Bluetooth."
                            }
                        )
                        AppPreferences.recordEvent(
                            this@SleepManagerService,
                            if (wifiFailureText != null) {
                                val prefix =
                                    if (disableRestoreRequested) "Disable" else "Wake"
                                "$prefix → $wifiFailureText"
                            } else if (disableRestoreRequested) {
                                "Disable → Helper restore pending"
                            } else {
                                "Wake → Helper restore pending"
                            }
                        )
                        finishDisableRestoreIfRequested(forceStop = true)
                        return
                    }

                    if (SleepCycleStore.isActive(this@SleepManagerService)) {
                        SleepCycleStore.markHelperRestored(this@SleepManagerService)
                    }

                    val restoreNetworkConnectors = pendingNetworkRestoreAfterHelper
                    pendingNetworkRestoreAfterHelper = false

                    if (restoreNetworkConnectors && hasPendingNetworkConnectorRestore()) {
                        Log.i(
                            TAG,
                            "Helper wake completed -> starting network-ready wait"
                        )
                        waitForNetworkAndRestorePendingConnectors()
                        maybeStartWakeTransitionSync()
                    } else {
                        if (
                            !disableRestoreRequested &&
                            !SleepCycleStore.hasPendingConnectorChanges(
                                this@SleepManagerService
                            )
                        ) {
                            AppPreferences.recordEvent(
                                this@SleepManagerService,
                                buildWakeSummary(
                                    wifiManaged = wifiManaged,
                                    wifiChanged = wifiChanged,
                                    wifiAttempted = wifiAttempted,
                                    wifiToggleSuccess = wifiToggleSuccess,
                                    wifiAirplaneMode = airplaneMode,
                                    bluetoothManaged = bluetoothManaged,
                                    bluetoothChanged = bluetoothChanged,
                                    syncthing = false
                                )
                            )
                        }

                        SleepCycleStore.completeIfRestored(this@SleepManagerService)
                        finishDisableRestoreIfRequested()
                        maybeStartWakeTransitionSync()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        createNotificationChannel()
        startForegroundCompat()
        registerScreenReceiver()
        registerHelperResultReceiver()
        refreshBasicSyncObserver()
        maybeRestoreOwnedBasicSyncState()
        refreshThorLidMonitor()
        recoverOwnedDeviceControls()
        recoverInterruptedSleepWifiMaintenance()
        Log.i(TAG, "Service started")
    }

    private fun recoverOwnedDeviceControls() {
        val batterySaverOwned =
            DeviceControlStore.batterySaver(this)

        if (batterySaverOwned.owned) {
            if (isRealWakeNow()) {
                restoreOwnedBatterySaver("Recovery")
            } else if (
                isEffectivelySleepingNow() &&
                !batterySaverOwned.previous &&
                AppPreferences.manageBatterySaver(this) &&
                !DeviceControlController.batterySaverEnabled(this)
            ) {
                if (
                    DeviceControlController
                        .setBatterySaverEnabled(true)
                ) {
                    Log.i(
                        TAG,
                        "Recovery -> Battery Saver sleep state re-applied"
                    )
                }
            }
        }

        val chargingOwned =
            DeviceControlStore.chargingSeparation(this)
        if (chargingOwned.owned) {
            val shouldRemainOff =
                AppPreferences.manageChargingSeparationWithLid(this) &&
                    ThorLidMonitor.isSupported() &&
                    thorLidClosed &&
                    !hasExternalDisplayConnected()

            if (shouldRemainOff) {
                if (
                    DeviceControlController
                        .chargingSeparationState(this) != false
                ) {
                    if (
                        DeviceControlController
                            .setChargingSeparationEnabled(false)
                    ) {
                        Log.i(
                            TAG,
                            "Recovery -> Charging Separation closed-lid state re-applied"
                        )
                    }
                }
            } else {
                restoreOwnedChargingSeparation("Recovery")
            }
        }
    }

    private fun recoverInterruptedSleepWifiMaintenance() {
        if (!isEffectivelySleepingNow()) return

        val cycle = SleepCycleStore.current(this)
        if (
            cycle.active &&
            cycle.helperExpected &&
            cycle.wifiManaged &&
            !cycle.helperRestored &&
            HelperController.isInstalled(this)
        ) {
            // Safe and idempotent: Helper 1.1+ accepts this only when it still
            // owns the Wi-Fi change from the active sleep cycle.
            HelperController.setTemporaryWifi(this, enabled = false)
            Log.i(
                TAG,
                "Service recovery -> requested sleep Wi-Fi state after interrupted maintenance"
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null && AppPreferences.isEnabled(this)) {
            val activeCycle = SleepCycleStore.isActive(this)
            val message =
                if (activeCycle) {
                    "Foreground service restarted with an active sleep transaction"
                } else {
                    "Foreground service restarted by Android"
                }
            DeviceControlStore.recordServiceRecovery(this, message)
            AppPreferences.recordEvent(
                this,
                if (activeCycle) {
                    "Recovery → service restarted · transaction resumed"
                } else {
                    "Recovery → service restarted"
                }
            )
            Log.i(TAG, message)
        }

        if (intent?.action == ACTION_DISABLE_AND_RESTORE) {
            beginDisableAndRestore()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_PERIODIC_SYNC) {
            handlePeriodicSyncAlarm()
            return START_STICKY
        }

        if (intent?.action == ACTION_SLEEP_DELAY_ELAPSED) {
            cancelSleepDelay()

            if (!AppPreferences.isEnabled(this)) {
                stopSelf()
                return START_NOT_STICKY
            }

            if (isEffectivelySleepingNow() && !SleepCycleStore.isActive(this)) {
                Log.i(TAG, "Custom sleep delay elapsed -> evaluating advanced rules")
                performFreshSleepActions()
            }
            return START_STICKY
        }

        if (!AppPreferences.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!receiverRegistered) registerScreenReceiver()
        if (!helperResultReceiverRegistered) registerHelperResultReceiver()
        refreshBasicSyncObserver()
        maybeRestoreOwnedBasicSyncState()
        refreshThorLidMonitor()

        if (!initialScreenStateApplied) {
            initialScreenStateApplied = true
            applyCurrentScreenState()
        }

        return START_STICKY
    }

    private fun startOwnedDeviceControlRestoreForDisable() {
        handler.removeCallbacks(ownedDeviceControlRestoreRunnable)
        deviceControlRestoreAttempts = 0

        val batteryRestored =
            restoreOwnedBatterySaver("Disable")
        val chargingRestored =
            restoreOwnedChargingSeparation("Disable")

        deviceControlRestorePending =
            !(batteryRestored && chargingRestored)

        if (deviceControlRestorePending) {
            deviceControlRestoreAttempts = 1
            handler.postDelayed(
                ownedDeviceControlRestoreRunnable,
                DEVICE_CONTROL_RESTORE_INTERVAL_MS
            )
        }
    }

    private fun continueOwnedDeviceControlRestoreForDisable() {
        if (!disableRestoreRequested) {
            deviceControlRestorePending = false
            deviceControlRestoreAttempts = 0
            return
        }

        val batteryRestored =
            restoreOwnedBatterySaver("Disable retry")
        val chargingRestored =
            restoreOwnedChargingSeparation("Disable retry")

        if (batteryRestored && chargingRestored) {
            deviceControlRestorePending = false
            deviceControlRestoreAttempts = 0
            finishDisableRestoreIfRequested()
            return
        }

        deviceControlRestoreAttempts++
        if (
            deviceControlRestoreAttempts <
            DEVICE_CONTROL_RESTORE_MAX_ATTEMPTS
        ) {
            handler.postDelayed(
                ownedDeviceControlRestoreRunnable,
                DEVICE_CONTROL_RESTORE_INTERVAL_MS
            )
            return
        }

        deviceControlRestorePending = false
        deviceControlRestoreAttempts = 0
        SleepCycleStore.markRestoreProblem(
            this,
            "A system setting changed by SleepManager could not be restored."
        )
        AppPreferences.recordEvent(
            this,
            "Disable → system setting restore pending"
        )
        finishDisableRestoreIfRequested(forceStop = true)
    }

    private fun beginDisableAndRestore() {
        disableRestoreRequested = true
        disableRestoreInitializing = true
        disableForceStopRequested = false
        pendingWakeTransitionSync = false
        SyncTransitionStore.clear(this)

        cancelNetworkReadyWait()

        SyncMaintenanceScheduler.cancel(this)
        cancelSyncMaintenance(restoreSleepWifi = false)
        cancelSleepDelay()
        clearPendingSleepStopWait()
        sleepSkippedByConditions = false
        releaseSleepTransitionWakeLock()

        startOwnedDeviceControlRestoreForDisable()
        maybeRestoreOwnedBasicSyncState()
        prepareTailscaleVerificationForWake()
        restorePendingJamesDsp()
        restorePendingBasicSync()

        var cycle = SleepCycleStore.current(this)
        if (
            cycle.active &&
            cycle.helperExpected &&
            !cycle.helperSleepRequested &&
            !cycle.helperRestored
        ) {
            SleepCycleStore.markHelperRestored(this)
            cycle = SleepCycleStore.current(this)
        }

        val helperRestoreNeeded =
            cycle.active &&
                cycle.helperExpected &&
                cycle.helperSleepRequested &&
                !cycle.helperRestored
        val networkRestoreNeeded = hasPendingNetworkConnectorRestore()

        pendingNetworkRestoreAfterHelper =
            helperRestoreNeeded && networkRestoreNeeded

        if (helperRestoreNeeded) {
            val sent = HelperController.restoreNow(this, cycle.cycleId)
            if (sent) {
                disableRestoreInitializing = false
                Log.i(TAG, "Disable requested -> waiting for Helper restore result")
                return
            }

            disableRestoreInitializing = false
            pendingNetworkRestoreAfterHelper = false
            Log.w(TAG, "Disable requested -> Helper restore could not be sent")
            SleepCycleStore.markRestoreProblem(
                this,
                "Compatibility Helper is unavailable, so Wi-Fi / Bluetooth cannot be restored."
            )
            AppPreferences.recordEvent(this, "Disable → Helper restore pending")
            finishDisableRestoreIfRequested(forceStop = true)
            return
        }

        if (networkRestoreNeeded) {
            disableRestoreInitializing = false
            waitForNetworkAndRestorePendingConnectors()
            return
        }

        disableRestoreInitializing = false
        SleepCycleStore.completeIfRestored(this)
        finishDisableRestoreIfRequested()
    }

    private fun finishDisableRestoreIfRequested(forceStop: Boolean = false) {
        if (!disableRestoreRequested) return

        if (forceStop) {
            disableForceStopRequested = true
        }

        if (
            disableRestoreInitializing ||
            ownedBasicSyncRestorePending ||
            deviceControlRestorePending
        ) {
            return
        }

        val shouldForceStop = disableForceStopRequested
        if (!shouldForceStop && SleepCycleStore.isActive(this)) {
            return
        }

        disableRestoreRequested = false
        disableForceStopRequested = false
        pendingNetworkRestoreAfterHelper = false

        if (!shouldForceStop) {
            AppPreferences.recordEvent(this, "SleepManager disabled")
        }

        stopSelf()
    }

    private fun onScreenOff() {
        thorSleepRequestPending = false
        pendingWakeTransitionSync = false

        if (
            SyncMaintenancePolicy.shouldCancelMaintenanceOnScreenOff(
                syncMaintenanceRunner?.activeTrigger
            )
        ) {
            cancelSyncMaintenance(restoreSleepWifi = false)
        }

        BatterySleepStore.beginSession(this)
        cancelNetworkReadyWait()
        pendingNetworkRestoreAfterHelper = false
        handler.removeCallbacks(thorScreenOnRecheckRunnable)

        val existingCycle = SleepCycleStore.current(this)
        if (sleepActionsApplied || existingCycle.active) {
            sleepActionsApplied = true

            val helperSleepPending =
                existingCycle.active &&
                    existingCycle.helperExpected &&
                    !existingCycle.helperSleepRequested
            val batterySaverWillEnable =
                batterySaverWillEnableForSleep()
            val syncthingStopPending =
                SleepCycleStore.hasConnectorChange(
                    this,
                    SyncthingConnector.id
                )
            val basicSyncStopPending =
                SleepCycleStore.hasConnectorChange(
                    this,
                    BasicSyncConnector.id
                )
            val tailscaleVerificationPending =
                isTailscaleSleepVerificationPending()

            val waitForManagedStops =
                SleepWakePolicy
                    .shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                        wifiManaged = existingCycle.wifiManaged,
                        helperAvailable = helperSleepPending,
                        batterySaverWillEnable = batterySaverWillEnable,
                        syncthingStopRequested = syncthingStopPending,
                        basicSyncStopRequested = basicSyncStopPending
                    )
            val waitForTailscale =
                SleepWakePolicy
                    .shouldWaitForTailscaleBeforeDisruptiveSleepAction(
                        helperAvailable = helperSleepPending,
                        batterySaverWillEnable = batterySaverWillEnable,
                        tailscaleVerificationPending =
                            tailscaleVerificationPending
                    )

            val postStopRecoveryNeeded =
                existingCycle.active &&
                    (
                        waitForManagedStops ||
                            waitForTailscale ||
                            helperSleepPending ||
                            batterySaverWillEnable
                    )

            if (postStopRecoveryNeeded) {
                val elapsed =
                    (System.currentTimeMillis() - existingCycle.startedAt)
                        .coerceAtLeast(0L)

                pendingSleepPostStopActions = true
                pendingSleepWifi =
                    helperSleepPending && existingCycle.wifiManaged
                pendingSleepBluetooth =
                    helperSleepPending && existingCycle.bluetoothManaged
                pendingSleepSyncthing =
                    waitForManagedStops && syncthingStopPending
                pendingSleepBasicSync =
                    waitForManagedStops && basicSyncStopPending

                initializeSleepStopWait(elapsed)

                if (pendingSleepBasicSync) {
                    BasicSyncController.requestStateBroadcast(this)
                }

                if (waitForTailscale) {
                    Log.i(
                        TAG,
                        "Recovered pending sleep transaction; resuming Tailscale verification before disruptive sleep actions"
                    )
                    scheduleTailscaleSleepVerification(resetAttempts = true)
                } else {
                    scheduleSleepRadioStopCheck(0L)
                    Log.i(
                        TAG,
                        "Recovered pending sleep transaction; resuming post-STOP sleep actions"
                    )
                }
                return
            }

            if (tailscaleVerificationPending) {
                Log.i(TAG, "Recovered pending Tailscale disconnect verification")
                scheduleTailscaleSleepVerification(resetAttempts = true)
                return
            }

            Log.i(
                TAG,
                "Screen OFF -> active sleep transaction already exists; skipping duplicate"
            )
            return
        }
        if (sleepGracePending) {
            Log.i(TAG, "Screen OFF -> sleep delay already pending")
            return
        }

        val sleepDelayMs = AppPreferences.effectiveSleepDelayMs(this)
        if (sleepDelayMs > 0L) {
            scheduleSleepDelay(sleepDelayMs)
            Log.i(TAG, "Screen OFF -> sleep delay scheduled for ${sleepDelayMs}ms")
            return
        }

        performFreshSleepActions()
    }

    private fun performFreshSleepActions() {
        sleepActionsApplied = true

        handler.removeCallbacks(sleepRadioRunnable)
        cancelActiveBasicSyncWait()
        releaseSleepTransitionWakeLock()

        val conditions = SleepConditionEvaluator.evaluate(this)
        if (!conditions.met) {
            SyncMaintenanceScheduler.cancel(this)
            SyncTransitionStore.clear(this)
            sleepSkippedByConditions = true
            val reason = conditions.failedReasons.joinToString(" · ")
            Log.i(TAG, "Sleep actions skipped -> $reason")
            AppPreferences.recordEvent(
                this,
                "Sleep skipped → $reason"
            )
            return
        }

        sleepSkippedByConditions = false

        val transitionSyncRequested = syncThenStopAvailable()
        if (
            !transitionSyncRequested &&
            shouldProbeBasicSyncBeforeNormalSleep()
        ) {
            probeBasicSyncBeforeNormalSleep()
            return
        }

        continueFreshSleepActions(transitionSyncRequested)
    }

    private fun shouldProbeBasicSyncBeforeNormalSleep(): Boolean =
        AppPreferences.manageBasicSync(this) &&
            BasicSyncController.isInstalled(this) &&
            BasicSyncController.supportsSyncCounters(this)

    private fun probeBasicSyncBeforeNormalSleep() {
        BasicSyncController.startStateObserver(this)
        val generation = ++basicSyncFreshProbeGeneration

        acquireSleepTransitionWakeLock(
            BASIC_SYNC_FRESH_STATE_TIMEOUT_MS +
                SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
        )

        val scheduled = runCatching {
            syncStopProbeExecutor.execute {
                val state =
                    runCatching {
                        BasicSyncController.requestState(
                            this,
                            BASIC_SYNC_FRESH_STATE_TIMEOUT_MS
                        )
                    }.getOrNull()

                handler.post {
                    if (generation != basicSyncFreshProbeGeneration) {
                        return@post
                    }

                    if (
                        !isEffectivelySleepingNow() ||
                        !AppPreferences.isEnabled(this) ||
                        !sleepActionsApplied
                    ) {
                        releaseSleepTransitionWakeLock()
                        return@post
                    }

                    if (
                        basicSyncCompletionState(state) ==
                        SyncCompletionState.SYNCING
                    ) {
                        startActiveBasicSyncWait()
                    } else {
                        releaseSleepTransitionWakeLock()
                        continueFreshSleepActions(
                            transitionSyncRequested = false
                        )
                    }
                }
            }
            true
        }.getOrDefault(false)

        if (!scheduled) {
            releaseSleepTransitionWakeLock()
            continueFreshSleepActions(
                transitionSyncRequested = false
            )
        }
    }

    private fun startActiveBasicSyncWait() {
        waitingForActiveBasicSync = true
        activeBasicSyncWaitStartedAt = SystemClock.elapsedRealtime()
        activeBasicSyncSyncedSince = 0L
        acquireSleepTransitionWakeLock(
            BASIC_SYNC_ACTIVE_FINISH_TIMEOUT_MS +
                SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
        )
        BasicSyncController.requestStateBroadcast(this)
        handler.post(activeBasicSyncFinishRunnable)
        Log.i(
            TAG,
            "BasicSync is already syncing; waiting for it to finish before sleep actions"
        )
        AppPreferences.recordEvent(
            this,
            "Sleep → waiting for active BasicSync sync to finish"
        )
    }

    private fun pollActiveBasicSyncBeforeSleep() {
        if (!waitingForActiveBasicSync) return

        if (
            isRealWakeNow() ||
            !AppPreferences.isEnabled(this)
        ) {
            cancelActiveBasicSyncWait()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - activeBasicSyncWaitStartedAt
        val completion =
            basicSyncCompletionState(
                BasicSyncController.lastObservedState()
            )

        when (completion) {
            SyncCompletionState.SYNCING -> {
                activeBasicSyncSyncedSince = 0L
            }

            SyncCompletionState.SYNCED -> {
                if (activeBasicSyncSyncedSince == 0L) {
                    activeBasicSyncSyncedSince = now
                } else if (
                    now - activeBasicSyncSyncedSince >=
                    BASIC_SYNC_IDLE_STABILITY_MS
                ) {
                    cancelActiveBasicSyncWait()
                    AppPreferences.recordEvent(
                        this,
                        "Sleep → active BasicSync sync finished"
                    )
                    continueFreshSleepActions(
                        transitionSyncRequested = false
                    )
                    return
                }
            }

            SyncCompletionState.UNKNOWN -> {
                activeBasicSyncSyncedSince = 0L
            }
        }

        if (elapsed >= BASIC_SYNC_ACTIVE_FINISH_TIMEOUT_MS) {
            cancelActiveBasicSyncWait()
            Log.w(
                TAG,
                "Timed out waiting for active BasicSync sync; continuing with STOP"
            )
            AppPreferences.recordEvent(
                this,
                "Sleep → BasicSync active sync wait timed out"
            )
            continueFreshSleepActions(
                transitionSyncRequested = false
            )
            return
        }

        BasicSyncController.requestStateBroadcast(this)
        handler.postDelayed(
            activeBasicSyncFinishRunnable,
            BASIC_SYNC_ACTIVE_FINISH_POLL_MS
        )
    }

    private fun cancelActiveBasicSyncWait() {
        handler.removeCallbacks(activeBasicSyncFinishRunnable)
        basicSyncFreshProbeGeneration++
        waitingForActiveBasicSync = false
        activeBasicSyncWaitStartedAt = 0L
        activeBasicSyncSyncedSince = 0L
    }

    private fun continueFreshSleepActions(
        transitionSyncRequested: Boolean
    ) {
        val transitionOwnershipReady =
            !transitionSyncRequested ||
                !AppPreferences.manageBasicSync(this) ||
                SyncStopOwnershipStore.captureBasicSyncIfNeeded(this)
        val transitionSyncAvailable =
            transitionSyncRequested && transitionOwnershipReady

        if (transitionSyncRequested && !transitionOwnershipReady) {
            Log.w(
                TAG,
                "Sync then stop skipped for this sleep: BasicSync original state is not known yet"
            )
        }

        if (transitionSyncAvailable) {
            SyncTransitionStore.armWakeSync(this)
        } else {
            SyncTransitionStore.clear(this)
        }

        if (transitionSyncAvailable) {
            val started =
                syncRunner().start(
                    SyncMaintenanceTrigger.BEFORE_SLEEP
                ) { snapshot ->
                    AppPreferences.recordEvent(
                        this,
                        "Pre-sleep sync → ${snapshot.outcome}"
                    )

                    val stillSleeping = isEffectivelySleepingNow()

                    if (
                        stillSleeping &&
                        AppPreferences.isEnabled(this) &&
                        sleepActionsApplied
                    ) {
                        applyFreshSleepActions(
                            keepBasicSyncStopped = true
                        )
                    } else {
                        Log.i(
                            TAG,
                            "Pre-sleep sync finished after wake; sleep actions not continued"
                        )
                    }
                }

            if (started) {
                Log.i(TAG, "Pre-sleep sync maintenance started")
                return
            }
        }

        applyFreshSleepActions(
            keepBasicSyncStopped = transitionSyncAvailable
        )
    }

    private fun applyFreshSleepActions(
        keepBasicSyncStopped: Boolean
    ) {
        val wifi = AppPreferences.manageWifi(this)
        val bluetooth = AppPreferences.manageBluetooth(this)
        val syncthing = AppPreferences.manageSyncthing(this)
        val tailscale =
            AppPreferences.manageTailscale(this) &&
                TailscaleConnector.isInstalled(this)
        val jamesDsp =
            AppPreferences.manageJamesDsp(this) &&
                JamesDspConnector.isInstalled(this)
        val basicSync =
            AppPreferences.manageBasicSync(this) &&
                BasicSyncConnector.isInstalled(this)
        if (basicSync) {
            BasicSyncController.startStateObserver(this)
        }
        val radiosManaged = wifi || bluetooth
        val helperAvailable = radiosManaged && HelperController.isInstalled(this)
        val batterySaverWillEnable =
            batterySaverWillEnableForSleep()

        val cycle = SleepCycleStore.begin(
            context = this,
            helperExpected = helperAvailable,
            wifiManaged = wifi,
            bluetoothManaged = bluetooth
        )
        Log.i(
            TAG,
            "Screen OFF -> cycle=${cycle.cycleId} wifi=$wifi bluetooth=$bluetooth " +
                "syncthing=$syncthing tailscale=$tailscale jamesDsp=$jamesDsp " +
                "basicSync=$basicSync keepBasicSyncStopped=$keepBasicSyncStopped"
        )

        // Syncthing always keeps its normal STOP/FOLLOW ownership. Advanced
        // completion-aware maintenance is BasicSync-only.
        val syncthingResult =
            if (syncthing) {
                SyncthingConnector.sleep(this)
            } else {
                null
            }

        if (syncthingResult?.changed == true) {
            SleepCycleStore.recordConnectorChange(
                this,
                SyncthingConnector.id,
                syncthingResult.restoreToken
            )
        }

        val tailscaleResult = if (tailscale) {
            TailscaleConnector.sleep(this)
        } else {
            null
        }

        if (
            tailscaleResult?.attempted == true &&
            TailscaleTransactionToken.disconnectTarget(tailscaleResult.restoreToken) != null
        ) {
            SleepCycleStore.recordConnectorChange(
                this,
                TailscaleConnector.id,
                tailscaleResult.restoreToken
            )
        }

        val jamesDspResult = if (jamesDsp) {
            JamesDspConnector.sleep(this)
        } else {
            null
        }

        if (jamesDspResult?.changed == true) {
            SleepCycleStore.recordConnectorChange(
                this,
                JamesDspConnector.id,
                jamesDspResult.restoreToken
            )
        }

        val basicSyncStopSent =
            if (basicSync && keepBasicSyncStopped) {
                BasicSyncController.sendStop(this)
            } else {
                false
            }

        val basicSyncResult =
            if (basicSync && !keepBasicSyncStopped) {
                BasicSyncConnector.sleep(this)
            } else {
                null
            }

        if (basicSyncResult?.changed == true) {
            SleepCycleStore.recordConnectorChange(
                this,
                BasicSyncConnector.id,
                basicSyncResult.restoreToken
            )
            val restoreTarget =
                BasicSyncConnector.restoreTargetName(
                    basicSyncResult.restoreToken
                )
            AppPreferences.recordEvent(
                this,
                "Sleep → BasicSync STOP sent · restore $restoreTarget"
            )
            Log.i(
                TAG,
                "BasicSync STOP sent; restore target=$restoreTarget"
            )
        } else if (basicSyncResult?.detail != null) {
            Log.i(TAG, "BasicSync unchanged: ${basicSyncResult.detail}")
        }

        if (basicSync && keepBasicSyncStopped) {
            SleepCycleStore.clearConnectorChange(
                this,
                BasicSyncConnector.id
            )
            Log.i(
                TAG,
                "BasicSync STOP sent=$basicSyncStopSent; advanced sync ownership retained"
            )
        }

        val syncthingStopRequested = syncthingResult?.changed == true
        val basicSyncStopRequested =
            if (keepBasicSyncStopped) {
                basicSyncStopSent
            } else {
                basicSyncResult?.changed == true
            }
        val tailscaleVerificationPending = isTailscaleSleepVerificationPending()
        val waitForManagedStops =
            SleepWakePolicy
                .shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
                    wifiManaged = wifi,
                    helperAvailable = helperAvailable,
                    batterySaverWillEnable = batterySaverWillEnable,
                    syncthingStopRequested = syncthingStopRequested,
                    basicSyncStopRequested = basicSyncStopRequested
                )
        val waitForTailscale =
            SleepWakePolicy
                .shouldWaitForTailscaleBeforeDisruptiveSleepAction(
                    helperAvailable = helperAvailable,
                    batterySaverWillEnable = batterySaverWillEnable,
                    tailscaleVerificationPending =
                        tailscaleVerificationPending
                )

        if (waitForManagedStops || waitForTailscale) {
            pendingSleepPostStopActions = true
            pendingSleepWifi = wifi
            pendingSleepBluetooth = bluetooth
            pendingSleepSyncthing =
                waitForManagedStops && syncthingStopRequested
            pendingSleepBasicSync =
                waitForManagedStops && basicSyncStopRequested
            initializeSleepStopWait()

            if (pendingSleepBasicSync) {
                BasicSyncController.requestStateBroadcast(this)
            }

            if (waitForTailscale) {
                Log.i(
                    TAG,
                    "Waiting for Tailscale disconnect verification before disruptive sleep actions"
                )
                scheduleTailscaleSleepVerification(resetAttempts = true)
            } else {
                scheduleSleepRadioStopCheck()
            }
        } else {
            if (tailscaleVerificationPending) {
                scheduleTailscaleSleepVerification(resetAttempts = true)
            }

            applySleepConnectivity(
                wifi = wifi,
                bluetooth = bluetooth,
                syncthing = syncthingStopRequested
            )
        }

        if (
            !helperAvailable &&
            !SleepCycleStore.hasPendingConnectorChanges(this)
        ) {
            SleepCycleStore.clear(this)
        }

        SyncMaintenanceScheduler.scheduleNext(this)
    }

    private fun syncThenStopAvailable(): Boolean =
        AppPreferences.syncThenStopOnSleepWake(this) &&
            ManagedSyncProviders.completionReady(this)

    private fun batterySaverWillEnableForSleep(): Boolean =
        AppPreferences.manageBatterySaver(this) &&
            DeviceControlController.supportsBatterySaverControl(this) &&
            !DeviceControlStore.batterySaver(this).owned &&
            !DeviceControlController.batterySaverEnabled(this)

    private fun initializeSleepStopWait(
        elapsedBeforeRecoveryMs: Long = 0L
    ) {
        val elapsed = elapsedBeforeRecoveryMs.coerceIn(0L, SYNC_STOP_TIMEOUT_MS)
        sleepStopWaitGeneration++
        sleepStopWaitStartedAtElapsed =
            SystemClock.elapsedRealtime() - elapsed
        lastBasicSyncStopStateRequestAtElapsed = 0L
        syncthingStopProbeInFlight = false
        syncthingStopConfirmed = !pendingSleepSyncthing

        acquireSleepTransitionWakeLock(
            SYNC_STOP_TIMEOUT_MS +
                SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
        )

        Log.i(
            TAG,
            "Waiting before disruptive sleep actions: Syncthing=$pendingSleepSyncthing " +
                "BasicSync=$pendingSleepBasicSync timeout=${SYNC_STOP_TIMEOUT_MS}ms"
        )
    }

    private fun scheduleSleepRadioStopCheck(
        delayMs: Long = SYNC_STOP_POLL_INTERVAL_MS
    ) {
        handler.removeCallbacks(sleepRadioRunnable)
        handler.postDelayed(
            sleepRadioRunnable,
            delayMs.coerceAtLeast(0L)
        )
    }

    private fun continueSleepRadioAfterSyncStop() {
        if (!pendingSleepPostStopActions) {
            releaseSleepTransitionWakeLock()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (sleepStopWaitStartedAtElapsed == 0L) {
            sleepStopWaitStartedAtElapsed = now
        }
        val elapsed = now - sleepStopWaitStartedAtElapsed
        val timedOut = elapsed >= SYNC_STOP_TIMEOUT_MS

        val basicSyncStopped =
            when {
                !pendingSleepBasicSync -> true
                BasicSyncController.supportsStateApi(this) ->
                    BasicSyncController.isConfirmedStopped()
                else ->
                    // BasicSync versions without the state API cannot confirm
                    // STOP. Preserve the old short grace rather than blocking
                    // the sleep transition for the full timeout.
                    elapsed >= SYNCTHING_STOP_GRACE_MS
            }

        if (
            pendingSleepBasicSync &&
            !basicSyncStopped &&
            !timedOut &&
            now - lastBasicSyncStopStateRequestAtElapsed >= 1_000L
        ) {
            lastBasicSyncStopStateRequestAtElapsed = now
            BasicSyncController.requestStateBroadcast(this)
        }

        if (
            pendingSleepSyncthing &&
            !syncthingStopConfirmed &&
            !timedOut
        ) {
            if (elapsed < SYNCTHING_STOP_GRACE_MS) {
                scheduleSleepRadioStopCheck(
                    (SYNCTHING_STOP_GRACE_MS - elapsed)
                        .coerceAtMost(SYNC_STOP_POLL_INTERVAL_MS)
                )
                return
            }

            if (!syncthingStopProbeInFlight) {
                syncthingStopProbeInFlight = true
                val generation = sleepStopWaitGeneration
                val restoreToken =
                    SleepCycleStore.connectorChange(
                        this,
                        SyncthingConnector.id
                    )?.restoreToken

                syncStopProbeExecutor.execute {
                    val verification =
                        runCatching {
                            SyncthingConnector.verifyStopAfterGrace(
                                restoreToken
                            )
                        }.getOrNull()

                    handler.post {
                        if (generation != sleepStopWaitGeneration) {
                            return@post
                        }

                        syncthingStopProbeInFlight = false
                        if (
                            pendingSleepSyncthing &&
                            SleepCycleStore.isActive(this)
                        ) {
                            val elapsedNow =
                                SystemClock.elapsedRealtime() -
                                    sleepStopWaitStartedAtElapsed

                            when (verification) {
                                true -> {
                                    syncthingStopConfirmed = true
                                    Log.i(
                                        TAG,
                                        "Syncthing STOP confirmed before disruptive sleep actions"
                                    )
                                }

                                false -> {
                                    Log.i(
                                        TAG,
                                        "Syncthing still running; delaying disruptive sleep actions while STOP completes"
                                    )
                                }

                                null -> {
                                    if (
                                        elapsedNow >=
                                        SYNCTHING_UNVERIFIED_STOP_GRACE_MS
                                    ) {
                                        // No supported state API exists for this
                                        // target. Keep a conservative fixed grace
                                        // instead of treating an unreachable local
                                        // health endpoint as proof that STOP finished.
                                        syncthingStopConfirmed = true
                                        Log.i(
                                            TAG,
                                            "Syncthing STOP state unavailable; fallback grace elapsed before disruptive sleep actions"
                                        )
                                    }
                                }
                            }

                            val nextDelay =
                                if (
                                    verification == null &&
                                    !syncthingStopConfirmed
                                ) {
                                    (
                                        SYNCTHING_UNVERIFIED_STOP_GRACE_MS -
                                            elapsedNow
                                    ).coerceAtLeast(0L)
                                } else {
                                    0L
                                }

                            scheduleSleepRadioStopCheck(nextDelay)
                        }
                    }
                }
            }
            return
        }

        if (
            !timedOut &&
            (!basicSyncStopped ||
                (pendingSleepSyncthing && !syncthingStopConfirmed))
        ) {
            scheduleSleepRadioStopCheck()
            return
        }

        if (pendingSleepBasicSync && !basicSyncStopped) {
            Log.w(
                TAG,
                "BasicSync STOP not confirmed before disruptive sleep actions timeout"
            )
            AppPreferences.recordEvent(
                this,
                "Sleep → BasicSync STOP not confirmed before disruptive sleep actions"
            )
        }

        if (pendingSleepSyncthing && !syncthingStopConfirmed) {
            if (
                SleepCycleStore.hasConnectorChange(
                    this,
                    SyncthingConnector.id
                )
            ) {
                SleepCycleStore.clearConnectorChange(
                    this,
                    SyncthingConnector.id
                )
            }
            Log.w(
                TAG,
                "Syncthing STOP not confirmed before disruptive sleep actions timeout"
            )
            AppPreferences.recordEvent(
                this,
                "Sleep → Syncthing STOP not confirmed before disruptive sleep actions"
            )
        }

        val wifi = pendingSleepWifi
        val bluetooth = pendingSleepBluetooth
        val syncthing = pendingSleepSyncthing

        clearPendingSleepStopWait()

        Log.i(
            TAG,
            "Sync STOP gate complete -> applying sleep device controls and radios"
        )
        applySleepConnectivity(
            wifi = wifi,
            bluetooth = bluetooth,
            syncthing = syncthing
        )
    }

    private fun clearPendingSleepStopWait() {
        handler.removeCallbacks(sleepRadioRunnable)
        sleepStopWaitGeneration++
        pendingSleepWifi = false
        pendingSleepBluetooth = false
        pendingSleepSyncthing = false
        pendingSleepBasicSync = false
        pendingSleepPostStopActions = false
        sleepStopWaitStartedAtElapsed = 0L
        lastBasicSyncStopStateRequestAtElapsed = 0L
        syncthingStopProbeInFlight = false
        syncthingStopConfirmed = false
    }

    private fun applySleepConnectivity(
        wifi: Boolean,
        bluetooth: Boolean,
        syncthing: Boolean
    ) {
        applyBatterySaverForSleep()

        val helperSent = if (wifi || bluetooth) {
            HelperController.sendSleep(this, wifi, bluetooth, SleepCycleStore.current(this).cycleId)
        } else {
            false
        }

        if (helperSent) {
            SleepCycleStore.markHelperSleepRequested(this)
        } else {
            if (SleepCycleStore.isActive(this)) {
                SleepCycleStore.markHelperRestored(this)
            }
            releaseSleepTransitionWakeLock()
            AppPreferences.recordEvent(
                this,
                buildSleepSummary(
                    wifiManaged = false,
                    wifiChanged = false,
                    bluetoothManaged = false,
                    bluetoothChanged = false,
                    syncthing = syncthing
                )
            )
        }
    }

    private fun applyBatterySaverForSleep() {
        if (
            !AppPreferences.manageBatterySaver(this) ||
            !DeviceControlController.supportsBatterySaverControl(this)
        ) {
            return
        }

        if (DeviceControlStore.batterySaver(this).owned) {
            return
        }

        val previous = DeviceControlController.batterySaverEnabled(this)
        if (previous) {
            return
        }

        // Persist ownership before the privileged call. If the process dies
        // immediately after the toggle, wake recovery still knows what to undo.
        DeviceControlStore.takeBatterySaverOwnership(
            this,
            previous = false
        )

        if (DeviceControlController.setBatterySaverEnabled(true)) {
            Log.i(TAG, "Battery Saver enabled for sleep")
            AppPreferences.recordEvent(
                this,
                "Sleep → Battery Saver enabled"
            )
        } else {
            DeviceControlStore.clearBatterySaverOwnership(this)
            Log.w(TAG, "Unable to enable Battery Saver for sleep")
        }
    }

    private fun restoreOwnedBatterySaver(reason: String): Boolean {
        val owned = DeviceControlStore.batterySaver(this)
        if (!owned.owned) return true

        val current = DeviceControlController.batterySaverEnabled(this)
        if (current == owned.previous) {
            DeviceControlStore.clearBatterySaverOwnership(this)
            return true
        }

        val restored =
            DeviceControlController.setBatterySaverEnabled(
                owned.previous
            )
        if (restored) {
            DeviceControlStore.clearBatterySaverOwnership(this)
            Log.i(TAG, "Battery Saver restored after $reason")
            AppPreferences.recordEvent(
                this,
                "$reason → Battery Saver restored"
            )
        } else {
            Log.w(TAG, "Battery Saver restore failed after $reason")
            SleepCycleStore.markRestoreProblem(
                this,
                "Battery Saver restore is still pending."
            )
        }
        return restored
    }

    private fun temporarilyRestoreBatterySaverForMaintenance(): Boolean {
        val owned = DeviceControlStore.batterySaver(this)
        if (!owned.owned || owned.previous) return false
        if (!DeviceControlController.batterySaverEnabled(this)) return false

        val restored =
            DeviceControlController.setBatterySaverEnabled(false)
        if (restored) {
            Log.i(TAG, "Battery Saver temporarily restored for periodic sync")
        }
        return restored
    }

    private fun reapplyBatterySaverAfterMaintenance() {
        val owned = DeviceControlStore.batterySaver(this)
        if (!owned.owned || owned.previous) return

        if (isRealWakeNow()) return

        if (!DeviceControlController.batterySaverEnabled(this)) {
            if (DeviceControlController.setBatterySaverEnabled(true)) {
                Log.i(TAG, "Battery Saver re-enabled after periodic sync")
            } else {
                Log.w(TAG, "Unable to re-enable Battery Saver after periodic sync")
            }
        }
    }

    private fun shouldMonitorLid(): Boolean =
        ThorLidMonitor.isSupported() &&
            (
                AppPreferences.manageThorProtection(this) ||
                    (
                        AppPreferences.manageChargingSeparationWithLid(this) &&
                            DeviceControlController
                                .supportsChargingSeparationControl(this)
                    )
            )

    private fun applyClosedLidChargingSeparation(reason: String) {
        if (
            !AppPreferences.manageChargingSeparationWithLid(this) ||
            !ThorLidMonitor.isSupported() ||
            !DeviceControlController.supportsChargingSeparationControl(this) ||
            !thorLidClosed ||
            hasExternalDisplayConnected()
        ) {
            restoreOwnedChargingSeparation(reason)
            return
        }

        val existingOwnership =
            DeviceControlStore.chargingSeparation(this)
        if (existingOwnership.owned) {
            val current =
                DeviceControlController.chargingSeparationState(this)
            if (current == false) {
                return
            }

            if (
                existingOwnership.previous &&
                DeviceControlController
                    .setChargingSeparationEnabled(false)
            ) {
                Log.i(
                    TAG,
                    "Charging Separation sleep state re-applied ($reason)"
                )
            } else {
                Log.w(
                    TAG,
                    "Charging Separation ownership exists but OFF state could not be confirmed ($reason)"
                )
            }
            return
        }

        val previous =
            DeviceControlController.chargingSeparationState(this)
                ?: return
        if (!previous) return

        DeviceControlStore.takeChargingSeparationOwnership(
            this,
            previous = true
        )

        if (DeviceControlController.setChargingSeparationEnabled(false)) {
            Log.i(
                TAG,
                "Charging Separation disabled while lid is closed ($reason)"
            )
            AppPreferences.recordEvent(
                this,
                "Lid closed → Charging Separation disabled"
            )
        } else {
            DeviceControlStore.clearChargingSeparationOwnership(this)
            Log.w(TAG, "Unable to disable Charging Separation")
        }
    }

    private fun restoreOwnedChargingSeparation(reason: String): Boolean {
        val owned = DeviceControlStore.chargingSeparation(this)
        if (!owned.owned) return true

        val current =
            DeviceControlController.chargingSeparationState(this)
        if (current == owned.previous) {
            DeviceControlStore.clearChargingSeparationOwnership(this)
            return true
        }

        val restored =
            DeviceControlController.setChargingSeparationEnabled(
                owned.previous
            )
        if (restored) {
            DeviceControlStore.clearChargingSeparationOwnership(this)
            Log.i(TAG, "Charging Separation restored ($reason)")
            AppPreferences.recordEvent(
                this,
                "$reason → Charging Separation restored"
            )
        } else {
            Log.w(TAG, "Charging Separation restore failed ($reason)")
        }
        return restored
    }

    private fun isTailscaleSleepVerificationPending(): Boolean =
        TailscaleTransactionToken.disconnectTarget(
            SleepCycleStore.connectorChange(this, TailscaleConnector.id)?.restoreToken
        ) != null

    private fun prepareTailscaleVerificationForWake() {
        if (!isTailscaleSleepVerificationPending()) return

        tailscaleVerificationNeedsWakeRestore = true
        scheduleTailscaleSleepVerification(resetAttempts = true)
        Log.i(TAG, "Tailscale disconnect verification continuing during wake")
    }

    private fun scheduleTailscaleSleepVerification(resetAttempts: Boolean) {
        handler.removeCallbacks(tailscaleSleepVerifyRunnable)
        if (resetAttempts) {
            tailscaleSleepVerifyAttempts = 0
        }

        acquireSleepTransitionWakeLock(
            TAILSCALE_VERIFY_INTERVAL_MS *
                (TAILSCALE_VERIFY_MAX_ATTEMPTS + 2)
        )
        handler.postDelayed(
            tailscaleSleepVerifyRunnable,
            TAILSCALE_VERIFY_INTERVAL_MS
        )
    }

    private fun verifyTailscaleSleepDisconnect() {
        val change =
            SleepCycleStore.connectorChange(this, TailscaleConnector.id)
                ?: return releaseSleepTransitionWakeLock()

        val targetPackage =
            TailscaleTransactionToken.disconnectTarget(change.restoreToken)
        if (targetPackage == null) {
            releaseSleepTransitionWakeLock()
            return
        }

        if (!TailscaleController.isConnected(this)) {
            SleepCycleStore.recordConnectorChange(
                this,
                TailscaleConnector.id,
                TailscaleTransactionToken.restore(targetPackage)
            )
            Log.i(TAG, "Tailscale disconnect verified")
            AppPreferences.recordEvent(
                this,
                "Sleep → Tailscale disconnected"
            )
            continueAfterTailscaleSleepVerification()
            return
        }

        tailscaleSleepVerifyAttempts++
        if (tailscaleSleepVerifyAttempts < TAILSCALE_VERIFY_MAX_ATTEMPTS) {
            handler.postDelayed(
                tailscaleSleepVerifyRunnable,
                TAILSCALE_VERIFY_INTERVAL_MS
            )
            return
        }

        SleepCycleStore.clearConnectorChange(this, TailscaleConnector.id)
        Log.i(
            TAG,
            "Tailscale disconnect not verified; no restore will be scheduled"
        )
        AppPreferences.recordEvent(
            this,
            "Sleep → Tailscale unchanged"
        )
        continueAfterTailscaleSleepVerification()
    }

    private fun continueAfterTailscaleSleepVerification() {
        handler.removeCallbacks(tailscaleSleepVerifyRunnable)
        tailscaleSleepVerifyAttempts = 0
        releaseSleepTransitionWakeLock()

        val realWake = isRealWakeNow()

        if (realWake || tailscaleVerificationNeedsWakeRestore) {
            pendingSleepWifi = false
            pendingSleepBluetooth = false
            pendingSleepSyncthing = false
            pendingSleepBasicSync = false
            pendingSleepPostStopActions = false

            val shouldRestoreNow =
                tailscaleVerificationNeedsWakeRestore &&
                    hasPendingNetworkConnectorRestore()
            tailscaleVerificationNeedsWakeRestore = false

            if (shouldRestoreNow) {
                waitForNetworkAndRestorePendingConnectors()
            } else {
                SleepCycleStore.completeIfRestored(this)
                finishDisableRestoreIfRequested()
            }
            return
        }

        if (pendingSleepPostStopActions) {
            scheduleSleepRadioStopCheck(0L)
        } else {
            SleepCycleStore.completeIfRestored(this)
        }
    }

    private fun cancelTailscaleVerification() {
        handler.removeCallbacks(tailscaleSleepVerifyRunnable)
        handler.removeCallbacks(tailscaleWakeVerifyRunnable)
        tailscaleSleepVerifyAttempts = 0
        tailscaleWakeVerifyAttempts = 0
        tailscaleVerificationNeedsWakeRestore = false
    }

    private fun restorePendingJamesDsp() {
        val change =
            SleepCycleStore.connectorChange(this, JamesDspConnector.id)
                ?: return

        val wakeResult =
            JamesDspConnector.wake(this, change.restoreToken)

        if (wakeResult.success) {
            SleepCycleStore.clearConnectorChange(this, JamesDspConnector.id)
            if (!disableRestoreRequested) {
                AppPreferences.recordEvent(
                    this,
                    "Wake → JamesDSP restored"
                )
            }
            Log.i(TAG, "JamesDSP power ON sent")
        } else {
            SleepCycleStore.markRestoreProblem(
                this,
                "JamesDSP restore is still pending: ${wakeResult.detail}."
            )
            AppPreferences.recordEvent(
                this,
                if (disableRestoreRequested) {
                    "Disable → JamesDSP restore pending"
                } else {
                    "Wake → JamesDSP restore pending"
                }
            )
            Log.w(TAG, "JamesDSP restore failed; preserving transaction")
        }
    }

    private fun restorePendingBasicSync() {
        val change =
            SleepCycleStore.connectorChange(this, BasicSyncConnector.id)
                ?: return

        val wakeResult =
            BasicSyncConnector.wake(this, change.restoreToken)

        if (wakeResult.success) {
            val restoreTarget =
                BasicSyncConnector.restoreTargetName(change.restoreToken)
            SleepCycleStore.clearConnectorChange(this, BasicSyncConnector.id)
            if (!disableRestoreRequested) {
                AppPreferences.recordEvent(
                    this,
                    "Wake → BasicSync restored · $restoreTarget"
                )
            }
            Log.i(TAG, "BasicSync restore sent: $restoreTarget")
        } else {
            SleepCycleStore.markRestoreProblem(
                this,
                "BasicSync restore is still pending: ${wakeResult.detail}."
            )
            AppPreferences.recordEvent(
                this,
                if (disableRestoreRequested) {
                    "Disable → BasicSync restore pending"
                } else {
                    "Wake → BasicSync restore pending"
                }
            )
            Log.w(TAG, "BasicSync restore failed; preserving transaction")
        }
    }

    private fun hasPendingNetworkConnectorRestore(): Boolean {
        val syncthingPending =
            SleepCycleStore.hasConnectorChange(this, SyncthingConnector.id)
        val tailscalePending =
            TailscaleTransactionToken.restoreTarget(
                SleepCycleStore.connectorChange(this, TailscaleConnector.id)?.restoreToken
            ) != null
        return syncthingPending || tailscalePending
    }

    private fun scheduleTailscaleWakeVerification() {
        handler.removeCallbacks(tailscaleWakeVerifyRunnable)
        tailscaleWakeVerifyAttempts = 0
        handler.postDelayed(
            tailscaleWakeVerifyRunnable,
            TAILSCALE_VERIFY_INTERVAL_MS
        )
    }

    private fun verifyTailscaleWakeReconnect() {
        val change =
            SleepCycleStore.connectorChange(this, TailscaleConnector.id)
                ?: return

        val targetPackage = TailscaleTransactionToken.restoreTarget(change.restoreToken)
        if (targetPackage == null) {
            return
        }

        if (TailscaleController.isConnected(this, targetPackage)) {
            SleepCycleStore.clearConnectorChange(this, TailscaleConnector.id)
            Log.i(TAG, "Tailscale reconnect verified")

            if (!disableRestoreRequested) {
                AppPreferences.recordEvent(
                    this,
                    "Wake → Tailscale restored"
                )
            }

            val complete = SleepCycleStore.completeIfRestored(this)
            if (disableRestoreRequested && !complete) {
                finishDisableRestoreIfRequested(forceStop = true)
            } else {
                finishDisableRestoreIfRequested()
            }
            return
        }

        tailscaleWakeVerifyAttempts++

        if (
            tailscaleWakeVerifyAttempts ==
            TAILSCALE_WAKE_RETRY_AT_ATTEMPT
        ) {
            val retrySent = TailscaleController.sendConnect(this, targetPackage)
            Log.i(
                TAG,
                "Tailscale reconnect still pending -> CONNECT retry sent=$retrySent"
            )
        }

        if (tailscaleWakeVerifyAttempts < TAILSCALE_WAKE_MAX_ATTEMPTS) {
            handler.postDelayed(
                tailscaleWakeVerifyRunnable,
                TAILSCALE_VERIFY_INTERVAL_MS
            )
            return
        }

        Log.w(TAG, "Tailscale reconnect not verified; restore remains pending")
        AppPreferences.recordEvent(
            this,
            if (disableRestoreRequested) {
                "Disable → Tailscale restore pending"
            } else {
                "Wake → Tailscale restore pending"
            }
        )
        finishDisableRestoreIfRequested(forceStop = disableRestoreRequested)
    }

    private fun handlePeriodicSyncAlarm() {
        val powerManager =
            getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = powerManager?.isInteractive == true
        val thorClosedLidWakeSuppressed = isThorFalseWakeNow()

        when (
            SyncMaintenancePolicy.periodicAlarmDeviceDecision(
                interactive = interactive,
                thorClosedLidWakeSuppressed = thorClosedLidWakeSuppressed
            )
        ) {
            PeriodicAlarmDeviceDecision.RETRY_AFTER_THOR_FALSE_WAKE -> {
                SyncMaintenanceScheduler.scheduleThorFalseWakeRetry(this)
                Log.i(
                    TAG,
                    "Periodic sync deferred: transient closed-lid Thor wake"
                )
                return
            }

            PeriodicAlarmDeviceDecision.CANCEL_AWAKE -> {
                SyncMaintenanceScheduler.cancel(this)
                Log.i(TAG, "Periodic sync ignored: device is genuinely awake")
                return
            }

            PeriodicAlarmDeviceDecision.RUN -> Unit
        }

        if (!SyncMaintenanceScheduler.canArm(this)) {
            SyncMaintenanceScheduler.cancel(this)
            Log.i(TAG, "Periodic sync ignored: feature unavailable")
            return
        }

        val conditions = SleepConditionEvaluator.evaluate(this)
        if (!conditions.met) {
            val reason = conditions.failedReasons.joinToString(" · ")
            AppPreferences.recordEvent(
                this,
                "Periodic sync skipped → $reason"
            )
            Log.i(
                TAG,
                "Periodic sync skipped by Advanced sleep conditions -> $reason"
            )
            SyncMaintenanceScheduler.scheduleNext(this)
            return
        }

        if (syncMaintenanceRunner?.active == true) {
            Log.i(TAG, "Periodic sync ignored: maintenance already active")
            SyncMaintenanceScheduler.scheduleNext(this)
            return
        }

        val batterySaverTemporarilyRestored =
            temporarilyRestoreBatterySaverForMaintenance()

        val started =
            syncRunner().start(
                SyncMaintenanceTrigger.PERIODIC_SLEEP
            ) { snapshot ->
                AppPreferences.recordEvent(
                    this,
                    "Periodic sync → ${snapshot.outcome}"
                )

                if (batterySaverTemporarilyRestored) {
                    reapplyBatterySaverAfterMaintenance()
                }

                val stillSleeping = isEffectivelySleepingNow()

                if (
                    stillSleeping &&
                    AppPreferences.periodicSyncWhileSleeping(this)
                ) {
                    SyncMaintenanceScheduler.scheduleNext(this)
                } else {
                    SyncMaintenanceScheduler.cancel(this)
                }
            }

        if (!started) {
            if (batterySaverTemporarilyRestored) {
                reapplyBatterySaverAfterMaintenance()
            }
            SyncMaintenanceScheduler.scheduleNext(this)
        }
    }

    private fun scheduleSleepDelay(delayMs: Long) {
        cancelSleepDelay()
        sleepGracePending = true

        if (delayMs <= 10_000L) {
            acquireSleepTransitionWakeLock(
                delayMs + SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
            )
            handler.postDelayed(sleepGraceRunnable, delayMs)
            return
        }

        val alarmManager =
            getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

        val triggerAt =
            SystemClock.elapsedRealtime() + delayMs

        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                sleepDelayPendingIntent()
            )
            Log.i(TAG, "Custom sleep delay scheduled as exact alarm")
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                sleepDelayPendingIntent()
            )
            Log.w(
                TAG,
                "Exact alarm access unavailable; custom sleep delay may be deferred"
            )
        }
    }

    private fun cancelSleepDelay() {
        handler.removeCallbacks(sleepGraceRunnable)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(sleepDelayPendingIntent())
        alarmManager?.cancel(legacySleepDelayPendingIntent())
        sleepGracePending = false
        releaseSleepTransitionWakeLock()
    }

    private fun sleepDelayPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            SLEEP_DELAY_REQUEST_CODE,
            Intent(this, SleepDelayReceiver::class.java)
                .setAction(ACTION_SLEEP_DELAY_ELAPSED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun legacySleepDelayPendingIntent(): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            SLEEP_DELAY_REQUEST_CODE,
            Intent(this, SleepManagerService::class.java)
                .setAction(ACTION_SLEEP_DELAY_ELAPSED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun acquireSleepTransitionWakeLock(
        timeoutMs: Long = SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
    ) {
        releaseSleepTransitionWakeLock()

        val powerManager =
            getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return

        sleepTransitionWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:sleep-transition"
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }

        Log.i(TAG, "Sleep transition wakelock acquired")
    }

    private fun releaseSleepTransitionWakeLock() {
        val wakeLock = sleepTransitionWakeLock ?: return

        if (wakeLock.isHeld) {
            runCatching { wakeLock.release() }
        }
        sleepTransitionWakeLock = null
    }

    private fun onScreenOn() {
        val falseWake = isThorFalseWakeNow()
        val wakeDecision =
            SleepWakePolicy.onScreenOn(
                thorProtectionEnabled = falseWake,
                lidClosed = falseWake,
                sleepDelayPending = sleepGracePending
            )

        if (wakeDecision.suppressWake) {
            Log.i(
                TAG,
                "Screen ON while lid is closed -> false wake suppressed; sleep work preserved"
            )
            handler.removeCallbacks(thorScreenOnRecheckRunnable)
            handler.postDelayed(
                thorScreenOnRecheckRunnable,
                THOR_SCREEN_ON_RECHECK_DELAY_MS
            )
            return
        }

        // Only a real wake may cancel sleep-side waits or restoration gates.
        cancelNetworkReadyWait()
        cancelActiveBasicSyncWait()

        restoreOwnedBatterySaver("Wake")

        SyncMaintenanceScheduler.cancel(this)
        cancelSyncMaintenance(restoreSleepWifi = false)

        val wakeSyncWasArmed =
            SyncTransitionStore.isWakeSyncArmed(this)
        pendingWakeTransitionSync =
            wakeSyncWasArmed && syncThenStopAvailable()

        if (wakeSyncWasArmed && !pendingWakeTransitionSync) {
            SyncTransitionStore.clear(this)
        }

        BatterySleepStore.finishSession(this)

        if (wakeDecision.cancelSleepDelay) {
            cancelSleepDelay()
            Log.i(TAG, "Sleep delay cancelled by wake")
        }

        clearPendingSleepStopWait()
        releaseSleepTransitionWakeLock()

        sleepActionsApplied = false

        if (isTailscaleSleepVerificationPending()) {
            prepareTailscaleVerificationForWake()
        }

        restorePendingJamesDsp()

        if (pendingWakeTransitionSync) {
            SleepCycleStore.clearConnectorChange(
                this,
                BasicSyncConnector.id
            )
            Log.i(
                TAG,
                "Wake sync mode active; BasicSync restore token cleared"
            )
        } else {
            restorePendingBasicSync()
        }

        var cycle = SleepCycleStore.current(this)
        if (
            cycle.active &&
            cycle.helperExpected &&
            !cycle.helperSleepRequested &&
            !cycle.helperRestored
        ) {
            SleepCycleStore.markHelperRestored(this)
            cycle = SleepCycleStore.current(this)
        }

        val helperRestoreNeeded =
            cycle.active &&
                cycle.helperExpected &&
                cycle.helperSleepRequested &&
                !cycle.helperRestored
        val networkRestoreNeeded = hasPendingNetworkConnectorRestore()

        Log.i(
            TAG,
            "Screen ON -> restoring cycle=${cycle.cycleId} active=${cycle.active} " +
                "helperRestoreNeeded=$helperRestoreNeeded " +
                "networkRestoreNeeded=$networkRestoreNeeded"
        )

        lastWakeWifiManaged = false
        lastWakeWifiChanged = false
        lastWakeBluetoothManaged = false
        lastWakeBluetoothChanged = false

        pendingNetworkRestoreAfterHelper =
            helperRestoreNeeded && networkRestoreNeeded

        val helperSent = if (helperRestoreNeeded) {
            HelperController.sendWake(this, cycle.cycleId)
        } else {
            false
        }

        if (!helperSent) {
            lastWakeWifiManaged = false
            lastWakeBluetoothManaged = false
            pendingNetworkRestoreAfterHelper = false

            if (helperRestoreNeeded) {
                Log.w(
                    TAG,
                    "Helper restore still pending; preserving sleep transaction"
                )
                AppPreferences.recordEvent(
                    this,
                    "Wake → Helper restore pending"
                )
            }

            if (networkRestoreNeeded) {
                waitForNetworkAndRestorePendingConnectors()
                maybeStartWakeTransitionSync()
            } else {
                if (
                    !helperRestoreNeeded &&
                    !sleepSkippedByConditions &&
                    !SleepCycleStore.hasPendingConnectorChanges(this)
                ) {
                    AppPreferences.recordEvent(
                        this,
                        buildWakeSummary(
                            wifiManaged = false,
                            wifiChanged = false,
                            bluetoothManaged = false,
                            bluetoothChanged = false,
                            syncthing = false
                        )
                    )
                }
                SleepCycleStore.completeIfRestored(this)
                finishDisableRestoreIfRequested()
                maybeStartWakeTransitionSync()
            }
        } else if (networkRestoreNeeded) {
            Log.i(TAG, "Connector restore waiting for Helper wake result")
        } else {
            SleepCycleStore.completeIfRestored(this)
            finishDisableRestoreIfRequested()
        }

        sleepSkippedByConditions = false
    }

    private fun maybeStartWakeTransitionSync() {
        if (!pendingWakeTransitionSync) return
        if (!syncThenStopAvailable()) {
            pendingWakeTransitionSync = false
            return
        }

        if (!isRealWakeNow()) {
            return
        }

        if (syncMaintenanceRunner?.active == true) {
            return
        }

        pendingWakeTransitionSync = false

        val started =
            syncRunner().start(
                SyncMaintenanceTrigger.AFTER_WAKE
            ) { snapshot ->
                SyncTransitionStore.clear(this)
                AppPreferences.recordEvent(
                    this,
                    "Wake sync → ${snapshot.outcome} · clients stopped"
                )
            }

        if (started) {
            Log.i(TAG, "Wake sync maintenance started")
        } else {
            SyncTransitionStore.clear(this)
        }
    }

    private fun restorePendingSyncthingBeforeNetworkWait() {
        val change =
            SleepCycleStore.connectorChange(
                this,
                SyncthingConnector.id
            ) ?: return

        val wakeResult =
            SyncthingConnector.wake(
                this,
                change.restoreToken
            )

        if (wakeResult.success) {
            SleepCycleStore.clearConnectorChange(
                this,
                SyncthingConnector.id
            )
            Log.i(
                TAG,
                "Syncthing FOLLOW sent before validated-network wait; " +
                    "network readiness delegated to Syncthing-Fork"
            )

            if (!disableRestoreRequested) {
                AppPreferences.recordEvent(
                    this,
                    buildWakeSummary(
                        wifiManaged = lastWakeWifiManaged,
                        wifiChanged = lastWakeWifiChanged,
                        wifiAttempted = lastWakeWifiAttempted,
                        wifiToggleSuccess = lastWakeWifiToggleSuccess,
                        wifiAirplaneMode = lastWakeWifiAirplaneMode,
                        bluetoothManaged = lastWakeBluetoothManaged,
                        bluetoothChanged = lastWakeBluetoothChanged,
                        syncthing = true
                    )
                )
            }
        } else {
            Log.w(
                TAG,
                "Immediate Syncthing FOLLOW failed; keeping restore pending for network-ready retry"
            )
        }
    }

    private fun waitForNetworkAndRestorePendingConnectors() {
        restorePendingSyncthingBeforeNetworkWait()

        if (!hasPendingNetworkConnectorRestore()) {
            SleepCycleStore.completeIfRestored(this)
            finishDisableRestoreIfRequested()
            return
        }

        cancelNetworkReadyWait()

        networkReadyGate = NetworkReadyGate(
            context = this,
            handler = handler,
            timeoutMs = NETWORK_READY_TIMEOUT_MS
        ) { result ->
            networkReadyGate = null

            val realWake = isRealWakeNow()

            if (!realWake) {
                Log.i(
                    TAG,
                    "Network ready result=$result while device is not in a real wake; restore deferred"
                )
            } else {
                Log.i(
                    TAG,
                    "Network ready result=$result -> restoring pending connectors"
                )

                var restoreFailed = false
                var tailscaleVerificationScheduled = false

                val syncthingChange =
                    SleepCycleStore.connectorChange(
                        this,
                        SyncthingConnector.id
                    )
                if (syncthingChange != null) {
                    val wakeResult =
                        SyncthingConnector.wake(
                            this,
                            syncthingChange.restoreToken
                        )

                    if (wakeResult.success) {
                        SleepCycleStore.clearConnectorChange(
                            this,
                            SyncthingConnector.id
                        )

                        if (!disableRestoreRequested) {
                            AppPreferences.recordEvent(
                                this,
                                buildWakeSummary(
                                    wifiManaged = lastWakeWifiManaged,
                                    wifiChanged = lastWakeWifiChanged,
                                    wifiAttempted = lastWakeWifiAttempted,
                                    wifiToggleSuccess = lastWakeWifiToggleSuccess,
                                    wifiAirplaneMode = lastWakeWifiAirplaneMode,
                                    bluetoothManaged = lastWakeBluetoothManaged,
                                    bluetoothChanged = lastWakeBluetoothChanged,
                                    syncthing = true
                                )
                            )
                        }
                    } else {
                        restoreFailed = true
                        SleepCycleStore.markRestoreProblem(
                            this,
                            "Syncthing restore is still pending: ${wakeResult.detail}."
                        )
                        Log.w(
                            TAG,
                            "Syncthing restore failed; preserving pending connector transaction"
                        )
                        AppPreferences.recordEvent(
                            this,
                            if (disableRestoreRequested) {
                                "Disable → Syncthing restore pending"
                            } else {
                                "Wake → Syncthing restore pending"
                            }
                        )
                    }
                }

                val tailscaleChange =
                    SleepCycleStore.connectorChange(
                        this,
                        TailscaleConnector.id
                    )
                val tailscaleRestoreToken = tailscaleChange?.restoreToken
                if (
                    TailscaleTransactionToken.restoreTarget(
                        tailscaleRestoreToken
                    ) != null
                ) {
                    val wakeResult =
                        TailscaleConnector.wake(
                            this,
                            tailscaleRestoreToken
                        )

                    if (wakeResult.success) {
                        Log.i(
                            TAG,
                            "Tailscale CONNECT sent -> waiting for VPN verification"
                        )
                        tailscaleVerificationScheduled = true
                        scheduleTailscaleWakeVerification()
                    } else {
                        restoreFailed = true
                        SleepCycleStore.markRestoreProblem(
                            this,
                            "Tailscale restore is still pending: ${wakeResult.detail}."
                        )
                        Log.w(
                            TAG,
                            "Tailscale reconnect request failed; preserving transaction"
                        )
                        AppPreferences.recordEvent(
                            this,
                            if (disableRestoreRequested) {
                                "Disable → Tailscale restore pending"
                            } else {
                                "Wake → Tailscale restore pending"
                            }
                        )
                    }
                }

                if (!tailscaleVerificationScheduled) {
                    SleepCycleStore.completeIfRestored(this)
                    finishDisableRestoreIfRequested(
                        forceStop =
                            disableRestoreRequested && restoreFailed
                    )
                }
            }
        }.also { it.start() }
    }

    private fun cancelNetworkReadyWait() {
        networkReadyGate?.cancel()
        networkReadyGate = null
    }

    private fun refreshThorLidMonitor() {
        if (!shouldMonitorLid()) {
            restoreOwnedChargingSeparation("Lid automation disabled")
            stopThorLidMonitor()
            return
        }

        registerThorDisplayListener()
        if (AppPreferences.manageThorProtection(this)) {
            startThorPowerButtonMonitor()
        } else {
            thorPowerButtonMonitor?.stop()
            thorPowerButtonMonitor = null
        }

        if (thorLidMonitor != null) {
            applyClosedLidChargingSeparation("settings refresh")
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val currentLidState = ThorLidMonitor.readCurrentLidClosed()
        thorLidClosed =
            currentLidState
                ?: if (powerManager?.isInteractive == false) {
                    AppPreferences.lastKnownThorLidClosed(this) ?: false
                } else {
                    false
                }
        currentLidState?.let {
            AppPreferences.setLastKnownThorLidClosed(this, it)
        }

        thorExternalDisplayConnected = hasExternalDisplayConnected()
        thorExternalDisplayActive = hasActiveExternalDisplay()
        applyClosedLidChargingSeparation("service start")

        val monitor = ThorLidMonitor(
            onClosed = {
                handler.post {
                    thorLidClosed = true
                    thorClosedAwakeOverride = false
                    thorClosedSleepIntent = false
                    AppPreferences.setLastKnownThorLidClosed(
                        this@SleepManagerService,
                        true
                    )
                    handler.removeCallbacks(thorCloseGuardRunnable)
                    handler.removeCallbacks(thorScreenOnRecheckRunnable)

                    thorExternalDisplayConnected = hasExternalDisplayConnected()
                    thorExternalDisplayActive = hasActiveExternalDisplay()
                    Log.i(TAG, "SW_LID -> CLOSED")
                    applyClosedLidChargingSeparation("lid closed")

                    if (
                        AppPreferences.manageThorProtection(this@SleepManagerService)
                    ) {
                        if (thorExternalDisplayConnected) {
                            Log.i(
                                TAG,
                                if (thorExternalDisplayActive) {
                                    "Dock mode -> external display active; lid close ignored"
                                } else {
                                    "Dock mode -> external display connected; lid close ignored"
                                }
                            )
                        } else {
                            handler.postDelayed(
                                thorCloseGuardRunnable,
                                THOR_CLOSE_GUARD_DELAY_MS
                            )
                        }
                    }
                }
            },
            onOpened = {
                handler.post {
                    thorLidClosed = false
                    thorClosedAwakeOverride = false
                    thorClosedSleepIntent = false
                    thorSleepRequestPending = false
                    AppPreferences.setLastKnownThorLidClosed(
                        this@SleepManagerService,
                        false
                    )
                    handler.removeCallbacks(thorCloseGuardRunnable)
                    handler.removeCallbacks(thorScreenOnRecheckRunnable)
                    handler.removeCallbacks(thorDockDisconnectRunnable)
                    restoreOwnedChargingSeparation("Lid opened")
                    Log.i(TAG, "SW_LID -> OPEN")
                }
            },
            onError = { error ->
                Log.e(TAG, "Lid monitor failed", error)
                handler.post {
                    AppPreferences.recordEvent(
                        this,
                        "Closed-lid monitoring unavailable"
                    )
                    restoreOwnedChargingSeparation("Lid monitor error")
                    stopThorLidMonitor()
                }
            }
        )

        if (monitor.start()) {
            thorLidMonitor = monitor
            Log.i(
                TAG,
                "Lid monitor started: ${ThorLidMonitor.detectionDescription()}"
            )
        } else {
            Log.w(TAG, "No readable SW_LID input device found")
            restoreOwnedChargingSeparation("Lid monitor unavailable")
        }
    }

    private fun registerThorDisplayListener() {
        if (thorDisplayListenerRegistered) return
        val manager = displayManager ?: return

        manager.registerDisplayListener(thorDisplayListener, handler)
        thorDisplayListenerRegistered = true
        thorExternalDisplayConnected = hasExternalDisplayConnected()
        thorExternalDisplayActive = hasActiveExternalDisplay()
        Log.i(
            TAG,
            "Thor display monitor started connected=$thorExternalDisplayConnected " +
                "active=$thorExternalDisplayActive"
        )
    }

    private fun unregisterThorDisplayListener() {
        if (!thorDisplayListenerRegistered) return
        runCatching {
            displayManager?.unregisterDisplayListener(thorDisplayListener)
        }
        thorDisplayListenerRegistered = false
        thorExternalDisplayConnected = false
        thorExternalDisplayActive = false
    }

    private fun startThorPowerButtonMonitor() {
        if (thorPowerButtonMonitor != null) return

        val monitor = ThorPowerButtonMonitor(
            onPressed = {
                handler.post { handleThorPowerButtonPressed() }
            },
            onError = { error ->
                Log.e(TAG, "Thor power button monitor failed", error)
                handler.post {
                    thorPowerButtonMonitor?.stop()
                    thorPowerButtonMonitor = null
                }
            }
        )

        if (monitor.start()) {
            thorPowerButtonMonitor = monitor
            Log.i(
                TAG,
                "Thor power button monitor started on " +
                    ThorPowerButtonMonitor.findPowerButtonDevicePath()
            )
        } else {
            Log.w(TAG, "No pmic_pwrkey input device found; closed-lid Power option unavailable")
        }
    }

    private fun isThorExternalDisplay(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY) return false
        val name = display.name.lowercase()
        return name.contains("dp screen") ||
            name.contains("hdmi") ||
            name.contains("external")
    }

    private fun hasExternalDisplayConnected(): Boolean =
        displayManager
            ?.displays
            ?.any(::isThorExternalDisplay) == true

    private fun hasActiveExternalDisplay(): Boolean =
        displayManager
            ?.displays
            ?.any { display ->
                isThorExternalDisplay(display) &&
                    display.state == Display.STATE_ON
            } == true

    private fun refreshThorExternalDisplayState(reason: String) {
        if (!shouldMonitorLid()) return

        val connected = hasExternalDisplayConnected()
        val active = hasActiveExternalDisplay()
        val wasConnected = thorExternalDisplayConnected

        thorExternalDisplayConnected = connected
        thorExternalDisplayActive = active

        if (connected) {
            handler.removeCallbacks(thorDockDisconnectRunnable)
            restoreOwnedChargingSeparation("Dock connected")
            if (
                AppPreferences.manageThorProtection(this) &&
                thorLidClosed &&
                !thorClosedSleepIntent
            ) {
                thorClosedAwakeOverride = false
                if (active) {
                    Log.i(TAG, "Thor dock mode -> external display active ($reason)")
                }
            }
            return
        }

        if (wasConnected && thorLidClosed) {
            handler.removeCallbacks(thorDockDisconnectRunnable)
            handler.postDelayed(
                thorDockDisconnectRunnable,
                THOR_DOCK_DISCONNECT_DEBOUNCE_MS
            )
            Log.i(TAG, "Thor dock mode -> external display disconnected; debounce started")
        }
    }

    private fun handleThorDockDisconnect() {
        if (!shouldMonitorLid() || !thorLidClosed) return

        if (hasExternalDisplayConnected()) {
            thorExternalDisplayConnected = true
            thorExternalDisplayActive = hasActiveExternalDisplay()
            thorClosedAwakeOverride = false
            restoreOwnedChargingSeparation("Dock still connected")
            return
        }

        thorExternalDisplayConnected = false
        thorExternalDisplayActive = false
        applyClosedLidChargingSeparation("Dock disconnected")

        if (!AppPreferences.manageThorProtection(this)) {
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isInteractive) {
            thorClosedAwakeOverride = false
            return
        }

        if (AppPreferences.thorDockDisconnectSleeps(this)) {
            thorClosedAwakeOverride = false
            thorClosedSleepIntent = true
            Log.i(TAG, "Dock mode -> display disconnected; requesting sleep")
            requestThorSleep("dock display disconnected")
        } else {
            thorClosedSleepIntent = false
            thorClosedAwakeOverride = true
            Log.i(TAG, "Dock mode -> display disconnected; keeping awake")
            AppPreferences.recordEvent(
                this,
                "Dock → display disconnected · kept awake"
            )
        }
    }

    private fun handleThorPowerButtonPressed() {
        if (!AppPreferences.manageThorProtection(this) || !thorLidClosed) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isInteractive) return

        if (!AppPreferences.thorClosedPowerSleeps(this)) {
            Log.i(TAG, "Thor KEY_POWER while lid closed -> AYN default")
            return
        }

        thorClosedAwakeOverride = false
        thorClosedSleepIntent = true
        Log.i(TAG, "Thor KEY_POWER while lid closed -> requesting sleep")
        requestThorSleep("closed-lid power button")
    }

    private fun shouldBypassThorProtectionForClosedLid(): Boolean {
        if (!thorLidClosed || thorClosedSleepIntent) return false
        return hasExternalDisplayConnected() || thorClosedAwakeOverride
    }

    private fun isThorFalseWakeNow(): Boolean {
        val interactive =
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isInteractive == true
        return SleepWakePolicy.isSuppressedThorFalseWake(
            interactive = interactive,
            thorProtectionEnabled = AppPreferences.manageThorProtection(this),
            lidClosed = thorLidClosed,
            bypassClosedLidProtection =
                shouldBypassThorProtectionForClosedLid()
        )
    }

    private fun isEffectivelySleepingNow(): Boolean {
        val interactive =
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isInteractive == true
        return SleepWakePolicy.isEffectivelySleeping(
            interactive = interactive,
            thorProtectionEnabled = AppPreferences.manageThorProtection(this),
            lidClosed = thorLidClosed,
            bypassClosedLidProtection =
                shouldBypassThorProtectionForClosedLid()
        )
    }

    private fun isRealWakeNow(): Boolean {
        val interactive =
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isInteractive == true
        return SleepWakePolicy.isRealWake(
            interactive = interactive,
            thorProtectionEnabled = AppPreferences.manageThorProtection(this),
            lidClosed = thorLidClosed,
            bypassClosedLidProtection =
                shouldBypassThorProtectionForClosedLid()
        )
    }

    private fun stopThorLidMonitor() {
        handler.removeCallbacks(thorCloseGuardRunnable)
        handler.removeCallbacks(thorScreenOnRecheckRunnable)
        handler.removeCallbacks(thorDockDisconnectRunnable)
        thorLidClosed = false
        thorClosedAwakeOverride = false
        thorClosedSleepIntent = false
        thorSleepRequestPending = false
        thorLidMonitor?.stop()
        thorLidMonitor = null
        thorPowerButtonMonitor?.stop()
        thorPowerButtonMonitor = null
        unregisterThorDisplayListener()
    }

    private fun maybeReturnThorToSleep(reason: String) {
        if (!AppPreferences.manageThorProtection(this) || !thorLidClosed) return

        if (shouldBypassThorProtectionForClosedLid()) {
            Log.i(TAG, "Thor protection bypassed -> dock/keep-awake state ($reason)")
            return
        }

        requestThorSleep(reason)
    }

    private fun requestThorSleep(reason: String) {
        if (!AppPreferences.manageThorProtection(this) || !thorLidClosed) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isInteractive) {
            thorSleepRequestPending = false
            return
        }
        if (thorSleepRequestPending) return

        val now = SystemClock.elapsedRealtime()
        val sinceLastLock = now - lastThorLockAt
        if (sinceLastLock < THOR_LOCK_COOLDOWN_MS) {
            handler.removeCallbacks(thorScreenOnRecheckRunnable)
            handler.postDelayed(
                thorScreenOnRecheckRunnable,
                THOR_LOCK_COOLDOWN_MS - sinceLastLock + 100L
            )
            return
        }

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (!dpm.isAdminActive(thorAdminComponent)) {
            Log.w(TAG, "Thor protection skipped: Device Admin not active")
            return
        }

        try {
            thorSleepRequestPending = true
            thorClosedSleepIntent = true
            lastThorLockAt = now
            cancelNetworkReadyWait()
            pendingNetworkRestoreAfterHelper = false
            Log.i(TAG, "Thor protection -> lockNow() ($reason)")
            AppPreferences.recordEvent(
                this,
                "Protection → AYN Thor returned to sleep"
            )
            dpm.lockNow()

            handler.removeCallbacks(thorScreenOnRecheckRunnable)
            handler.postDelayed(
                thorScreenOnRecheckRunnable,
                THOR_LOCK_COOLDOWN_MS + 150L
            )
        } catch (t: Throwable) {
            thorSleepRequestPending = false
            Log.e(TAG, "Unable to return Thor to sleep", t)
        }
    }

    private fun buildSleepSummary(
        wifiManaged: Boolean,
        wifiChanged: Boolean,
        wifiAttempted: Boolean = false,
        wifiToggleSuccess: Boolean = true,
        wifiAirplaneMode: Boolean = false,
        bluetoothManaged: Boolean,
        bluetoothChanged: Boolean,
        syncthing: Boolean
    ): String {
        val actions = buildList {
            if (wifiManaged) {
                add(
                    when {
                        wifiAttempted && !wifiToggleSuccess ->
                            "Wi-Fi toggle failed" +
                                if (wifiAirplaneMode) {
                                    " · Airplane mode is enabled"
                                } else {
                                    ""
                                }
                        wifiChanged -> "Wi-Fi off"
                        else -> "Wi-Fi unchanged"
                    }
                )
            }
            if (bluetoothManaged) add(if (bluetoothChanged) "Bluetooth off" else "Bluetooth unchanged")
            if (syncthing) add("Syncthing paused")
        }
        return if (actions.isEmpty()) "Sleep" else "Sleep → " + actions.joinToString(" · ")
    }

    private fun buildWakeSummary(
        wifiManaged: Boolean,
        wifiChanged: Boolean,
        wifiAttempted: Boolean = false,
        wifiToggleSuccess: Boolean = true,
        wifiAirplaneMode: Boolean = false,
        bluetoothManaged: Boolean,
        bluetoothChanged: Boolean,
        syncthing: Boolean
    ): String {
        val actions = buildList {
            if (wifiManaged) {
                add(
                    when {
                        wifiAttempted && !wifiToggleSuccess ->
                            "Wi-Fi toggle failed" +
                                if (wifiAirplaneMode) {
                                    " · Airplane mode is enabled"
                                } else {
                                    ""
                                }
                        wifiChanged -> "Wi-Fi restored to previous state"
                        else -> "Wi-Fi unchanged"
                    }
                )
            }
            if (bluetoothManaged) add(if (bluetoothChanged) "Bluetooth restored to previous state" else "Bluetooth unchanged")
            if (syncthing) add("Syncthing resumed")
        }
        return if (actions.isEmpty()) "Wake" else "Wake → " + actions.joinToString(" · ")
    }

    private fun applyCurrentScreenState() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) onScreenOff() else onScreenOn()
    }

    private fun registerHelperResultReceiver() {
        if (helperResultReceiverRegistered) return

        val filter = IntentFilter(HelperController.ACTION_RESULT)
        ContextCompat.registerReceiver(
            this,
            helperResultReceiver,
            filter,
            HelperController.PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
        helperResultReceiverRegistered = true
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SleepManager",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps sleep and wake automation active"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        return builder
            .setContentTitle("SleepManager is on")
            .setContentText("Sleep / wake automation is running")
            .setSmallIcon(R.drawable.ic_notification_sleepmanager)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        pendingWakeTransitionSync = false

        if (isEffectivelySleepingNow()) {
            HelperController.setTemporaryWifi(this, enabled = false)
        }

        cancelSyncMaintenance(restoreSleepWifi = false)
        cancelNetworkReadyWait()
        pendingNetworkRestoreAfterHelper = false
        disableRestoreRequested = false
        if (!AppPreferences.isEnabled(this)) {
            cancelSleepDelay()
        } else {
            handler.removeCallbacks(sleepGraceRunnable)
            sleepGracePending = false
        }
        clearPendingSleepStopWait()
        cancelActiveBasicSyncWait()
        cancelTailscaleVerification()
        handler.removeCallbacks(thorCloseGuardRunnable)
        handler.removeCallbacks(thorScreenOnRecheckRunnable)
        handler.removeCallbacks(thorDockDisconnectRunnable)
        handler.removeCallbacks(ownedBasicSyncRestoreRunnable)
        handler.removeCallbacks(ownedDeviceControlRestoreRunnable)
        ownedBasicSyncRestorePending = false
        deviceControlRestorePending = false
        disableRestoreInitializing = false
        disableForceStopRequested = false
        releaseSleepTransitionWakeLock()
        syncStopProbeExecutor.shutdownNow()
        BasicSyncController.stopStateObserver()
        stopThorLidMonitor()

        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }

        if (helperResultReceiverRegistered) {
            try {
                unregisterReceiver(helperResultReceiver)
            } catch (_: IllegalArgumentException) {
            }
            helperResultReceiverRegistered = false
        }

        running = false
        Log.i(TAG, "Service stopped; active sleep transaction preserved")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
