package com.med.sleepmanager

import android.Manifest
import android.app.TimePickerDialog
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.text.format.DateFormat
import android.provider.Settings
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.med.sleepmanager.BuildConfig
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.diagnostics.DiagnosticsBuilder
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.qs.SleepManagerTileService
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.sync.ManagedSyncProviders
import com.med.sleepmanager.sync.SyncCompletionState
import com.med.sleepmanager.sync.basicSyncCompletionState
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import com.med.sleepmanager.update.UpdateCheckResult
import com.med.sleepmanager.update.UpdateCheckScheduler
import com.med.sleepmanager.update.UpdateChecker
import com.med.sleepmanager.update.UpdateDownloadResult
import com.med.sleepmanager.update.HelperUpdateInfo
import com.med.sleepmanager.update.UpdateInfo
import com.med.sleepmanager.update.UpdateInstaller
import com.med.sleepmanager.update.UpdateNotifier
import java.io.File
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun View.performSleepManagerFeedback() {
    if (isHapticFeedbackEnabled) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    if (isSoundEffectsEnabled) {
        playSoundEffect(SoundEffectConstants.CLICK)
    }
}

@Composable
private fun feedbackClick(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performSleepManagerFeedback()
        action()
    }
}

@Composable
private fun <T> feedbackChange(action: (T) -> Unit): (T) -> Unit {
    val view = LocalView.current
    return { value ->
        view.performSleepManagerFeedback()
        action(value)
    }
}

private enum class AppSection {
    HOME,
    ADVANCED,
    STATS,
    ACTIVITY_LOG,
    ABOUT
}

private val AppSection.label: String
    get() = when (this) {
        AppSection.HOME -> "Home"
        AppSection.ADVANCED -> "Advanced settings"
        AppSection.STATS -> "Stats"
        AppSection.ACTIVITY_LOG -> "Activity log"
        AppSection.ABOUT -> "About"
    }

private val AppSection.iconRes: Int
    get() = when (this) {
        AppSection.HOME -> R.drawable.ic_home
        AppSection.ADVANCED -> R.drawable.ic_advanced
        AppSection.STATS -> R.drawable.ic_battery
        AppSection.ACTIVITY_LOG -> R.drawable.ic_activity_log
        AppSection.ABOUT -> R.drawable.ic_info
    }

class MainActivity : ComponentActivity() {

    private var activityRefreshToken by mutableIntStateOf(0)
    private var currentWifiState by mutableStateOf<Boolean?>(null)
    private var currentBluetoothState by mutableStateOf<Boolean?>(null)
    private var currentSyncthingState by mutableStateOf<SyncthingController.RuntimeState?>(null)
    private var currentTailscaleConnected by mutableStateOf<Boolean?>(null)
    private var currentBasicSyncState by mutableStateOf<BasicSyncController.RemoteState?>(null)
    @Volatile
    private var syncthingStateProbeRunning = false
    private var helperStateReceiverRegistered = false
    private var pendingThorAdminEnable = false
    private var pendingExactAlarmEnable = false
    private var pendingExternalNavigation = false
    private var pendingUpdateInstallPath: String? = null
    private var pendingPackageInstallerReturn = false
    private var installerReturnToken by mutableIntStateOf(0)
    private var openUpdatesOnLaunch = false

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

        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        statusRefreshRunnable.run()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == UPDATE_NOTIFICATION_PERMISSION_REQUEST_CODE) {
            pendingExternalNavigation = false
            activityRefreshToken++
        }
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
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                helperStateReceiver,
                filter,
                HelperController.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                helperStateReceiver,
                filter,
                HelperController.PERMISSION,
                null
            )
        }
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

    private fun isThorAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(thorAdminComponent())
    }

    private fun requestThorAdmin() {
        pendingThorAdminEnable = true
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, thorAdminComponent())
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allows SleepManager to immediately return the AYN Thor to sleep if it wakes while the lid is still closed."
            )
        }
        startActivity(intent)
    }

    private fun setThorProtectionEnabled(enabled: Boolean) {
        if (!enabled) {
            AppPreferences.setManageThorProtection(this, false)
            refreshRunningService()

            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(thorAdminComponent())) {
                runCatching { dpm.removeActiveAdmin(thorAdminComponent()) }
            }
            return
        }

        if (!ThorLidMonitor.isSupported()) {
            Toast.makeText(
                this,
                "AYN Thor hall sensor not detected",
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

    private fun refreshRunningService() {
        if (!AppPreferences.isEnabled(this)) return

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
        } catch (_: Throwable) {
        }
    }

    private fun finishSetup() {
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

    private fun setManagerEnabled(enabled: Boolean) {
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
                "Enable AYN Thor closed-lid protection permission first",
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

    private fun restoreSyncthingTransactionNow() {
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

    private fun restoreJamesDspTransactionNow() {
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

    private fun restoreBasicSyncTransactionNow() {
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

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        val alarmManager =
            getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false

        return alarmManager.canScheduleExactAlarms()
    }

    private fun requestExactAlarmAccess() {
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

    private fun showTimePicker(
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

    private fun openReleaseUrl(url: String) {
        launchExternalActivity(
            intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            failureMessage = "No app is available to open this link"
        )
    }

    private fun openAppInfo() {
        launchExternalActivity(
            intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ),
            failureMessage = "Unable to open Android app info"
        )
    }

    private fun installVerifiedUpdate(apkPath: String) {
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

    private fun requestUpdateNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            pendingExternalNavigation = true
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                UPDATE_NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun copyDiagnostics() {
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
        if (!AppPreferences.isEnabled(this) || SleepManagerService.running) return

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
        } catch (_: Throwable) {
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SleepManagerScreen(
        useSystemColors: Boolean,
        onUseSystemColorsChanged: (Boolean) -> Unit
    ) {
        val refreshToken = activityRefreshToken
        var showTargetDialog by remember { mutableStateOf(false) }
        var showTestDialog by remember { mutableStateOf(false) }
        var currentSection by rememberSaveable {
            mutableStateOf(
                if (openUpdatesOnLaunch) AppSection.ABOUT else AppSection.HOME
            )
        }
        val homeListState = rememberLazyListState()
        val advancedListState = rememberLazyListState()
        val statsListState = rememberLazyListState()
        val activityListState = rememberLazyListState()
        val aboutListState = rememberLazyListState()
        val currentListState = when (currentSection) {
            AppSection.HOME -> homeListState
            AppSection.ADVANCED -> advancedListState
            AppSection.STATS -> statsListState
            AppSection.ACTIVITY_LOG -> activityListState
            AppSection.ABOUT -> aboutListState
        }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val drawerScope = rememberCoroutineScope()
        val compactLayout = LocalConfiguration.current.screenWidthDp < 600

        var managerEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.isEnabled(this))
        }
        var wifiEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageWifi(this))
        }
        var bluetoothEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageBluetooth(this))
        }
        var syncthingEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageSyncthing(this))
        }
        var tailscaleEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageTailscale(this))
        }
        var jamesDspEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageJamesDsp(this))
        }
        var basicSyncEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageBasicSync(this))
        }
        var periodicSyncWhileSleeping by remember(refreshToken) {
            mutableStateOf(AppPreferences.periodicSyncWhileSleeping(this))
        }
        var syncThenStopOnSleepWake by remember(refreshToken) {
            mutableStateOf(AppPreferences.syncThenStopOnSleepWake(this))
        }
        var thorProtectionEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageThorProtection(this))
        }
        var thorDockDisconnectSleeps by remember(refreshToken) {
            mutableStateOf(AppPreferences.thorDockDisconnectSleeps(this))
        }
        var thorClosedPowerSleeps by remember(refreshToken) {
            mutableStateOf(AppPreferences.thorClosedPowerSleeps(this))
        }
        var sleepGraceMs by remember(refreshToken) {
            mutableStateOf(AppPreferences.sleepGraceMs(this))
        }
        var customDelayEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.customDelayEnabled(this))
        }
        var customDelayMs by remember(refreshToken) {
            mutableStateOf(AppPreferences.customDelayMs(this))
        }
        var batteryConditionEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.batteryConditionEnabled(this))
        }
        var batteryBelowPercent by remember(refreshToken) {
            mutableStateOf(AppPreferences.batteryBelowPercent(this))
        }
        var notChargingOnly by remember(refreshToken) {
            mutableStateOf(AppPreferences.notChargingOnly(this))
        }
        var batterySaverMode by remember(refreshToken) {
            mutableStateOf(AppPreferences.batterySaverMode(this))
        }
        var scheduleEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.scheduleEnabled(this))
        }
        var scheduleStartMinutes by remember(refreshToken) {
            mutableStateOf(AppPreferences.scheduleStartMinutes(this))
        }
        var scheduleEndMinutes by remember(refreshToken) {
            mutableStateOf(AppPreferences.scheduleEndMinutes(this))
        }
        val effectiveSleepDelayMs =
            if (customDelayEnabled) customDelayMs else sleepGraceMs

        val setupComplete = remember(refreshToken) {
            AppPreferences.isSetupComplete(this)
        }

        val helperInstalled = remember(refreshToken) {
            HelperController.isInstalled(this)
        }
        val helperVersion = remember(refreshToken) {
            runCatching {
                packageManager.getPackageInfo(HelperController.PACKAGE, 0).versionName
            }.getOrNull()
        }
        val targets = remember(refreshToken) {
            SyncthingController.installedTargets(this)
        }
        val selectedTarget = remember(refreshToken) {
            SyncthingController.selectedTarget(this)
        }
        val tailscaleInstalled = remember(refreshToken) {
            TailscaleController.isInstalled(this)
        }
        val tailscaleVersion = remember(refreshToken) {
            TailscaleController.versionName(this)
        }
        val jamesDspTarget = remember(refreshToken) {
            JamesDspController.selectedTarget(this)
        }
        val basicSyncInstalled = remember(refreshToken) {
            BasicSyncController.isInstalled(this)
        }
        val basicSyncVersion = remember(refreshToken) {
            BasicSyncController.versionName(this)
        }
        val thorProtectionSupported = remember(refreshToken) {
            ThorLidMonitor.isSupported()
        }
        val thorAdminActive = remember(refreshToken) {
            isThorAdminActive()
        }
        val batteryDashboard = remember(refreshToken) {
            BatterySleepStore.dashboard(this)
        }
        val batteryStats = remember(refreshToken) {
            BatterySleepStore.stats(this)
        }
        val restoreProblem = remember(refreshToken) {
            SleepCycleStore.restoreProblem(this)
        }
        var automaticUpdateChecks by remember(refreshToken) {
            mutableStateOf(AppPreferences.automaticUpdateChecks(this))
        }
        val availableUpdate = remember(refreshToken) {
            UpdateChecker.cachedUpdate(this)
        }
        val availableHelperUpdate = remember(refreshToken) {
            UpdateChecker.cachedHelperUpdate(this)
        }
        val updateNotificationsAllowed = remember(refreshToken) {
            UpdateNotifier.notificationsAllowed(this)
        }

        if (showTestDialog) {
            AlertDialog(
                onDismissRequest = { showTestDialog = false },
                title = { Text("Test sleep / wake") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("1. Choose the sleep actions you want to test below.")
                        Text("2. Enable SleepManager at the top of the app.")
                        Text("3. Turn the screen off normally.")
                        Text(
                            if (effectiveSleepDelayMs > 0L) {
                                "4. Leave it off for more than ${formatDuration(effectiveSleepDelayMs)} so the sleep delay can finish."
                            } else {
                                "4. Leave it off for a few seconds."
                            }
                        )
                        Text("5. Wake the device normally, then reopen SleepManager.")
                        Text("6. Last activity and View log should show the sleep / wake result.")
                        Text("Copy log includes the full transaction details if needed.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = feedbackClick { showTestDialog = false }) {
                        Text("Got it")
                    }
                }
            )
        }

        if (showTargetDialog) {
            SyncthingTargetDialog(
                targets = targets,
                selected = selectedTarget?.packageName,
                onDismiss = { showTargetDialog = false },
                onSelect = { target ->
                    val previous = SyncthingController.selectedTarget(this)?.packageName

                    if (
                        managerEnabled &&
                        syncthingEnabled &&
                        previous != null &&
                        previous != target.packageName
                    ) {
                        val change = SleepCycleStore.connectorChange(
                            this,
                            SyncthingConnector.id
                        )
                        if (
                            SyncthingConnector.restoreTargetPackage(
                                change?.restoreToken
                            ) == previous
                        ) {
                            restoreSyncthingTransactionNow()
                            SleepCycleStore.completeIfRestored(this)
                        }
                    }

                    SyncthingController.select(this, target.packageName)
                    showTargetDialog = false
                    activityRefreshToken++
                }
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "SleepManager",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Quiet on sleep. Ready on wake.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AppSection.values().forEach { section ->
                            NavigationDrawerItem(
                                modifier = Modifier.height(48.dp),
                                icon = {
                                    Icon(
                                        painter = painterResource(section.iconRes),
                                        contentDescription = null
                                    )
                                },
                                label = { Text(section.label) },
                                selected = currentSection == section,
                                onClick = feedbackClick {
                                    currentSection = section
                                    drawerScope.launch { drawerState.close() }
                                }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            ),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 16.dp,
                                    end = 8.dp,
                                    top = 4.dp,
                                    bottom = 4.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Use system colors",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    if (Build.VERSION.SDK_INT >= 31) {
                                        "Material You"
                                    } else {
                                        "Requires Android 12+"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = useSystemColors,
                                onCheckedChange = feedbackChange(
                                    onUseSystemColorsChanged
                                ),
                                enabled = Build.VERSION.SDK_INT >= 31
                            )
                        }
                    }
                }
            }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (!compactLayout) {
                    CompactSideRail(
                        currentSection = currentSection,
                        onSectionSelected = { currentSection = it },
                        onMenuClick = {
                            drawerScope.launch { drawerState.open() }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (compactLayout) {
                                IconButton(
                                    onClick = feedbackClick {
                                        drawerScope.launch { drawerState.open() }
                                    },
                                    modifier = Modifier.offset(y = (-4).dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_menu),
                                        contentDescription = "Open navigation"
                                    )
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    when (currentSection) {
                                        AppSection.HOME -> "SleepManager"
                                        AppSection.ADVANCED -> "Advanced"
                                        AppSection.STATS -> "Stats"
                                        AppSection.ACTIVITY_LOG -> "Activity log"
                                        AppSection.ABOUT -> "About"
                                    },
                                    modifier = Modifier.testTag("top_app_title"),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    when (currentSection) {
                                        AppSection.HOME -> "Quiet on sleep. Ready on wake."
                                        AppSection.ADVANCED -> "Fine-tune synchronization and sleep behavior"
                                        AppSection.STATS -> "Sleep and battery measurements"
                                        AppSection.ACTIVITY_LOG -> "Recent SleepManager activity"
                                        AppSection.ABOUT -> "App information"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            ) { padding ->
            LazyColumn(
                state = currentListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("main_list"),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (currentSection) {
                    AppSection.HOME -> {
                item {
                    StatusCard(
                        enabled = managerEnabled,
                        running = SleepManagerService.running,
                        onToggle = {
                            setManagerEnabled(!managerEnabled)
                            managerEnabled = AppPreferences.isEnabled(this@MainActivity)
                            activityRefreshToken++
                        }
                    )
                }

                if (availableUpdate != null || availableHelperUpdate != null) {
                    item {
                        UpdateAvailableCard(
                            update = availableUpdate,
                            helperUpdate = availableHelperUpdate,
                            onUpdate = { currentSection = AppSection.ABOUT }
                        )
                    }
                }

                if (!setupComplete) {
                    item {
                        OnboardingCard(
                            helperInstalled = helperInstalled,
                            helperVersion = helperVersion,
                            syncthingTarget = selectedTarget,
                            syncthingEnabled = syncthingEnabled,
                            tailscaleInstalled = tailscaleInstalled,
                            tailscaleVersion = tailscaleVersion,
                            jamesDspTarget = jamesDspTarget,
                            basicSyncInstalled = basicSyncInstalled,
                            basicSyncVersion = basicSyncVersion,
                            managerEnabled = managerEnabled,
                            onGetHelper = { currentSection = AppSection.ABOUT },
                            onShowTest = { showTestDialog = true }
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = "Battery",
                        subtitle = "Track sleep drain, averages and standby estimates."
                    )
                }

                item {
                    BatteryDashboardCard(
                        dashboard = batteryDashboard,
                        stats = batteryStats
                    )
                }

                restoreProblem?.let { problem ->
                    item {
                        PendingRestoreCard(
                            problem = problem,
                            onForget = {
                                HelperController.forgetPendingState(this@MainActivity)
                                SleepCycleStore.clear(this@MainActivity)
                                AppPreferences.recordEvent(
                                    this@MainActivity,
                                    "Recovery → pending restore forgotten"
                                )
                                activityRefreshToken++
                            }
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = "When device sleeps",
                        subtitle = "Choose what SleepManager should temporarily switch off."
                    )
                }

                item {
                    SettingsCard {
                        SettingRow(
                            icon = R.drawable.ic_wifi,
                            title = "Wi‑Fi",
                            subtitle = if (helperInstalled) {
                                "Turn off during sleep. Restore previous state on wake."
                            } else {
                                "Compatibility helper required"
                            },
                            status = if (helperInstalled) {
                                currentWifiState?.let {
                                    "Current state: ${if (it) "ON" else "OFF"}"
                                } ?: "Current state: CHECKING…"
                            } else {
                                null
                            },
                            checked = wifiEnabled,
                            enabled = helperInstalled,
                            onCheckedChange = {
                                wifiEnabled = it
                                AppPreferences.setManageWifi(this@MainActivity, it)
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        SettingRow(
                            icon = R.drawable.ic_bluetooth,
                            title = "Bluetooth",
                            subtitle = if (helperInstalled) {
                                "Turn off during sleep. Restore previous state on wake."
                            } else {
                                "Compatibility helper required"
                            },
                            status = if (helperInstalled) {
                                currentBluetoothState?.let {
                                    "Current state: ${if (it) "ON" else "OFF"}"
                                } ?: "Current state: CHECKING…"
                            } else {
                                null
                            },
                            checked = bluetoothEnabled,
                            enabled = helperInstalled,
                            onCheckedChange = {
                                bluetoothEnabled = it
                                AppPreferences.setManageBluetooth(this@MainActivity, it)
                            }
                        )

                    }
                }

                item {
                    AnimatedVisibility(visible = !helperInstalled) {
                        InfoCard(
                            title = "Compatibility helper not installed",
                            text = "The helper controls Wi‑Fi and Bluetooth without root or Shizuku. It has no launcher icon and runs only when SleepManager asks it to.",
                            actionLabel = "Install Helper",
                            onAction = { currentSection = AppSection.ABOUT }
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = "Sleep behavior",
                        subtitle = "Control how SleepManager reacts when the screen turns off."
                    )
                }

                item {
                    SettingsCard {
                        if (thorProtectionSupported) {
                            SettingRow(
                                icon = R.drawable.ic_lid_lock,
                                title = "AYN Thor closed-lid protection",
                                subtitle = "Return the Thor to sleep after accidental trigger wake-ups with the lid closed. Dock-safe: external displays won\'t trigger false sleeps.",
                                checked = thorProtectionEnabled && thorAdminActive,
                                enabled = true,
                                onCheckedChange = { enabled ->
                                    setThorProtectionEnabled(enabled)
                                    thorProtectionEnabled =
                                        AppPreferences.manageThorProtection(this@MainActivity)
                                    activityRefreshToken++
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            SettingRow(
                                icon = R.drawable.ic_lid_lock,
                                title = "Sleep when external display disconnects",
                                subtitle = "With the lid closed, put the Thor to sleep when dock video is unplugged. Off keeps AYN's default awake behavior.",
                                checked = thorDockDisconnectSleeps,
                                enabled = thorProtectionEnabled && thorAdminActive,
                                onCheckedChange = {
                                    thorDockDisconnectSleeps = it
                                    AppPreferences.setThorDockDisconnectSleeps(
                                        this@MainActivity,
                                        it
                                    )
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            SettingRow(
                                icon = R.drawable.ic_lid_lock,
                                title = "Power button sleeps with lid closed",
                                subtitle = "With the lid closed and Thor awake, press Power to sleep—docked or after disconnecting the external display. Off keeps AYN's default behavior.",
                                checked = thorClosedPowerSleeps,
                                enabled = thorProtectionEnabled && thorAdminActive,
                                onCheckedChange = {
                                    thorClosedPowerSleeps = it
                                    AppPreferences.setThorClosedPowerSleeps(
                                        this@MainActivity,
                                        it
                                    )
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        SleepGraceSelector(
                            valueMs = sleepGraceMs,
                            customDelayEnabled = customDelayEnabled,
                            customDelayMs = customDelayMs,
                            onChange = { value ->
                                sleepGraceMs = value
                                AppPreferences.setSleepGraceMs(this@MainActivity, value)
                            },
                            onCustom = {
                                currentSection = AppSection.ADVANCED
                            }
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = "App integrations",
                        subtitle = "Pause background services that don’t need to run while your device sleeps."
                    )
                }

                item {
                    SettingsCard {
                        CompactIntegrationRow(
                            icon = R.drawable.ic_syncthing,
                            title = "Syncthing‑Fork",
                            version = selectedTarget?.displayName
                                ?.substringAfter("•")
                                ?.trim()
                                ?: if (selectedTarget != null) "Installed" else "Not detected",
                            status = if (selectedTarget != null) {
                                when (currentSyncthingState) {
                                    SyncthingController.RuntimeState.RUNNING -> "Running"
                                    SyncthingController.RuntimeState.STOPPED -> "Stopped"
                                    SyncthingController.RuntimeState.UNKNOWN -> "Unknown"
                                    null -> "Checking…"
                                }
                            } else {
                                null
                            },
                            checked = syncthingEnabled && selectedTarget != null,
                            enabled = selectedTarget != null,
                            onCheckedChange = {
                                syncthingEnabled = it
                                AppPreferences.setManageSyncthing(this@MainActivity, it)

                                if (it) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Enable Settings → Behaviour → Service control by broadcast in Syncthing-Fork.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else if (managerEnabled) {
                                    restoreSyncthingTransactionNow()
                                    SleepCycleStore.completeIfRestored(this@MainActivity)
                                }
                            },
                            onOpen = if (selectedTarget != null) {
                                { SyncthingController.open(this@MainActivity) }
                            } else {
                                null
                            },
                            secondaryActionLabel =
                                if (targets.size > 1) "Change target" else null,
                            onSecondaryAction =
                                if (targets.size > 1) {
                                    { showTargetDialog = true }
                                } else {
                                    null
                                }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        CompactIntegrationRow(
                            icon = R.drawable.ic_tailscale,
                            title = "Tailscale / TailDNS",
                            version = if (tailscaleInstalled) {
                                tailscaleVersion?.substringBefore("-") ?: "Installed"
                            } else {
                                "Not detected"
                            },
                            status = if (tailscaleInstalled) {
                                currentTailscaleConnected?.let {
                                    if (it) "Connected" else "Disconnected"
                                } ?: "Checking…"
                            } else {
                                null
                            },
                            checked = tailscaleEnabled && tailscaleInstalled,
                            enabled = tailscaleInstalled,
                            onCheckedChange = {
                                tailscaleEnabled = it
                                AppPreferences.setManageTailscale(
                                    this@MainActivity,
                                    it
                                )
                            },
                            onOpen = if (tailscaleInstalled) {
                                { TailscaleController.open(this@MainActivity) }
                            } else {
                                null
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        CompactIntegrationRow(
                            icon = R.drawable.ic_equalizer,
                            title = "JamesDSP",
                            version = jamesDspTarget?.versionName ?: "Not detected",
                            status = null,
                            checked = jamesDspEnabled && jamesDspTarget != null,
                            enabled = jamesDspTarget != null,
                            onCheckedChange = {
                                jamesDspEnabled = it
                                AppPreferences.setManageJamesDsp(
                                    this@MainActivity,
                                    it
                                )

                                if (!it && managerEnabled) {
                                    restoreJamesDspTransactionNow()
                                    SleepCycleStore.completeIfRestored(this@MainActivity)
                                }
                            },
                            onOpen = if (jamesDspTarget != null) {
                                { JamesDspController.open(this@MainActivity) }
                            } else {
                                null
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        CompactIntegrationRow(
                            icon = R.drawable.ic_sync,
                            title = "BasicSync",
                            version = if (basicSyncInstalled) {
                                basicSyncVersion ?: "Installed"
                            } else {
                                "Not detected"
                            },
                            status = if (basicSyncInstalled) {
                                if (BasicSyncController.supportsStateApi(this@MainActivity)) {
                                    currentBasicSyncState?.let { state ->
                                        val mode = when (state.mode) {
                                            BasicSyncController.Mode.AUTO_MODE -> "Auto mode"
                                            BasicSyncController.Mode.MANUAL_MODE_STARTED -> "Manual mode"
                                            BasicSyncController.Mode.MANUAL_MODE_STOPPED -> "Manual mode"
                                        }
                                        val runState = when (state.runState) {
                                            BasicSyncController.RunState.RUNNING -> "Running"
                                            BasicSyncController.RunState.NOT_RUNNING -> "Stopped"
                                            BasicSyncController.RunState.PAUSED -> "Paused"
                                            BasicSyncController.RunState.STARTING -> "Starting"
                                            BasicSyncController.RunState.STOPPING -> "Stopping"
                                            BasicSyncController.RunState.IMPORTING -> "Importing"
                                            BasicSyncController.RunState.EXPORTING -> "Exporting"
                                        }
                                        val syncState =
                                            if (
                                                BasicSyncController.supportsSyncCounters(
                                                    this@MainActivity
                                                )
                                            ) {
                                                when (basicSyncCompletionState(state)) {
                                                    SyncCompletionState.SYNCING -> "Syncing"
                                                    SyncCompletionState.SYNCED -> "Synced"
                                                    SyncCompletionState.UNKNOWN -> null
                                                }
                                            } else {
                                                null
                                            }
                                        listOfNotNull(mode, runState, syncState)
                                            .joinToString(" · ")
                                    } ?: "Checking…"
                                } else {
                                    "Legacy: STOP → Auto mode"
                                }
                            } else {
                                null
                            },
                            checked = basicSyncEnabled && basicSyncInstalled,
                            enabled = basicSyncInstalled,
                            onCheckedChange = {
                                basicSyncEnabled = it
                                AppPreferences.setManageBasicSync(
                                    this@MainActivity,
                                    it
                                )

                                if (it) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        if (BasicSyncController.supportsStateApi(this@MainActivity)) {
                                            "Enable Allow remote control in BasicSync. SleepManager will preserve and restore BasicSync's previous mode."
                                        } else {
                                            "Enable Allow remote control in BasicSync. This BasicSync version uses legacy STOP → Auto mode behavior."
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else if (managerEnabled) {
                                    restoreBasicSyncTransactionNow()
                                    SleepCycleStore.completeIfRestored(this@MainActivity)
                                    // Also let the running service restore the
                                    // original state owned by Sync then stop.
                                    refreshRunningService()
                                }

                                if (it && managerEnabled) {
                                    refreshRunningService()
                                }
                            },
                            onOpen = if (basicSyncInstalled) {
                                { BasicSyncController.open(this@MainActivity) }
                            } else {
                                null
                            }
                        )
                    }
                }
                item {
                    BehaviorCard(
                        wifi = wifiEnabled && helperInstalled,
                        bluetooth = bluetoothEnabled && helperInstalled,
                        syncthing = syncthingEnabled && selectedTarget != null,
                        tailscale = tailscaleEnabled && tailscaleInstalled,
                        jamesDsp = jamesDspEnabled && jamesDspTarget != null,
                        basicSync = basicSyncEnabled && basicSyncInstalled,
                        thorProtection = thorProtectionEnabled && thorAdminActive,
                        sleepGraceMs = effectiveSleepDelayMs,
                        advancedConditions = buildList {
                            if (batteryConditionEnabled) {
                                add("Battery below ${batteryBelowPercent}%")
                            }
                            if (notChargingOnly) {
                                add("Device is not charging")
                            }
                            when (batterySaverMode) {
                                AppPreferences.BATTERY_SAVER_ON ->
                                    add("Battery Saver is ON")
                                AppPreferences.BATTERY_SAVER_OFF ->
                                    add("Battery Saver is OFF")
                            }
                            if (scheduleEnabled) {
                                add(
                                    "Time is between " +
                                        formatTime(scheduleStartMinutes) +
                                        " and " +
                                        formatTime(scheduleEndMinutes)
                                )
                            }
                        }
                    )
                }

                if (managerEnabled && !setupComplete) {
                    item {
                        Button(
                            onClick = feedbackClick { finishSetup() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Finish setup")
                        }
                    }
                } else if (setupComplete) {
                    item {
                        Button(
                            onClick = feedbackClick { finishAndRemoveTask() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }

                item {
                    LastActivityCard(
                        context = this@MainActivity,
                        onViewLog = { currentSection = AppSection.ACTIVITY_LOG },
                        onCopyLog = { copyDiagnostics() }
                    )
                }
                    }

                    AppSection.ADVANCED -> {
                        item {
                            AdvancedSettingsPage(
                                periodicSyncWhileSleeping = periodicSyncWhileSleeping,
                                syncThenStopOnSleepWake = syncThenStopOnSleepWake,
                                syncConditionsAvailable =
                                    ManagedSyncProviders.completionReady(
                                        this@MainActivity
                                    ),
                                onPeriodicSyncWhileSleepingChange = {
                                    periodicSyncWhileSleeping = it
                                    AppPreferences.setPeriodicSyncWhileSleeping(
                                        this@MainActivity,
                                        it
                                    )
                                },
                                onSyncThenStopOnSleepWakeChange = {
                                    syncThenStopOnSleepWake = it
                                    AppPreferences.setSyncThenStopOnSleepWake(
                                        this@MainActivity,
                                        it
                                    )

                                    if (managerEnabled) {
                                        // Disabling this option must hand
                                        // BasicSync back to its pre-feature state.
                                        refreshRunningService()
                                    }
                                },
                                customDelayEnabled = customDelayEnabled,
                                customDelayMs = customDelayMs,
                                batteryConditionEnabled = batteryConditionEnabled,
                                batteryBelowPercent = batteryBelowPercent,
                                notChargingOnly = notChargingOnly,
                                batterySaverMode = batterySaverMode,
                                scheduleEnabled = scheduleEnabled,
                                scheduleStartMinutes = scheduleStartMinutes,
                                scheduleEndMinutes = scheduleEndMinutes,
                                onCustomDelayEnabledChange = { enabled ->
                                    if (
                                        enabled &&
                                        !canScheduleExactAlarms()
                                    ) {
                                        requestExactAlarmAccess()
                                    } else {
                                        customDelayEnabled = enabled
                                        AppPreferences.setCustomDelayEnabled(
                                            this@MainActivity,
                                            enabled
                                        )

                                        if (!enabled) {
                                            sleepGraceMs = 0L
                                            AppPreferences.setSleepGraceMs(
                                                this@MainActivity,
                                                0L
                                            )
                                        }
                                    }
                                },
                                onCustomDelayChange = {
                                    customDelayMs = it
                                    AppPreferences.setCustomDelayMs(this@MainActivity, it)
                                },
                                onBatteryConditionEnabledChange = {
                                    batteryConditionEnabled = it
                                    AppPreferences.setBatteryConditionEnabled(this@MainActivity, it)
                                },
                                onBatteryBelowPercentChange = {
                                    batteryBelowPercent = it
                                    AppPreferences.setBatteryBelowPercent(this@MainActivity, it)
                                },
                                onNotChargingOnlyChange = {
                                    notChargingOnly = it
                                    AppPreferences.setNotChargingOnly(this@MainActivity, it)
                                },
                                onBatterySaverModeChange = {
                                    batterySaverMode = it
                                    AppPreferences.setBatterySaverMode(this@MainActivity, it)
                                },
                                onScheduleEnabledChange = {
                                    scheduleEnabled = it
                                    AppPreferences.setScheduleEnabled(this@MainActivity, it)
                                },
                                onPickScheduleStart = {
                                    showTimePicker(scheduleStartMinutes) { value ->
                                        scheduleStartMinutes = value
                                        AppPreferences.setScheduleStartMinutes(
                                            this@MainActivity,
                                            value
                                        )
                                    }
                                },
                                onPickScheduleEnd = {
                                    showTimePicker(scheduleEndMinutes) { value ->
                                        scheduleEndMinutes = value
                                        AppPreferences.setScheduleEndMinutes(
                                            this@MainActivity,
                                            value
                                        )
                                    }
                                }
                            )
                        }
                    }

                    AppSection.STATS -> {
                        item {
                            BatteryStatsPage(
                                stats = batteryStats
                            )
                        }
                    }

                    AppSection.ACTIVITY_LOG -> {
                        item {
                            ActivityLogPage(
                                context = this@MainActivity,
                                onCopyLog = { copyDiagnostics() }
                            )
                        }
                    }

                    AppSection.ABOUT -> {
                        item {
                            AboutPage(
                                context = this@MainActivity,
                                automaticUpdateChecks = automaticUpdateChecks,
                                notificationsAllowed = updateNotificationsAllowed,
                                onAutomaticUpdateChecksChange = { enabled ->
                                    automaticUpdateChecks = enabled
                                    AppPreferences.setAutomaticUpdateChecks(
                                        this@MainActivity,
                                        enabled
                                    )
                                    UpdateCheckScheduler.sync(this@MainActivity)
                                    if (enabled) {
                                        UpdateChecker.checkOnForegroundAsync(
                                            this@MainActivity,
                                            notify = true
                                        )
                                    }
                                    activityRefreshToken++
                                },
                                onRequestNotificationPermission = {
                                    requestUpdateNotificationPermission()
                                },
                                onOpenExternalUrl = { url ->
                                    openReleaseUrl(url)
                                },
                                onOpenAppInfo = {
                                    openAppInfo()
                                },
                                onInstallVerifiedUpdate = { apkPath ->
                                    installVerifiedUpdate(apkPath)
                                },
                                installerReturnToken = installerReturnToken,
                                onUpdateStateChanged = {
                                    activityRefreshToken++
                                }
                            )
                        }
                    }
                }
            }
                }
            }
        }
    }
    companion object {
        const val EXTRA_OPEN_UPDATES = "com.med.sleepmanager.extra.OPEN_UPDATES"
        private const val STATUS_REFRESH_INTERVAL_MS = 3000L
        private const val UPDATE_NOTIFICATION_PERMISSION_REQUEST_CODE = 5222
    }
}

@Composable
private fun CompactSideRail(
    currentSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    onMenuClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .testTag("compact_side_rail")
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        NavigationRail(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            windowInsets = WindowInsets(0, 0, 0, 0),
            header = {
                IconButton(
                    onClick = feedbackClick(onMenuClick),
                    modifier = Modifier.offset(y = (-4).dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = "Open navigation"
                    )
                }
            }
        ) {
            AppSection.values().forEach { section ->
                NavigationRailItem(
                    modifier = Modifier.height(48.dp),
                    selected = currentSection == section,
                    onClick = feedbackClick { onSectionSelected(section) },
                    icon = {
                        Icon(
                            painter = painterResource(section.iconRes),
                            contentDescription = section.label
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    helperInstalled: Boolean,
    helperVersion: String?,
    syncthingTarget: SyncthingController.Target?,
    syncthingEnabled: Boolean,
    tailscaleInstalled: Boolean,
    tailscaleVersion: String?,
    jamesDspTarget: JamesDspController.Target?,
    basicSyncInstalled: Boolean,
    basicSyncVersion: String?,
    managerEnabled: Boolean,
    onGetHelper: () -> Unit,
    onShowTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Quick setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                if (helperInstalled) {
                    "✓ Compatibility helper installed${helperVersion?.let { " • $it" } ?: ""}"
                } else {
                    "• Compatibility helper not installed — only needed for Wi-Fi / Bluetooth."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (!helperInstalled) {
                TextButton(onClick = feedbackClick(onGetHelper)) {
                    Text("Install Helper")
                }
            }

            Text(
                syncthingTarget?.let { "✓ ${it.displayName} detected" }
                    ?: "• Syncthing-Fork not detected — optional.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (syncthingEnabled && syncthingTarget != null) {
                Text(
                    "Syncthing-Fork: make sure Settings → Behaviour → Service control by broadcast is enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                if (tailscaleInstalled) {
                    val version =
                        tailscaleVersion?.substringBefore("-")
                    "✓ Tailscale" +
                        (version?.let { " • $it" } ?: "") +
                        " detected"
                } else {
                    "• Tailscale not detected — optional."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                jamesDspTarget?.let { target ->
                    "✓ JamesDSP" +
                        (target.versionName?.let { " • $it" } ?: "") +
                        " detected"
                } ?: "• JamesDSP not detected — optional.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                if (basicSyncInstalled) {
                    "✓ BasicSync" +
                        (basicSyncVersion?.let { " • $it" } ?: "") +
                        " detected"
                } else {
                    "• BasicSync not detected — optional."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                if (managerEnabled) {
                    "SleepManager is enabled. Finish setup when your selected actions look right."
                } else {
                    "Choose the actions you want below, enable SleepManager, then tap Finish setup."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "Sleep statistics start automatically. Complete a sleep session of at least 3 hours without charging to build averages and standby estimates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = feedbackClick(onShowTest)) {
                Text("How to test sleep / wake")
            }
        }
    }
}

@Composable
private fun UpdateAvailableCard(
    update: UpdateInfo?,
    helperUpdate: HelperUpdateInfo?,
    onUpdate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("update_available_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (update != null && helperUpdate != null) {
                        "Updates available"
                    } else {
                        "Update available"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        update != null && helperUpdate != null ->
                            "SleepManager ${update.versionName} and Helper ${helperUpdate.versionName} are available."
                        update != null ->
                            "SleepManager ${update.versionName} is available on GitHub."
                        helperUpdate != null ->
                            "SleepManager Helper ${helperUpdate.versionName} is available."
                        else -> "An update is available."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            OutlinedButton(onClick = feedbackClick(onUpdate)) {
                Text("Update")
            }
        }
    }
}

@Composable
private fun StatusCard(
    enabled: Boolean,
    running: Boolean,
    onToggle: () -> Unit
) {
    val active = enabled && running

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (active) R.drawable.ic_shield else R.drawable.ic_shield_off
                        ),
                        contentDescription = null,
                        tint = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            active -> "SleepManager is active"
                            enabled -> "SleepManager is starting"
                            else -> "SleepManager is off"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = when {
                            active -> "Selected settings turn off or pause on sleep, then restore on wake."
                            enabled -> "Background automation is starting…"
                            else -> "Enable it once, and it will run automatically in the background."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Button(
                onClick = feedbackClick(onToggle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (enabled) "Disable SleepManager"
                    else "Enable SleepManager"
                )
            }

        }
    }
}


@Composable
private fun BatteryStatsPage(
    stats: BatterySleepStore.Stats
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatsCard(title = "Battery") {
            StatsGrid(
                metrics = listOf(
                    "Current" to (
                        stats.currentPrecisePercent?.let {
                            "${formatPercentTwoDecimals(it)}%"
                        } ?: stats.currentPercent?.let { "$it%" } ?: "—"
                    ),
                    "Estimated capacity" to (
                        stats.estimatedCapacityMah?.let {
                            "~${formatMah(it)} mAh"
                        } ?: "Unavailable"
                    ),
                    "Current charge" to (
                        stats.currentChargeMah?.let {
                            "${formatMah(it)} mAh"
                        } ?: "Unavailable"
                    )
                )
            )
        }

        StatsCard(title = "Sleep efficiency") {
            StatsGrid(
                metrics = listOf(
                    "7-day drain" to (
                        stats.averageDrainPerHour?.let {
                            "${formatDrainRate(it)}% / h"
                        } ?: "Not enough data"
                    ),
                    "Charge drain" to (
                        stats.averageDrainMahPerHour?.let {
                            "${formatMahRate(it)} mAh / h"
                        } ?: "Unavailable"
                    ),
                    "Deep sleep" to (
                        stats.averageDeepSleepPercent?.let {
                            "${formatPercentOneDecimal(it)}%"
                        } ?: "Collecting data"
                    ),
                    "Measured sleep" to formatSleepSessionDuration(
                        stats.totalMeasuredSleepMs
                    )
                )
            )
        }

        StatsCard(title = "Standby estimate") {
            StatsGrid(
                metrics = listOf(
                    "From current battery" to (
                        stats.estimatedHoursRemaining?.let {
                            formatStandbyEstimate(it)
                        } ?: "Not enough data"
                    ),
                    "From 100%" to (
                        stats.estimatedHoursFromFull?.let {
                            formatStandbyEstimate(it)
                        } ?: "Not enough data"
                    ),
                    "Best drain" to (
                        stats.bestDrainPerHour?.let {
                            "${formatDrainRate(it)}% / h"
                        } ?: "—"
                    ),
                    "Worst drain" to (
                        stats.worstDrainPerHour?.let {
                            "${formatDrainRate(it)}% / h"
                        } ?: "—"
                    )
                )
            )
            Text(
                "Standby estimates use eligible sleep sessions of at least 3 hours from the last 7 days and are only indicative.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        stats.lastSession?.let { last ->
            StatsCard(title = "Last sleep") {
                StatsGrid(
                    metrics = listOf(
                        "Duration" to formatSleepSessionDuration(last.durationMs),
                        (if (last.chargedDuringSleep) "Battery change" else "Battery used") to
                            formatBatteryChange(last),
                        "Charge used" to (
                            last.drainMah?.let {
                                "${formatMah(it)} mAh"
                            } ?: if (last.chargedDuringSleep) {
                                "Charging during sleep"
                            } else {
                                "Unavailable"
                            }
                        ),
                        "Deep sleep" to (
                            last.deepSleepPercent?.let {
                                "${formatPercentOneDecimal(it)}%"
                            } ?: "Collecting data"
                        )
                    )
                )
            }
        }

        StatsCard(title = "Measurement") {
            Text(
                "${stats.averageSessionCount} eligible sleep session" +
                    if (stats.averageSessionCount == 1) "." else "s.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Sessions shorter than 3 hours or containing charging are excluded from battery statistics. Shorter sleeps still appear in Last sleep and history. Capacity is an estimate when Android does not expose a readable full-capacity value.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun StatsGrid(
    metrics: List<Pair<String, String>>
) {
    metrics.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            row.forEach { pair ->
                BatteryMetric(
                    modifier = Modifier.weight(1f),
                    label = pair.first,
                    value = pair.second
                )
            }
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun formatMah(value: Double): String =
    String.format(Locale.US, "%.0f", value)

private fun formatMahRate(value: Double): String =
    if (value < 10.0) {
        String.format(Locale.US, "%.1f", value)
    } else {
        String.format(Locale.US, "%.0f", value)
    }

private fun formatPercentOneDecimal(value: Double): String =
    String.format(Locale.US, "%.1f", value)

private fun formatPercentTwoDecimals(value: Double): String =
    String.format(Locale.getDefault(), "%.2f", value)

private fun formatStandbyEstimate(hours: Double): String {
    if (!hours.isFinite() || hours <= 0.0) return "—"

    val totalHours = hours.toLong().coerceAtLeast(1L)
    val days = totalHours / 24L
    val remainderHours = totalHours % 24L
    return when {
        days > 0L && remainderHours > 0L -> "${days}d ${remainderHours}h"
        days > 0L -> "${days}d"
        else -> "${totalHours}h"
    }
}

@Composable
private fun BatteryDashboardCard(
    dashboard: BatterySleepStore.Dashboard,
    stats: BatterySleepStore.Stats
) {
    val currentText = dashboard.currentPercent?.let { "$it%" } ?: "—"
    val last = dashboard.lastSession

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                InteractiveBatteryGauge(
                    percent = dashboard.currentPercent,
                    charging = dashboard.currentCharging,
                    currentChargeMah = stats.currentChargeMah,
                    estimatedCapacityMah = stats.estimatedCapacityMah,
                    estimatedHoursRemaining = stats.estimatedHoursRemaining,
                    averageDrainPerHour = stats.averageDrainPerHour,
                    averageDeepSleepPercent = stats.averageDeepSleepPercent,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 280.dp)
                )

                val gaugeBodyHeight =
                    if (LocalConfiguration.current.screenWidthDp < 600) 66.dp else 74.dp

                Column(
                    modifier = Modifier
                        .align(Alignment.Top)
                        .height(gaugeBodyHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        currentText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (dashboard.currentCharging) {
                        Text(
                            "Charging",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (last == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Last sleep",
                        value = "—"
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Drain",
                        value = "—"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "7-day average",
                        value = "—"
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Samples",
                        value = "0"
                    )
                }

                Text(
                    "Sleep statistics will appear after your first sleep session of at least 3 hours without charging.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Last sleep",
                        value =
                            "${formatBatteryChange(last)} • ${formatSleepSessionDuration(last.durationMs)}"
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Drain",
                        value =
                            when {
                                last.chargedDuringSleep -> "Charging during sleep"
                                last.durationMs < BatterySleepStore.MIN_AVERAGE_DURATION_MS -> "Short session"
                                else -> last.drainPerHour?.let {
                                    "${formatDrainRate(it)}% / h"
                                } ?: "—"
                            }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "7-day average",
                        value =
                            dashboard.averageDrainPerHour?.let {
                                "${formatDrainRate(it)}% / h"
                            } ?: "Not enough data"
                    )
                    BatteryMetric(
                        modifier = Modifier.weight(1f),
                        label = "Samples",
                        value = dashboard.averageSessionCount.toString()
                    )
                }

                when {
                    last.chargedDuringSleep -> {
                        Text(
                            "Sessions with charging are excluded from the 7-day drain average.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    last.durationMs < BatterySleepStore.MIN_AVERAGE_DURATION_MS -> {
                        Text(
                            if (dashboard.averageSessionCount == 0) {
                                "Complete a sleep session of at least 3 hours to start building sleep averages."
                            } else {
                                "This short session is excluded from the 7-day average."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        last.drainMah?.let {
                            Text(
                                "Measured charge used: ${String.format(Locale.US, "%.0f", it)} mAh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveBatteryGauge(
    percent: Int?,
    charging: Boolean,
    currentChargeMah: Double?,
    estimatedCapacityMah: Double?,
    estimatedHoursRemaining: Double?,
    averageDrainPerHour: Double?,
    averageDeepSleepPercent: Double?,
    modifier: Modifier = Modifier
) {
    var infoIndex by remember { mutableIntStateOf(0) }
    var hasInteracted by remember { mutableStateOf(false) }
    val level = (percent ?: 0).coerceIn(0, 100)
    val animatedLevel by animateFloatAsState(
        targetValue = level / 100f,
        label = "Battery level"
    )

    // Above 50%, follow the app/system Material accent so the battery naturally
    // matches the current Android UI. Lower levels intentionally override it.
    val levelColor = when {
        charging -> MaterialTheme.colorScheme.primary
        percent == null -> MaterialTheme.colorScheme.outline
        level >= 50 -> MaterialTheme.colorScheme.primary
        level >= 20 -> Color(0xFFE7B55E)
        else -> Color(0xFFE97878)
    }

    val chargeText =
        if (currentChargeMah != null && estimatedCapacityMah != null) {
            "${formatMah(currentChargeMah)} / ~${formatMah(estimatedCapacityMah)} mAh"
        } else {
            "Charge data unavailable"
        }

    val standbyText =
        estimatedHoursRemaining?.let {
            "Estimated standby • ${formatStandbyEstimate(it)}"
        } ?: "Estimated standby • Not enough data"

    val sleepDrainText =
        averageDrainPerHour?.let {
            "Sleep drain • ${formatDrainRate(it)}% / h"
        } ?: "Sleep drain • Not enough data"

    val deepSleepText =
        averageDeepSleepPercent?.let {
            "Deep sleep • ${String.format(Locale.US, "%.0f", it)}%"
        } ?: "Deep sleep • Collecting data"

    val infoTexts = listOf(
        chargeText,
        standbyText,
        sleepDrainText,
        deepSleepText
    )
    val currentInfo = infoTexts[infoIndex]

    val onGaugeClick = feedbackClick {
        hasInteracted = true
        infoIndex = (infoIndex + 1) % infoTexts.size
    }

    // Do not keep an infinite frame-clock animation alive while discharging.
    val chargingAlpha = if (charging) {
        val chargingTransition = rememberInfiniteTransition(label = "Charging pulse")
        val alpha by chargingTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Charging alpha"
        )
        alpha
    } else {
        1f
    }

    val compactBatteryLayout = LocalConfiguration.current.screenWidthDp < 600
    val gaugeHeight = if (compactBatteryLayout) 66.dp else 74.dp
    val terminalHeight = if (compactBatteryLayout) 30.dp else 34.dp
    val bodyShape = RoundedCornerShape(if (compactBatteryLayout) 18.dp else 20.dp)
    val innerShape = RoundedCornerShape(if (compactBatteryLayout) 12.dp else 14.dp)

    Column(
        modifier = modifier
            .testTag("battery_gauge")
            .clickable(onClick = onGaugeClick)
            .semantics {
                contentDescription =
                    "Battery ${percent?.let { "$it percent" } ?: "level unavailable"}. " +
                        "$currentInfo. Tap to cycle battery stats."
            },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier
                .testTag("battery_gauge_body")
                .fillMaxWidth()
                .height(gaugeHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(bodyShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 3.dp,
                        color = levelColor,
                        shape = bodyShape
                    )
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(innerShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedLevel)
                            .background(levelColor.copy(alpha = 0.30f))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Crossfade(
                                targetState = infoIndex,
                                label = "Battery info"
                            ) { index ->
                                Text(
                                    text = infoTexts[index],
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (charging) {
                            Text(
                                "⚡",
                                modifier = Modifier.alpha(chargingAlpha),
                                style = MaterialTheme.typography.titleMedium,
                                color = levelColor
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(10.dp)
                    .height(terminalHeight)
                    .clip(RoundedCornerShape(0.dp, 6.dp, 6.dp, 0.dp))
                    .background(levelColor)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(end = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = !hasInteracted) {
                Text(
                    "Tap to cycle stats",
                    modifier = Modifier.testTag("battery_gauge_hint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!hasInteracted) {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Row(
                modifier = Modifier.testTag("battery_gauge_page_indicator"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(infoTexts.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == infoIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PendingRestoreCard(
    problem: String,
    onForget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Pending restore needs attention",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "SleepManager keeps the transaction instead of pretending the restore succeeded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            OutlinedButton(
                onClick = feedbackClick(onForget)
            ) {
                Text("Forget pending restore")
            }
        }
    }
}

private fun formatBatteryChange(
    session: BatterySleepStore.SleepSession
): String {
    val precise = session.preciseBatteryChangePercent
    if (precise != null && precise.isFinite()) {
        val magnitude = formatPercentTwoDecimals(kotlin.math.abs(precise))
        return when {
            precise > 0.0 -> "+$magnitude%"
            precise < 0.0 -> "−$magnitude%"
            else -> "0.00%"
        }
    }

    val delta = session.endPercent - session.startPercent
    return when {
        delta > 0 -> "+${delta}%"
        delta < 0 -> "−${-delta}%"
        else -> "0%"
    }
}

private fun formatSleepSessionDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        totalMinutes > 0L -> "${totalMinutes}m"
        else -> "<1m"
    }
}

private fun formatDrainRate(value: Double): String =
    when {
        value < 0.01 -> "<0.01"
        value < 1.0 -> String.format(Locale.US, "%.2f", value)
        else -> String.format(Locale.US, "%.1f", value)
    }

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    status: String? = null,
    checked: Boolean,
    enabled: Boolean,
    dimWhenDisabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val contentAlpha = if (enabled || !dimWhenDisabled) 1f else 0.55f
    val secondaryAlpha = if (enabled || !dimWhenDisabled) 1f else 0.6f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                checked && enabled -> MaterialTheme.colorScheme.primary
                enabled -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = when {
                    checked && enabled -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = contentAlpha
                )
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = secondaryAlpha
                )
            )

            status?.let { currentStatus ->
                val isActive =
                    currentStatus.endsWith(": ON") ||
                        currentStatus.endsWith(": RUNNING") ||
                        currentStatus.endsWith(": CONNECTED")
                Text(
                    currentStatus,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        currentStatus.contains("CHECKING") -> MaterialTheme.colorScheme.onSurfaceVariant
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = feedbackChange(onCheckedChange),
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "$title toggle"
            }
        )
    }
}

@Composable
private fun CompactIntegrationRow(
    @DrawableRes icon: Int,
    title: String,
    version: String,
    status: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val compact = LocalConfiguration.current.screenWidthDp < 600

    val iconContent: @Composable () -> Unit = {
        Surface(
            shape = CircleShape,
            color = when {
                checked && enabled -> MaterialTheme.colorScheme.primary
                enabled -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = when {
                    checked && enabled -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp)
            )
        }
    }

    val detailsContent: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                title,
                modifier = Modifier.testTag("integration_title_$title"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                status?.let {
                    val active =
                        it.contains("Running", ignoreCase = true) ||
                            it.contains("Connected", ignoreCase = true) ||
                            it.contains("Starting", ignoreCase = true)
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }

    val actionsContent: @Composable () -> Unit = {
        if (onOpen != null || (secondaryActionLabel != null && onSecondaryAction != null)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onOpen?.let { open ->
                    OutlinedButton(onClick = feedbackClick(open)) {
                        Text("Open")
                    }
                }

                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    TextButton(
                        onClick = feedbackClick(onSecondaryAction),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }

    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                iconContent()
                Box(modifier = Modifier.weight(1f)) {
                    detailsContent()
                }
                Switch(
                    checked = checked,
                    onCheckedChange = feedbackChange(onCheckedChange),
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = "$title toggle"
                    }
                )
            }

            if (onOpen != null || (secondaryActionLabel != null && onSecondaryAction != null)) {
                Box(
                    modifier = Modifier.padding(start = 54.dp)
                ) {
                    actionsContent()
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            iconContent()
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    detailsContent()
                    actionsContent()
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = feedbackChange(onCheckedChange),
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = "$title toggle"
                }
            )
        }
    }
}
@Composable
private fun SleepGraceSelector(
    valueMs: Long,
    customDelayEnabled: Boolean,
    customDelayMs: Long,
    onChange: (Long) -> Unit,
    onCustom: () -> Unit
) {
    val options = listOf(
        "Immediate" to 0L,
        "5 s" to 5000L,
        "10 s" to 10000L
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Grace period",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            if (customDelayEnabled) {
                "Using custom delay from Advanced settings."
            } else {
                "Wait before applying sleep actions. If the screen wakes during this period, nothing is changed."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options.size) { index ->
                val (label, value) = options[index]
                FilterChip(
                    selected = !customDelayEnabled && valueMs == value,
                    onClick = feedbackClick { onChange(value) },
                    enabled = !customDelayEnabled,
                    label = { Text(label) }
                )
            }

            item {
                FilterChip(
                    selected = customDelayEnabled,
                    onClick = feedbackClick(onCustom),
                    label = { Text("Custom") }
                )
            }
        }

        if (customDelayEnabled) {
            TextButton(onClick = feedbackClick(onCustom)) {
                Text("Advanced • ${formatDuration(customDelayMs)}")
            }
        }
    }
}

@Composable
private fun AdvancedSettingsPage(
    periodicSyncWhileSleeping: Boolean,
    syncThenStopOnSleepWake: Boolean,
    syncConditionsAvailable: Boolean,
    onPeriodicSyncWhileSleepingChange: (Boolean) -> Unit,
    onSyncThenStopOnSleepWakeChange: (Boolean) -> Unit,
    customDelayEnabled: Boolean,
    customDelayMs: Long,
    batteryConditionEnabled: Boolean,
    batteryBelowPercent: Int,
    notChargingOnly: Boolean,
    batterySaverMode: String,
    scheduleEnabled: Boolean,
    scheduleStartMinutes: Int,
    scheduleEndMinutes: Int,
    onCustomDelayEnabledChange: (Boolean) -> Unit,
    onCustomDelayChange: (Long) -> Unit,
    onBatteryConditionEnabledChange: (Boolean) -> Unit,
    onBatteryBelowPercentChange: (Int) -> Unit,
    onNotChargingOnlyChange: (Boolean) -> Unit,
    onBatterySaverModeChange: (String) -> Unit,
    onScheduleEnabledChange: (Boolean) -> Unit,
    onPickScheduleStart: () -> Unit,
    onPickScheduleEnd: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(
            title = "Advanced sync conditions",
            subtitle = "Control when managed sync clients run outside their normal sleep behavior."
        )

        SettingsCard {
            AdvancedToggleRow(
                title = "Periodic sync while sleeping",
                subtitle = if (syncConditionsAvailable) {
                    "While the device stays asleep, sync managed clients every 24h, then stop them and restore the sleep state."
                } else {
                    "BasicSync 3.19+ required; Syncthing-Fork support pending."
                },
                checked = periodicSyncWhileSleeping,
                enabled = syncConditionsAvailable,
                onCheckedChange = onPeriodicSyncWhileSleepingChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = "Sync then stop on sleep & wake",
                subtitle = if (syncConditionsAvailable) {
                    "Sync managed clients after wake and again before sleep. After each sync completes, stop them to reduce background battery use."
                } else {
                    "BasicSync 3.19+ required; Syncthing-Fork support pending."
                },
                checked = syncThenStopOnSleepWake,
                enabled = syncConditionsAvailable,
                onCheckedChange = onSyncThenStopOnSleepWakeChange
            )
        }

        SectionTitle(
            title = "Advanced sleep conditions",
            subtitle = "Fine-tune when sleep actions are allowed and when they begin."
        )

        SettingsCard {
            AdvancedToggleRow(
                title = "Use custom delay",
                subtitle = if (customDelayEnabled) {
                    "Grace period will show Advanced • ${formatDuration(customDelayMs)}"
                } else {
                    "Grace period uses Immediate / 5s / 10s."
                },
                checked = customDelayEnabled,
                onCheckedChange = onCustomDelayEnabledChange
            )

            if (customDelayEnabled) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Delay before sleep actions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    val options = listOf(
                        "1 min" to 60_000L,
                        "5 min" to 300_000L,
                        "10 min" to 600_000L,
                        "30 min" to 1_800_000L
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(options.size) { index ->
                            val (label, value) = options[index]
                            FilterChip(
                                selected = customDelayMs == value,
                                onClick = feedbackClick { onCustomDelayChange(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        SectionTitle(
            title = "Conditions",
            subtitle = "All enabled conditions must be true."
        )

        Text(
            "Conditions are combined with AND logic. If one enabled condition is false, sleep actions are skipped.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsCard {
            AdvancedToggleRow(
                title = "Battery level",
                subtitle = if (batteryConditionEnabled) {
                    "Only below ${batteryBelowPercent}%"
                } else {
                    "Ignore battery percentage"
                },
                checked = batteryConditionEnabled,
                onCheckedChange = onBatteryConditionEnabledChange
            )

            if (batteryConditionEnabled) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val levels = listOf(20, 30, 40, 50, 60)
                        items(levels.size) { index ->
                            val level = levels[index]
                            FilterChip(
                                selected = batteryBelowPercent == level,
                                onClick = feedbackClick { onBatteryBelowPercentChange(level) },
                                label = { Text("< ${level}%") }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = "Not charging",
                subtitle = if (notChargingOnly) {
                    "Only run sleep actions while unplugged"
                } else {
                    "Ignore charging state"
                },
                checked = notChargingOnly,
                onCheckedChange = onNotChargingOnlyChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Battery Saver",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    when (batterySaverMode) {
                        AppPreferences.BATTERY_SAVER_ON -> "Only when Android Battery Saver is ON"
                        AppPreferences.BATTERY_SAVER_OFF -> "Only when Android Battery Saver is OFF"
                        else -> "Ignore Battery Saver state"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        "Ignore" to AppPreferences.BATTERY_SAVER_IGNORE,
                        "ON" to AppPreferences.BATTERY_SAVER_ON,
                        "OFF" to AppPreferences.BATTERY_SAVER_OFF
                    )
                    items(modes.size) { index ->
                        val (label, mode) = modes[index]
                        FilterChip(
                            selected = batterySaverMode == mode,
                            onClick = feedbackClick { onBatterySaverModeChange(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = "Schedule",
                subtitle = if (scheduleEnabled) {
                    "Only between ${formatTime(scheduleStartMinutes)} and ${formatTime(scheduleEndMinutes)}"
                } else {
                    "No time restriction"
                },
                checked = scheduleEnabled,
                onCheckedChange = onScheduleEnabledChange
            )

            if (scheduleEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = feedbackClick(onPickScheduleStart),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("From ${formatTime(scheduleStartMinutes)}")
                    }
                    OutlinedButton(
                        onClick = feedbackClick(onPickScheduleEnd),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("To ${formatTime(scheduleEndMinutes)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                }
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.55f
                )
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = feedbackChange(onCheckedChange),
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "$title toggle"
            }
        )
    }
}

@Composable
private fun ActivityLogPage(
    context: Context,
    onCopyLog: () -> Unit
) {
    val events = EventHistoryStore.recent(context)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = feedbackClick(onCopyLog)) {
                Text("Copy log")
            }
        }

        if (events.isEmpty()) {
            InfoCard(
                title = "Activity log",
                text = "No recent activity"
            )
        } else {
            SettingsCard {
                events.forEachIndexed { index, event ->
                    val date = Date(event.timestamp)
                    val timestamp =
                        DateFormat.getMediumDateFormat(context).format(date) +
                            " • " +
                            DateFormat.getTimeFormat(context).format(date)

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            event.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            timestamp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (index != events.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPage(
    context: Context,
    automaticUpdateChecks: Boolean,
    notificationsAllowed: Boolean,
    onAutomaticUpdateChecksChange: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenAppInfo: () -> Unit,
    onInstallVerifiedUpdate: (String) -> Unit,
    installerReturnToken: Int,
    onUpdateStateChanged: () -> Unit
) {
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val helperVersion = runCatching {
        context.packageManager
            .getPackageInfo(HelperController.PACKAGE, 0)
            .versionName
    }.getOrNull()
    val updateScope = rememberCoroutineScope()
    var updateCheckRunning by remember { mutableStateOf(false) }
    var mainDownloadRunning by remember { mutableStateOf(false) }
    var helperDownloadRunning by remember { mutableStateOf(false) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    val cachedUpdate = UpdateChecker.cachedUpdate(context)
    val cachedHelperUpdate = UpdateChecker.cachedHelperUpdate(context)
    val cachedHelperRelease = UpdateChecker.cachedHelperReleaseInfo(context)
    val latestMainVersion = AppPreferences.latestReleaseVersion(context)

    LaunchedEffect(installerReturnToken) {
        if (installerReturnToken > 0) {
            updateCheckMessage = null
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoCard(
            title = "SleepManager",
            text = "Quiet on sleep. Ready on wake.\nVersion ${packageInfo?.versionName ?: "Unknown"} • Smart sleep automation for Android."
        )

        SettingsCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "What SleepManager does",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Runs your chosen sleep actions when the screen turns off, then restores only what SleepManager changed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        SectionTitle(
            title = "App details",
            subtitle = "Version and compatibility information."
        )

        SettingsCard {
            AboutInfoRow(
                label = "SleepManager",
                value = packageInfo?.versionName ?: "Unknown"
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutInfoRow(
                label = "Compatibility helper",
                value = helperVersion ?: "Not installed"
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutInfoRow(
                label = "Android",
                value = "${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}"
            )
        }

        SectionTitle(
            title = "Updates",
            subtitle = "Check GitHub releases and keep SleepManager and the optional Helper up to date."
        )

        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Automatic update checks",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Check SleepManager and Helper when you open the app, and daily in the background.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = automaticUpdateChecks,
                    onCheckedChange = feedbackChange(onAutomaticUpdateChecksChange)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            val updateStatusText =
                updateCheckMessage ?: buildString {
                    val installedMain = packageInfo?.versionName ?: "Unknown"
                    append("SleepManager ")
                    when {
                        cachedUpdate != null ->
                            append("$installedMain → ${cachedUpdate.versionName} available")
                        latestMainVersion != null ->
                            append("$installedMain • Up to date")
                        else ->
                            append("$installedMain • Not checked")
                    }

                    append("\nCompatibility Helper ")
                    when {
                        helperVersion == null && cachedHelperRelease != null ->
                            append(
                                "Not installed • ${cachedHelperRelease.versionName} available to install"
                            )
                        helperVersion == null ->
                            append("Not installed • Not checked")
                        cachedHelperUpdate != null ->
                            append(
                                "$helperVersion → ${cachedHelperUpdate.versionName} available"
                            )
                        cachedHelperRelease != null ->
                            append("$helperVersion • Up to date")
                        else ->
                            append("$helperVersion • Not checked")
                    }
                }

            AboutActionRow(
                title = "Check for updates",
                subtitle = updateStatusText,
                actionLabel = if (updateCheckRunning) "Checking…" else "Check",
                enabled = !updateCheckRunning &&
                    !mainDownloadRunning &&
                    !helperDownloadRunning,
                onClick = {
                    updateCheckRunning = true
                    updateCheckMessage = null
                    updateScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            UpdateChecker.check(
                                context = context,
                                force = true,
                                notify = false
                            )
                        }
                        updateCheckMessage = when (result) {
                            is UpdateCheckResult.Error ->
                                "Unable to check right now."
                            UpdateCheckResult.Disabled ->
                                "Automatic checks are disabled."
                            UpdateCheckResult.NotDue ->
                                "An update check is already running."
                            else -> null
                        }
                        updateCheckRunning = false
                        onUpdateStateChanged()
                    }
                }
            )

            UpdateChecker.cachedUpdate(context)?.let { update ->
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                AboutActionRow(
                    title = "SleepManager ${update.versionName}",
                    subtitle = if (update.directInstallAvailable) {
                        "Download, verify and install the signed APK."
                    } else {
                        "Direct install metadata unavailable. Open the GitHub release."
                    },
                    actionLabel = when {
                        mainDownloadRunning -> "Downloading…"
                        update.directInstallAvailable -> "Update"
                        else -> "Open"
                    },
                    enabled = !mainDownloadRunning && !helperDownloadRunning,
                    onClick = {
                        if (!update.directInstallAvailable) {
                            onOpenExternalUrl(update.releaseUrl)
                        } else {
                            mainDownloadRunning = true
                            updateCheckMessage =
                                "Downloading SleepManager ${update.versionName}…"
                            updateScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    UpdateInstaller.downloadAndVerify(
                                        context = context,
                                        update = update
                                    )
                                }

                                when (result) {
                                    is UpdateDownloadResult.Success -> {
                                        mainDownloadRunning = false
                                        updateCheckMessage =
                                            "APK verified. Opening Android installer…"
                                        onInstallVerifiedUpdate(
                                            result.apk.absolutePath
                                        )
                                    }

                                    is UpdateDownloadResult.Failure -> {
                                        mainDownloadRunning = false
                                        updateCheckMessage = result.message
                                    }
                                }
                            }
                        }
                    }
                )
            }

            val helperActionInfo =
                cachedHelperUpdate ?: if (helperVersion == null) {
                    cachedHelperRelease
                } else {
                    null
                }

            if (helperVersion == null || helperActionInfo != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                AboutActionRow(
                    title = helperActionInfo?.let {
                        "SleepManager Helper ${it.versionName}"
                    } ?: "SleepManager Helper",
                    subtitle = when {
                        helperVersion == null &&
                            helperActionInfo != null &&
                            !helperActionInfo.directInstallAvailable ->
                            "Direct install metadata unavailable. Open the GitHub release."
                        helperVersion == null ->
                            "Download, verify and install the signed Helper for Wi-Fi and Bluetooth."
                        helperActionInfo?.directInstallAvailable == true ->
                            "Download, verify and install the signed Helper APK."
                        else ->
                            "Direct install metadata unavailable. Open the GitHub release."
                    },
                    actionLabel = when {
                        helperDownloadRunning -> "Downloading…"
                        helperActionInfo != null &&
                            !helperActionInfo.directInstallAvailable -> "Open"
                        helperVersion == null -> "Install"
                        else -> "Update"
                    },
                    enabled = !mainDownloadRunning && !helperDownloadRunning,
                    onClick = {
                        if (
                            helperActionInfo != null &&
                            !helperActionInfo.directInstallAvailable
                        ) {
                            onOpenExternalUrl(helperActionInfo.releaseUrl)
                        } else {
                            helperDownloadRunning = true
                            updateCheckMessage =
                                if (helperVersion == null) {
                                    "Preparing SleepManager Helper…"
                                } else {
                                    "Downloading SleepManager Helper ${helperActionInfo?.versionName.orEmpty()}…"
                                }

                            updateScope.launch {
                                val helperInfoResult = withContext(Dispatchers.IO) {
                                    runCatching {
                                        helperActionInfo
                                            ?: UpdateChecker.fetchLatestHelperForInstall(
                                                context
                                            )
                                            ?: error(
                                                "No Helper APK is available in the latest release."
                                            )
                                    }
                                }

                                val helperInfo = helperInfoResult.getOrNull()
                                if (helperInfo == null) {
                                    helperDownloadRunning = false
                                    updateCheckMessage =
                                        helperInfoResult.exceptionOrNull()?.message
                                            ?: "Unable to find the Helper release."
                                    onUpdateStateChanged()
                                    return@launch
                                }

                                if (!helperInfo.directInstallAvailable) {
                                    helperDownloadRunning = false
                                    updateCheckMessage =
                                        "Direct install metadata is unavailable for the Helper."
                                    onUpdateStateChanged()
                                    return@launch
                                }

                                updateCheckMessage =
                                    "Downloading SleepManager Helper ${helperInfo.versionName}…"

                                val result = withContext(Dispatchers.IO) {
                                    UpdateInstaller.downloadAndVerifyHelper(
                                        context = context,
                                        update = helperInfo
                                    )
                                }

                                when (result) {
                                    is UpdateDownloadResult.Success -> {
                                        helperDownloadRunning = false
                                        updateCheckMessage =
                                            "Helper APK verified. Opening Android installer…"
                                        onInstallVerifiedUpdate(
                                            result.apk.absolutePath
                                        )
                                    }

                                    is UpdateDownloadResult.Failure -> {
                                        helperDownloadRunning = false
                                        updateCheckMessage = result.message
                                        onUpdateStateChanged()
                                    }
                                }
                            }
                        }
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= 33) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                if (notificationsAllowed) {
                    AboutInfoRow(
                        label = "Update notifications",
                        value = "Allowed"
                    )
                } else {
                    AboutActionRow(
                        title = "Update notifications",
                        subtitle = "Allow Android notifications for new releases.",
                        actionLabel = "Enable",
                        onClick = onRequestNotificationPermission
                    )
                }
            }

            if (BuildConfig.VERSION_NAME.contains("-dev")) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                AboutActionRow(
                    title = "Simulate update",
                    subtitle = "Developer test: pretend SleepManager 0.5.2 is available.",
                    actionLabel = "Simulate",
                    onClick = {
                        val simulated =
                            UpdateChecker.simulateAvailableUpdate(
                                context = context,
                                versionName = "0.5.2"
                            )
                        updateCheckMessage =
                            "Simulated SleepManager ${simulated.versionName} update."
                        onUpdateStateChanged()
                    }
                )
            }
        }

        SectionTitle(
            title = "Support & project",
            subtitle = "Useful links for troubleshooting and development."
        )

        SettingsCard {
            AboutActionRow(
                title = "Source code",
                subtitle = "View SleepManager on GitHub",
                actionLabel = "Open",
                onClick = {
                    onOpenExternalUrl("https://github.com/Darkaxt/SleepManager")
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutActionRow(
                title = "Report an issue",
                subtitle = "Open the GitHub issue tracker",
                actionLabel = "Open",
                onClick = {
                    onOpenExternalUrl("https://github.com/Darkaxt/SleepManager/issues")
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutActionRow(
                title = "Android app info",
                subtitle = "Permissions, battery and storage settings",
                actionLabel = "Open",
                onClick = { onOpenAppInfo() }
            )
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = feedbackClick(onClick),
            enabled = enabled
        ) {
            Text(actionLabel)
        }
    }
}

private fun formatDuration(valueMs: Long): String =
    when (valueMs) {
        0L -> "Immediate"
        3000L -> "3 s"
        5000L -> "5 s"
        10000L -> "10 s"
        60000L -> "1 min"
        300000L -> "5 min"
        600000L -> "10 min"
        1800000L -> "30 min"
        else -> "${valueMs / 1000}s"
    }

private fun formatTime(minutes: Int): String {
    val safe = minutes.coerceIn(0, 1439)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

@Composable
private fun InfoCard(
    title: String,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            if (actionLabel != null && onAction != null) {
                TextButton(onClick = feedbackClick(onAction)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun BehaviorCard(
    wifi: Boolean,
    bluetooth: Boolean,
    syncthing: Boolean,
    tailscale: Boolean,
    jamesDsp: Boolean,
    basicSync: Boolean,
    thorProtection: Boolean,
    sleepGraceMs: Long,
    advancedConditions: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSleepAction =
        wifi || bluetooth || syncthing || tailscale || jamesDsp || basicSync

    val sleepLines = buildList {
        if (sleepGraceMs > 0L && hasSleepAction) {
            add("Wait ${formatDuration(sleepGraceMs)}")
        }
        advancedConditions.forEach { add("Only if $it") }
        if (syncthing) add("Pause Syncthing‑Fork")
        if (tailscale) add("Disconnect Tailscale")
        if (jamesDsp) add("Power off JamesDSP")
        if (basicSync) add("Stop BasicSync when active")
        if (wifi) add("Wi‑Fi off")
        if (bluetooth) add("Bluetooth off")
        if (!hasSleepAction) add("No sleep actions selected")
        if (thorProtection) add("Thor closed-lid protection")
    }

    val wakeLines = buildList {
        if (wifi) add("Restore Wi‑Fi")
        if (bluetooth) add("Restore Bluetooth")
        if (syncthing) add("Resume Syncthing‑Fork")
        if (tailscale) add("Restore Tailscale if SleepManager disconnected it")
        if (jamesDsp) add("Restore JamesDSP")
        if (basicSync) add("Restore BasicSync previous mode")
    }

    val compactSleepSummary = buildList {
        if (sleepGraceMs > 0L && hasSleepAction) add(formatDuration(sleepGraceMs))
        if (wifi) add("Wi‑Fi")
        if (bluetooth) add("Bluetooth")
        if (syncthing) add("Syncthing")
        if (tailscale) add("Tailscale")
        if (jamesDsp) add("JamesDSP")
        if (basicSync) add("BasicSync")
        if (advancedConditions.isNotEmpty()) {
            add("${advancedConditions.size} condition${if (advancedConditions.size > 1) "s" else ""}")
        }
        if (!hasSleepAction) add("No actions")
    }.joinToString(" • ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Current behavior",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        compactSleepSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = feedbackClick { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "Details")
                }
            }

            if (expanded) {
                BehaviorGroup(
                    title = "When screen turns OFF",
                    lines = sleepLines
                )

                BehaviorGroup(
                    title = "When screen turns ON",
                    lines = wakeLines.ifEmpty { listOf("Nothing to restore") }
                )

                if (wifi || bluetooth) {
                    Text(
                        "Only states changed by SleepManager are restored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (thorProtection) {
                    Text(
                        "Thor false wakes with the lid closed are returned to sleep without normal wake restoration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
@Composable
private fun BehaviorGroup(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        lines.forEach {
            Text(
                "• $it",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LastActivityCard(
    context: Context,
    onViewLog: () -> Unit,
    onCopyLog: () -> Unit
) {
    val event = AppPreferences.lastEvent(context)
    val time = AppPreferences.lastEventTime(context)

    val timeText = if (time > 0L) {
        val date = Date(time)
        DateFormat.getMediumDateFormat(context).format(date) +
            " • " + DateFormat.getTimeFormat(context).format(date)
    } else {
        null
    }

    val isStructured = event.contains(" → ")
    val phase = if (isStructured) event.substringBefore(" → ") else null
    val actions = if (isStructured) {
        event.substringAfter(" → ").split(" · ").filter { it.isNotBlank() }
    } else emptyList()

    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Last activity",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (phase != null) {
            Text(phase, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

            actions.forEach { action ->
                val subject = when {
                    action.startsWith("Wi‑Fi ") -> "Wi‑Fi"
                    action.startsWith("Bluetooth ") -> "Bluetooth"
                    action.startsWith("Syncthing ") -> "Syncthing"
                    action.startsWith("Tailscale ") -> "Tailscale"
                    else -> null
                }
                val detail = when (subject) {
                    "Wi‑Fi" -> action.removePrefix("Wi‑Fi ").replaceFirstChar { it.uppercase() }
                    "Bluetooth" -> action.removePrefix("Bluetooth ").replaceFirstChar { it.uppercase() }
                    "Syncthing" -> action.removePrefix("Syncthing ").replaceFirstChar { it.uppercase() }
                    "Tailscale" -> action.removePrefix("Tailscale ").replaceFirstChar { it.uppercase() }
                    else -> action
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (subject != null) {
                        Text("$subject ·", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (detail.equals("Unchanged", ignoreCase = true)) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        } else {
            Text(event, style = MaterialTheme.typography.bodyMedium)
        }

        timeText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = feedbackClick(onViewLog),
                modifier = Modifier.weight(1f)
            ) {
                Text("View log")
            }

            OutlinedButton(
                onClick = feedbackClick(onCopyLog),
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy log")
            }
        }
    }
}

@Composable
private fun ActivityLogDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val events = EventHistoryStore.recent(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activity log") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (events.isEmpty()) {
                    item {
                        Text(
                            "No recent activity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(events.size) { index ->
                        val event = events[index]
                        val date = Date(event.timestamp)
                        val timestamp =
                            DateFormat.getMediumDateFormat(context).format(date) +
                                " • " +
                                DateFormat.getTimeFormat(context).format(date)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                event.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                timestamp,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = feedbackClick(onDismiss)) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SyncthingTargetDialog(
    targets: List<SyncthingController.Target>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (SyncthingController.Target) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Syncthing‑Fork build") },
        text = {
            Column {
                targets.forEach { target ->
                    TextButton(
                        onClick = feedbackClick { onSelect(target) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                target.displayName,
                                modifier = Modifier.weight(1f)
                            )
                            if (target.packageName == selected) {
                                Text(
                                    "Selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = feedbackClick(onDismiss)) {
                Text("Close")
            }
        }
    )
}
