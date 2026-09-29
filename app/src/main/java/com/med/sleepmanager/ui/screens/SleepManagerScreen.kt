package com.med.sleepmanager.ui.screens

import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.MainActivity
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.protection.ThorPowerButtonMonitor
import com.med.sleepmanager.R
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.sync.basicSyncCompletionState
import com.med.sleepmanager.sync.ManagedSyncProviders
import com.med.sleepmanager.sync.SyncMaintenanceScheduler
import com.med.sleepmanager.sync.SyncCompletionState
import com.med.sleepmanager.ui.AppSection
import com.med.sleepmanager.ui.components.BehaviorCard
import com.med.sleepmanager.ui.components.ClamshellOptionsCard
import com.med.sleepmanager.ui.components.CompactIntegrationRow
import com.med.sleepmanager.ui.components.CompactSideRail
import com.med.sleepmanager.ui.components.LastActivityCard
import com.med.sleepmanager.ui.components.OnboardingCard
import com.med.sleepmanager.ui.components.SectionTitle
import com.med.sleepmanager.ui.components.SettingRow
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.components.StatusCard
import com.med.sleepmanager.ui.components.SyncthingTargetDialog
import com.med.sleepmanager.ui.components.UpdateAvailableCard
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.ui.iconRes
import com.med.sleepmanager.ui.label
import com.med.sleepmanager.update.UpdateChecker
import com.med.sleepmanager.update.UpdateCheckScheduler
import com.med.sleepmanager.update.UpdateNotifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.med.sleepmanager.ui.feedbackChange

@OptIn(ExperimentalMaterial3Api::class)
    @Composable
    internal fun MainActivity.SleepManagerScreen(
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
        var aboutScrollTarget by remember {
            mutableStateOf(
                if (openUpdatesOnLaunch) {
                    AboutScrollTarget.UPDATES
                } else {
                    null
                }
            )
        }
        var aboutScrollRequestId by remember {
            mutableStateOf(if (openUpdatesOnLaunch) 1 else 0)
        }
        var advancedScrollTarget by remember {
            mutableStateOf<AdvancedScrollTarget?>(null)
        }
        var advancedScrollRequestId by remember {
            mutableStateOf(0)
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

        fun navigateToAbout(target: AboutScrollTarget) {
            aboutScrollTarget = target
            aboutScrollRequestId++
            currentSection = AppSection.ABOUT
        }

        fun navigateToAdvanced(target: AdvancedScrollTarget) {
            advancedScrollTarget = target
            advancedScrollRequestId++
            currentSection = AppSection.ADVANCED
        }

        fun navigateToActivityLog() {
            currentSection = AppSection.ACTIVITY_LOG
            drawerScope.launch {
                activityListState.animateScrollToItem(0)
            }
        }

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
        var batterySaverActionEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageBatterySaver(this))
        }
        var chargingSeparationWithLidEnabled by remember(refreshToken) {
            mutableStateOf(
                AppPreferences.manageChargingSeparationWithLid(this)
            )
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
        val currentBatterySaverState = remember(refreshToken) {
            DeviceControlController.batterySaverEnabled(this)
        }

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
        val closedLidPowerSupported = remember(refreshToken) {
            ThorPowerButtonMonitor.isSupported()
        }
        val deviceControlCapabilities =
            currentDeviceControlCapabilities
        val batterySaverControlSupported =
            deviceControlCapabilities?.batterySaverControl == true
        val chargingSeparationSupported =
            thorProtectionSupported &&
                deviceControlCapabilities?.chargingSeparationControl == true
        val backgroundReliability =
            currentBackgroundReliability
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

                        AppSection.entries.forEach { section ->
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
                            managerEnabled = AppPreferences.isEnabled(this@SleepManagerScreen)
                            activityRefreshToken++
                        }
                    )
                }

                if (availableUpdate != null || availableHelperUpdate != null) {
                    item {
                        UpdateAvailableCard(
                            update = availableUpdate,
                            helperUpdate = availableHelperUpdate,
                            onUpdate = {
                                navigateToAbout(AboutScrollTarget.UPDATES)
                            },
                            onReleaseNotes =
                                (
                                    availableUpdate?.releaseUrl
                                        ?: availableHelperUpdate?.releaseUrl
                                )?.let { url ->
                                    { openReleaseUrl(url) }
                                }
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
                            onGetHelper = {
                                navigateToAbout(AboutScrollTarget.HELPER)
                            },
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
                                HelperController.forgetPendingState(this@SleepManagerScreen)
                                SleepCycleStore.clear(this@SleepManagerScreen)
                                AppPreferences.recordEvent(
                                    this@SleepManagerScreen,
                                    "Recovery → pending restore forgotten"
                                )
                                activityRefreshToken++
                            }
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = "System controls",
                        subtitle = "Choose which system features SleepManager manages during sleep."
                    )
                }

                item {
                    SettingsCard(
                        modifier = Modifier.testTag("system_controls_card")
                    ) {
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
                                AppPreferences.setManageWifi(this@SleepManagerScreen, it)
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
                                AppPreferences.setManageBluetooth(this@SleepManagerScreen, it)
                            }
                        )

                        if (batterySaverControlSupported) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            SettingRow(
                                icon = R.drawable.ic_battery,
                                title = "Battery Saver",
                                subtitle = "Enable during sleep and restore the previous state on wake.",
                                status =
                                    "Current state: ${if (currentBatterySaverState) "ON" else "OFF"}",
                                checked = batterySaverActionEnabled,
                                enabled = true,
                                onCheckedChange = {
                                    batterySaverActionEnabled = it
                                    AppPreferences.setManageBatterySaver(
                                        this@SleepManagerScreen,
                                        it
                                    )
                                    if (it) {
                                        batterySaverMode =
                                            AppPreferences.BATTERY_SAVER_IGNORE
                                    }
                                    refreshRunningService()
                                }
                            )
                        }

                    }
                }

                item {
                    AnimatedVisibility(visible = !helperInstalled) {
                        InfoCard(
                            title = "Compatibility helper not installed",
                            text = "The helper controls Wi‑Fi and Bluetooth without root or Shizuku. It has no launcher icon and runs only when SleepManager asks it to.",
                            actionLabel = "Install Helper",
                            onAction = {
                                navigateToAbout(AboutScrollTarget.HELPER)
                            }
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
                        SleepGraceSelector(
                            valueMs = sleepGraceMs,
                            customDelayEnabled = customDelayEnabled,
                            customDelayMs = customDelayMs,
                            onChange = { value ->
                                sleepGraceMs = value
                                AppPreferences.setSleepGraceMs(this@SleepManagerScreen, value)
                            },
                            onCustom = {
                                navigateToAdvanced(
                                    AdvancedScrollTarget.CUSTOM_DELAY
                                )
                            }
                        )
                    }
                }

                if (thorProtectionSupported) {
                    item {
                        SectionTitle(
                            title = "Clamshell options",
                            subtitle = "Extra controls for devices with a compatible lid sensor."
                        )
                    }

                    item {
                        ClamshellOptionsCard(
                            closedLidProtectionEnabled =
                                thorProtectionEnabled && thorAdminActive,
                            chargingSeparationSupported = chargingSeparationSupported,
                            chargingSeparationEnabled = chargingSeparationWithLidEnabled,
                            sleepOnExternalDisplayDisconnect = thorDockDisconnectSleeps,
                            powerButtonSleepSupported = closedLidPowerSupported,
                            powerButtonSleepsWithLidClosed = thorClosedPowerSleeps,
                            onClosedLidProtectionChange = { enabled ->
                                setThorProtectionEnabled(enabled)
                                thorProtectionEnabled =
                                    AppPreferences.manageThorProtection(
                                        this@SleepManagerScreen
                                    )
                                activityRefreshToken++
                            },
                            onChargingSeparationChange = {
                                chargingSeparationWithLidEnabled = it
                                AppPreferences.setManageChargingSeparationWithLid(
                                    this@SleepManagerScreen,
                                    it
                                )
                                refreshRunningService()
                            },
                            onSleepOnExternalDisplayDisconnectChange = {
                                thorDockDisconnectSleeps = it
                                AppPreferences.setThorDockDisconnectSleeps(
                                    this@SleepManagerScreen,
                                    it
                                )
                            },
                            onPowerButtonSleepsWithLidClosedChange = {
                                thorClosedPowerSleeps = it
                                AppPreferences.setThorClosedPowerSleeps(
                                    this@SleepManagerScreen,
                                    it
                                )
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
                    SettingsCard(
                        modifier = Modifier.testTag("app_integrations_card")
                    ) {
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
                                AppPreferences.setManageSyncthing(this@SleepManagerScreen, it)

                                if (it) {
                                    Toast.makeText(
                                        this@SleepManagerScreen,
                                        "Enable Settings → Behaviour → Service control by broadcast in Syncthing-Fork.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else if (managerEnabled) {
                                    restoreSyncthingTransactionNow()
                                    SleepCycleStore.completeIfRestored(this@SleepManagerScreen)
                                }
                            },
                            onOpen = if (selectedTarget != null) {
                                { SyncthingController.open(this@SleepManagerScreen) }
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
                                    this@SleepManagerScreen,
                                    it
                                )
                            },
                            onOpen = if (tailscaleInstalled) {
                                { TailscaleController.open(this@SleepManagerScreen) }
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
                                    this@SleepManagerScreen,
                                    it
                                )

                                if (!it && managerEnabled) {
                                    restoreJamesDspTransactionNow()
                                    SleepCycleStore.completeIfRestored(this@SleepManagerScreen)
                                }
                            },
                            onOpen = if (jamesDspTarget != null) {
                                { JamesDspController.open(this@SleepManagerScreen) }
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
                                if (BasicSyncController.supportsStateApi(this@SleepManagerScreen)) {
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
                                                    this@SleepManagerScreen
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
                                    this@SleepManagerScreen,
                                    it
                                )

                                if (it) {
                                    Toast.makeText(
                                        this@SleepManagerScreen,
                                        if (BasicSyncController.supportsStateApi(this@SleepManagerScreen)) {
                                            "Enable Allow remote control in BasicSync. SleepManager will preserve and restore BasicSync's previous mode."
                                        } else {
                                            "Enable Allow remote control in BasicSync. This BasicSync version uses legacy STOP → Auto mode behavior."
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else if (managerEnabled) {
                                    restoreBasicSyncTransactionNow()
                                    SleepCycleStore.completeIfRestored(this@SleepManagerScreen)
                                    // BasicSync is the only completion-aware
                                    // provider, so no periodic alarm should
                                    // remain armed while its integration is off.
                                    SyncMaintenanceScheduler.cancel(
                                        this@SleepManagerScreen
                                    )
                                    // Also let the running service restore the
                                    // original state owned by Sync then stop.
                                    refreshRunningService()
                                }

                                if (it && managerEnabled) {
                                    refreshRunningService()
                                }
                            },
                            onOpen = if (basicSyncInstalled) {
                                { BasicSyncController.open(this@SleepManagerScreen) }
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
                        batterySaver =
                            batterySaverActionEnabled && batterySaverControlSupported,
                        syncthing = syncthingEnabled && selectedTarget != null,
                        tailscale = tailscaleEnabled && tailscaleInstalled,
                        jamesDsp = jamesDspEnabled && jamesDspTarget != null,
                        basicSync = basicSyncEnabled && basicSyncInstalled,
                        closedLidProtection = thorProtectionEnabled && thorAdminActive,
                        chargingSeparationWithLid =
                            chargingSeparationWithLidEnabled && chargingSeparationSupported,
                        sleepOnExternalDisplayDisconnect =
                            thorProtectionSupported && thorDockDisconnectSleeps,
                        powerButtonSleepsWithLidClosed =
                            closedLidPowerSupported && thorClosedPowerSleeps,
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
                        },
                        periodicSyncWhileSleeping = periodicSyncWhileSleeping,
                        syncThenStopOnSleepWake = syncThenStopOnSleepWake
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
                        context = this@SleepManagerScreen,
                        onViewLog = { navigateToActivityLog() },
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
                                        this@SleepManagerScreen
                                    ),
                                onPeriodicSyncWhileSleepingChange = {
                                    periodicSyncWhileSleeping = it
                                    AppPreferences.setPeriodicSyncWhileSleeping(
                                        this@SleepManagerScreen,
                                        it
                                    )
                                    if (!it) {
                                        SyncMaintenanceScheduler.cancel(
                                            this@SleepManagerScreen
                                        )
                                    }
                                },
                                onSyncThenStopOnSleepWakeChange = {
                                    syncThenStopOnSleepWake = it
                                    AppPreferences.setSyncThenStopOnSleepWake(
                                        this@SleepManagerScreen,
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
                                            this@SleepManagerScreen,
                                            enabled
                                        )

                                        if (!enabled) {
                                            sleepGraceMs = 0L
                                            AppPreferences.setSleepGraceMs(
                                                this@SleepManagerScreen,
                                                0L
                                            )
                                        }
                                    }
                                },
                                onCustomDelayChange = {
                                    customDelayMs = it
                                    AppPreferences.setCustomDelayMs(this@SleepManagerScreen, it)
                                },
                                onBatteryConditionEnabledChange = {
                                    batteryConditionEnabled = it
                                    AppPreferences.setBatteryConditionEnabled(this@SleepManagerScreen, it)
                                },
                                onBatteryBelowPercentChange = {
                                    batteryBelowPercent = it
                                    AppPreferences.setBatteryBelowPercent(this@SleepManagerScreen, it)
                                },
                                onNotChargingOnlyChange = {
                                    notChargingOnly = it
                                    AppPreferences.setNotChargingOnly(this@SleepManagerScreen, it)
                                },
                                onBatterySaverModeChange = {
                                    batterySaverMode = it
                                    AppPreferences.setBatterySaverMode(this@SleepManagerScreen, it)
                                    if (
                                        it !=
                                        AppPreferences.BATTERY_SAVER_IGNORE
                                    ) {
                                        batterySaverActionEnabled = false
                                    }
                                },
                                onScheduleEnabledChange = {
                                    scheduleEnabled = it
                                    AppPreferences.setScheduleEnabled(this@SleepManagerScreen, it)
                                },
                                onPickScheduleStart = {
                                    showTimePicker(scheduleStartMinutes) { value ->
                                        scheduleStartMinutes = value
                                        AppPreferences.setScheduleStartMinutes(
                                            this@SleepManagerScreen,
                                            value
                                        )
                                    }
                                },
                                onPickScheduleEnd = {
                                    showTimePicker(scheduleEndMinutes) { value ->
                                        scheduleEndMinutes = value
                                        AppPreferences.setScheduleEndMinutes(
                                            this@SleepManagerScreen,
                                            value
                                        )
                                    }
                                },
                                scrollTarget = advancedScrollTarget,
                                scrollRequestId = advancedScrollRequestId,
                                onScrollTargetConsumed = {
                                    advancedScrollTarget = null
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
                                context = this@SleepManagerScreen,
                                onCopyLog = { copyDiagnostics() }
                            )
                        }
                    }

                    AppSection.ABOUT -> {
                        item {
                            AboutPage(
                                context = this@SleepManagerScreen,
                                backgroundReliability = backgroundReliability,
                                automaticUpdateChecks = automaticUpdateChecks,
                                notificationsAllowed = updateNotificationsAllowed,
                                onAutomaticUpdateChecksChange = { enabled ->
                                    automaticUpdateChecks = enabled
                                    AppPreferences.setAutomaticUpdateChecks(
                                        this@SleepManagerScreen,
                                        enabled
                                    )
                                    UpdateCheckScheduler.sync(this@SleepManagerScreen)
                                    if (enabled) {
                                        UpdateChecker.checkOnForegroundAsync(
                                            this@SleepManagerScreen,
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
                                onOpenBatteryOptimization = {
                                    openBatteryOptimizationSettings()
                                },
                                onOpenUnusedAppRestrictions = {
                                    openUnusedAppRestrictionsSettings()
                                },
                                onInstallVerifiedUpdate = { apkPath ->
                                    installVerifiedUpdate(apkPath)
                                },
                                installerReturnToken = installerReturnToken,
                                onUpdateStateChanged = {
                                    activityRefreshToken++
                                },
                                scrollTarget = aboutScrollTarget,
                                scrollRequestId = aboutScrollRequestId,
                                onScrollTargetConsumed = {
                                    aboutScrollTarget = null
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
