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
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.IntentCompat
import androidx.core.content.ContextCompat
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.device.BackgroundReliability
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.diagnostics.DiagnosticsBuilder
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.qs.SleepManagerTileService
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.ui.screens.SleepManagerScreen
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import com.med.sleepmanager.update.UpdateCheckScheduler
import com.med.sleepmanager.update.UpdateInstaller
import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    internal var activityRefreshToken by mutableIntStateOf(0)
    internal var currentWifiState by mutableStateOf<Boolean?>(null)
    internal var currentBluetoothState by mutableStateOf<Boolean?>(null)
    internal var currentSyncthingState by mutableStateOf<SyncthingController.RuntimeState?>(null)
    internal var currentTailscaleConnected by mutableStateOf<Boolean?>(null)
    internal var currentBasicSyncState by mutableStateOf<BasicSyncController.RemoteState?>(null)
    internal var currentBackgroundReliability by
        mutableStateOf<BackgroundReliability.Snapshot?>(null)
    internal var currentDeviceControlCapabilities by
        mutableStateOf<DeviceControlController.ControlCapabilities?>(null)

    @Volatile
    private var syncthingStateProbeRunning = false
    @Volatile
    private var backgroundReliabilityProbeRunning = false
    @Volatile
    private var deviceCapabilitiesProbeRunning = false
    private var helperStateReceiverRegistered = false
    private var pendingThorAdminEnable = false
    private var pendingExactAlarmEnable = false
    private var pendingExternalNavigation = false
    private var pendingUpdateInstallPath: String? = null
    private var pendingPackageInstallerReturn = false
    internal var installerReturnToken by mutableIntStateOf(0)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openUpdatesOnLaunch =
            intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true
        UpdateCheckScheduler.sync(this)
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
                    useSystemColors = useSystemColors,
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
        HelperController.requestState(this)
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

        if (pendingThorAdminEnable) {
            pendingThorAdminEnable = false
            val granted = isThorAdminActive()
            AppPreferences.setManageThorProtection(this, granted)
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
            AppPreferences.manageThorProtection(this) &&
            !isThorAdminActive()
        ) {
            AppPreferences.setManageThorProtection(this, false)
            activityRefreshToken++
        }

        if (pendingExactAlarmEnable) {
            pendingExactAlarmEnable = false
            val granted = canScheduleExactAlarms()
            if (granted) {
                AppPreferences.setCustomDelayEnabled(this, true)
                AppPreferences.setSleepGraceMs(this, 0L)
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
        super.onStop()
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
            pendingThorAdminEnable ||
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

    private fun thorAdminComponent(): ComponentName =
        ComponentName(this, ThorDeviceAdminReceiver::class.java)

    internal fun isThorAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(thorAdminComponent())
    }

    private fun refreshBackgroundReliabilityAfterThorAdminRemoval(
        attempt: Int = 0
    ) {
        val adminStillActive = isThorAdminActive()

        if (!adminStillActive || attempt >= 5) {
            refreshBackgroundReliabilityAsync()
            activityRefreshToken++
            return
        }

        statusRefreshHandler.postDelayed(
            {
                refreshBackgroundReliabilityAfterThorAdminRemoval(
                    attempt = attempt + 1
                )
            },
            250L
        )
    }

    private fun requestThorAdmin() {
        pendingThorAdminEnable = true
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, thorAdminComponent())
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allows SleepManager to immediately return the device to sleep if it wakes while the lid is still closed."
            )
        }
        startActivity(intent)
    }

    internal fun setThorProtectionEnabled(enabled: Boolean) {
        if (!enabled) {
            AppPreferences.setManageThorProtection(this, false)
            refreshRunningService()

            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(thorAdminComponent())) {
                runCatching { dpm.removeActiveAdmin(thorAdminComponent()) }
                refreshBackgroundReliabilityAfterThorAdminRemoval()
            } else {
                refreshBackgroundReliabilityAsync()
            }
            return
        }

        if (!ThorLidMonitor.isSupported()) {
            Toast.makeText(
                this,
                "Compatible lid sensor not detected",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isThorAdminActive()) {
            requestThorAdmin()
            return
        }

        AppPreferences.setManageThorProtection(this, true)
        refreshRunningService()
    }

    internal fun refreshRunningService() {
        if (!AppPreferences.isEnabled(this)) return

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
        } catch (_: Throwable) {
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
        AppPreferences.recordEvent(this, "Setup finished • background automation active")
        finishAndRemoveTask()
    }

    internal fun setManagerEnabled(enabled: Boolean) {
        if (!enabled) {
            AppPreferences.setEnabled(this, false)

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
            AppPreferences.manageThorProtection(this) &&
            (!ThorLidMonitor.isSupported() || !isThorAdminActive())
        ) {
            Toast.makeText(
                this,
                "Enable closed-lid protection permission first",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AppPreferences.setEnabled(this, true)

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)

            AppPreferences.recordEvent(this, "SleepManager enabled")
            SleepManagerTileService.requestRefresh(this)
        } catch (t: Throwable) {
            AppPreferences.setEnabled(this, false)
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
            AppPreferences.recordEvent(this, "Syncthing restore pending")
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
            AppPreferences.recordEvent(this, "JamesDSP restore pending")
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
            AppPreferences.recordEvent(this, "BasicSync restore pending")
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
