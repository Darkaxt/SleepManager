package com.med.sleepmanager.ui.screens

import android.content.Context
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.RaOfflineProxyController
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyQueueState
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.protection.LidMonitor
import com.med.sleepmanager.protection.PmicPowerButtonMonitor
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
import com.med.sleepmanager.ui.components.CompactSupportedAppRow
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
import com.med.sleepmanager.ui.labelRes
import com.med.sleepmanager.ui.subtitleRes
import com.med.sleepmanager.ui.titleRes
import com.med.sleepmanager.update.UpdateChecker
import com.med.sleepmanager.update.UpdateCheckScheduler
import com.med.sleepmanager.update.UpdateNotifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.med.sleepmanager.ui.feedbackChange
import com.med.sleepmanager.ui.state.SleepManagerUiState

private class IntegrationUiRow(
    val installed: Boolean,
    val icon: Int,
    val title: String,
    val preserveIconColors: Boolean = false,
    val content: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
    @Composable
    internal fun SleepManagerScreen(
        uiState: SleepManagerUiState,
        context: Context,
        openUpdatesOnLaunch: Boolean,
        useSystemColors: Boolean,
        onRefreshRequested: () -> Unit,
        onServiceRefreshRequested: () -> Unit,
        onManagerEnabledChange: (Boolean) -> Unit,
        onManageWifiChange: (Boolean) -> Unit,
        onManageBluetoothChange: (Boolean) -> Unit,
        onManageBatterySaverChange: (Boolean) -> Unit,
        onChargingSeparationWithLidChange: (Boolean) -> Unit,
        onManageSyncthingChange: (Boolean) -> Unit,
        onManageTailscaleChange: (Boolean) -> Unit,
        onManageJamesDspChange: (Boolean) -> Unit,
        onManageBasicSyncChange: (Boolean) -> Unit,
        onManageRaOfflineProxyChange: (Boolean) -> Unit,
        onPeriodicSyncWhileSleepingChange: (Boolean) -> Unit,
        onSyncThenStopOnSleepWakeChange: (Boolean) -> Unit,
        onSleepGraceChange: (Long) -> Unit,
        onCustomDelayEnabledChange: (Boolean) -> Unit,
        onCustomDelayChange: (Long) -> Unit,
        onBatteryConditionEnabledChange: (Boolean) -> Unit,
        onBatteryBelowPercentChange: (Int) -> Unit,
        onNotChargingOnlyChange: (Boolean) -> Unit,
        onBatterySaverModeChange: (String) -> Unit,
        onScheduleEnabledChange: (Boolean) -> Unit,
        onScheduleStartMinutesChange: (Int) -> Unit,
        onScheduleEndMinutesChange: (Int) -> Unit,
        onAutomaticUpdateChecksChange: (Boolean) -> Unit,
        onFinishSetupRequested: () -> Unit,
        onFinishAppRequested: () -> Unit,
        onClosedLidAdminActiveRequested: () -> Boolean,
        onClosedLidProtectionChangeRequested: (Boolean) -> Unit,
        onDockDisconnectSleepsChange: (Boolean) -> Unit,
        onClosedLidPowerSleepsChange: (Boolean) -> Unit,
        onCopyDiagnosticsRequested: () -> Unit,
        onOpenExternalUrlRequested: (String) -> Unit,
        onOpenAppInfoRequested: () -> Unit,
        onOpenBatteryOptimizationRequested: () -> Unit,
        onOpenRaOfflineProxySettingsRequested: () -> Unit,
        onOpenUnusedAppRestrictionsRequested: () -> Unit,
        onRequestUpdateNotificationPermissionRequested: () -> Unit,
        onInstallVerifiedUpdateRequested: (String) -> Unit,
        onCanScheduleExactAlarmsRequested: () -> Boolean,
        onRequestExactAlarmAccessRequested: () -> Unit,
        onShowTimePickerRequested: (Int, (Int) -> Unit) -> Unit,
        onRestoreSyncthingRequested: () -> Unit,
        onRestoreJamesDspRequested: () -> Unit,
        onRestoreBasicSyncRequested: () -> Unit,
        onUseSystemColorsChanged: (Boolean) -> Unit
    ) {
        val refreshToken = uiState.activityRefreshToken
        var showTargetDialog by remember { mutableStateOf(false) }
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

        val managerEnabled = uiState.managerEnabled
        val wifiEnabled = uiState.manageWifiEnabled
        val bluetoothEnabled = uiState.manageBluetoothEnabled
        val batterySaverActionEnabled = uiState.manageBatterySaverEnabled
        val chargingSeparationWithLidEnabled =
            uiState.chargingSeparationWithLidEnabled
        val syncthingEnabled = uiState.manageSyncthingEnabled
        val tailscaleEnabled = uiState.manageTailscaleEnabled
        val jamesDspEnabled = uiState.manageJamesDspEnabled
        val basicSyncEnabled = uiState.manageBasicSyncEnabled
        val raOfflineProxyEnabled =
            uiState.manageRaOfflineProxyEnabled
        val periodicSyncWhileSleeping = uiState.periodicSyncWhileSleeping
        val syncThenStopOnSleepWake = uiState.syncThenStopOnSleepWake
        val closedLidProtectionEnabled = uiState.closedLidProtectionEnabled
        val dockDisconnectSleeps = uiState.dockDisconnectSleeps
        val closedLidPowerSleeps = uiState.closedLidPowerSleeps
        val sleepGraceMs = uiState.sleepGraceMs
        val customDelayEnabled = uiState.customDelayEnabled
        val customDelayMs = uiState.customDelayMs
        val batteryConditionEnabled = uiState.batteryConditionEnabled
        val batteryBelowPercent = uiState.batteryBelowPercent
        val notChargingOnly = uiState.notChargingOnly
        val batterySaverMode = uiState.batterySaverMode
        val scheduleEnabled = uiState.scheduleEnabled
        val scheduleStartMinutes = uiState.scheduleStartMinutes
        val scheduleEndMinutes = uiState.scheduleEndMinutes
        val effectiveSleepDelayMs =
            if (customDelayEnabled) customDelayMs else sleepGraceMs
        val currentBatterySaverState = uiState.currentBatterySaverState

        val setupComplete = remember(refreshToken) {
            AppPreferences.isSetupComplete(context)
        }

        val helperInstalled = remember(refreshToken) {
            HelperController.isInstalled(context)
        }
        val helperVersion = remember(refreshToken) {
            runCatching {
                context.packageManager.getPackageInfo(HelperController.PACKAGE, 0).versionName
            }.getOrNull()
        }
        val targets = remember(refreshToken) {
            SyncthingController.installedTargets(context)
        }
        val selectedTarget = remember(refreshToken) {
            SyncthingController.selectedTarget(context)
        }
        val tailscaleInstalled = remember(refreshToken) {
            TailscaleController.isInstalled(context)
        }
        val tailscaleVersion = remember(refreshToken) {
            TailscaleController.versionName(context)
        }
        val jamesDspTarget = remember(refreshToken) {
            JamesDspController.selectedTarget(context)
        }
        val basicSyncInstalled = remember(refreshToken) {
            BasicSyncController.isInstalled(context)
        }
        val basicSyncVersion = remember(refreshToken) {
            BasicSyncController.versionName(context)
        }
        val raOfflineProxyInstalled = remember(refreshToken) {
            RaOfflineProxyController.isInstalled(context)
        }
        val raOfflineProxyVersion = remember(refreshToken) {
            RaOfflineProxyController.versionName(context)
        }
        val raOfflineProxyProviderAvailable =
            remember(refreshToken) {
                RaOfflineProxyController.providerAvailable(context)
            }
        val raOfflineProxyControlPermission =
            remember(refreshToken) {
                RaOfflineProxyController.hasControlPermission(context)
            }
        val raOfflineProxyBatteryUnrestricted =
            remember(refreshToken) {
                RaOfflineProxyController.isBatteryUnrestricted(context)
            }
        val raOfflineProxyStatus =
            uiState.currentRaOfflineProxyStatus
        val raOfflineProxyStatusProbeComplete =
            uiState.raOfflineProxyStatusProbeComplete
        val raOfflineProxyApiCompatible =
            raOfflineProxyStatus?.version ==
                RaOfflineProxyController.SUPPORTED_API_VERSION
        val raOfflineProxyReady =
            raOfflineProxyInstalled &&
                raOfflineProxyProviderAvailable &&
                raOfflineProxyControlPermission &&
                raOfflineProxyBatteryUnrestricted &&
                raOfflineProxyApiCompatible
        val closedLidProtectionSupported = remember(refreshToken) {
            LidMonitor.isSupported()
        }
        val expectedThorClamshell = remember {
            Build.MODEL.contains("thor", ignoreCase = true) ||
                (
                    Build.MANUFACTURER.contains("ayn", ignoreCase = true) &&
                        Build.DEVICE.contains("thor", ignoreCase = true)
                )
        }
        val closedLidPowerSupported = remember(refreshToken) {
            PmicPowerButtonMonitor.isSupported()
        }
        val deviceControlCapabilities =
            uiState.currentDeviceControlCapabilities
        val batterySaverControlSupported =
            deviceControlCapabilities?.batterySaverControl == true
        val chargingSeparationSupported =
            closedLidProtectionSupported &&
                deviceControlCapabilities?.chargingSeparationControl == true
        val backgroundReliability =
            uiState.currentBackgroundReliability
        val closedLidAdminActive = remember(refreshToken) {
            onClosedLidAdminActiveRequested()
        }
        val batteryDashboard = remember(refreshToken) {
            BatterySleepStore.dashboard(context)
        }
        val batteryStats = remember(refreshToken) {
            BatterySleepStore.stats(context)
        }
        val restoreProblem = remember(refreshToken) {
            SleepCycleStore.restoreProblem(context)
        }
        val automaticUpdateChecks = uiState.automaticUpdateChecks
        val availableUpdate = remember(refreshToken) {
            UpdateChecker.cachedUpdate(context)
        }
        val availableHelperUpdate = remember(refreshToken) {
            UpdateChecker.cachedHelperUpdate(context)
        }
        val updateNotificationsAllowed = remember(refreshToken) {
            UpdateNotifier.notificationsAllowed(context)
        }

        if (showTargetDialog) {
            SyncthingTargetDialog(
                targets = targets,
                selected = selectedTarget?.packageName,
                onDismiss = { showTargetDialog = false },
                onSelect = { target ->
                    val previous = SyncthingController.selectedTarget(context)?.packageName

                    if (
                        managerEnabled &&
                        syncthingEnabled &&
                        previous != null &&
                        previous != target.packageName
                    ) {
                        val change = SleepCycleStore.connectorChange(
                            context,
                            SyncthingConnector.id
                        )
                        if (
                            SyncthingConnector.restoreTargetPackage(
                                change?.restoreToken
                            ) == previous
                        ) {
                            onRestoreSyncthingRequested()
                            SleepCycleStore.completeIfRestored(context)
                        }
                    }

                    SyncthingController.select(context, target.packageName)
                    showTargetDialog = false
                    onRefreshRequested()
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
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.section_home_subtitle),
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
                                label = { Text(stringResource(section.labelRes)) },
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
                                    stringResource(R.string.use_system_colors),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(
                                        if (Build.VERSION.SDK_INT >= 31) {
                                            R.string.material_you
                                        } else {
                                            R.string.requires_android_12
                                        }
                                    ),
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
                                        contentDescription = stringResource(R.string.open_navigation)
                                    )
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    stringResource(currentSection.titleRes),
                                    modifier = Modifier.testTag("top_app_title"),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(currentSection.subtitleRes),
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
                            onManagerEnabledChange(!managerEnabled)
                            onRefreshRequested()
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
                                    { onOpenExternalUrlRequested(url) }
                                }
                        )
                    }
                }

                if (!setupComplete) {
                    item {
                        OnboardingCard(
                            syncthingTarget = selectedTarget,
                            tailscaleInstalled = tailscaleInstalled,
                            jamesDspTarget = jamesDspTarget,
                            basicSyncInstalled = basicSyncInstalled,
                            raOfflineProxyInstalled = raOfflineProxyInstalled
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = stringResource(R.string.stats_battery),
                        subtitle = stringResource(R.string.home_battery_description)
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
                                HelperController.forgetPendingState(context)
                                SleepCycleStore.clear(context)
                                DiagnosticsStateStore.recordEvent(
                                    context,
                                    "Recovery → pending restore forgotten"
                                )
                                onRefreshRequested()
                            }
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = stringResource(R.string.home_system_controls),
                        subtitle = stringResource(R.string.home_system_controls_description)
                    )
                }

                item {
                    SettingsCard(
                        modifier = Modifier.testTag("system_controls_card")
                    ) {
                        SettingRow(
                            icon = R.drawable.ic_wifi,
                            title = stringResource(R.string.wifi),
                            subtitle = stringResource(
                                if (helperInstalled) {
                                    R.string.home_radio_sleep_description
                                } else {
                                    R.string.home_helper_required
                                }
                            ),
                            status = if (helperInstalled) {
                                uiState.currentWifiState?.let {
                                    stringResource(
                                        if (it) {
                                            R.string.home_current_state_on
                                        } else {
                                            R.string.home_current_state_off
                                        }
                                    )
                                } ?: stringResource(R.string.home_current_state_checking)
                            } else {
                                null
                            },
                            checked = wifiEnabled,
                            enabled = helperInstalled,
                            onCheckedChange = {
                                onManageWifiChange(it)
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        SettingRow(
                            icon = R.drawable.ic_bluetooth,
                            title = stringResource(R.string.bluetooth),
                            subtitle = stringResource(
                                if (helperInstalled) {
                                    R.string.home_radio_sleep_description
                                } else {
                                    R.string.home_helper_required
                                }
                            ),
                            status = if (helperInstalled) {
                                uiState.currentBluetoothState?.let {
                                    stringResource(
                                        if (it) {
                                            R.string.home_current_state_on
                                        } else {
                                            R.string.home_current_state_off
                                        }
                                    )
                                } ?: stringResource(R.string.home_current_state_checking)
                            } else {
                                null
                            },
                            checked = bluetoothEnabled,
                            enabled = helperInstalled,
                            onCheckedChange = {
                                onManageBluetoothChange(it)
                            }
                        )

                        if (batterySaverControlSupported) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            SettingRow(
                                icon = R.drawable.ic_battery,
                                title = stringResource(R.string.battery_saver),
                                subtitle = stringResource(
                                    R.string.home_battery_saver_description
                                ),
                                status = stringResource(
                                    if (currentBatterySaverState) {
                                        R.string.home_current_state_on
                                    } else {
                                        R.string.home_current_state_off
                                    }
                                ),
                                checked = batterySaverActionEnabled,
                                enabled = true,
                                onCheckedChange = {
                                    onManageBatterySaverChange(it)
                                    if (it) {
                                        onBatterySaverModeChange(
                                            AppPreferences.BATTERY_SAVER_IGNORE
                                        )
                                    }
                                    onServiceRefreshRequested()
                                }
                            )
                        }

                    }
                }

                item {
                    AnimatedVisibility(visible = !helperInstalled) {
                        InfoCard(
                            title = stringResource(
                                R.string.home_helper_not_installed_title
                            ),
                            text = stringResource(
                                R.string.home_helper_not_installed_description
                            ),
                            actionLabel = stringResource(R.string.install_helper),
                            onAction = {
                                navigateToAbout(AboutScrollTarget.HELPER)
                            }
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = stringResource(R.string.home_sleep_behavior),
                        subtitle = stringResource(R.string.home_sleep_behavior_description)
                    )
                }

                item {
                    SettingsCard {
                        SleepGraceSelector(
                            valueMs = sleepGraceMs,
                            customDelayEnabled = customDelayEnabled,
                            customDelayMs = customDelayMs,
                            onChange = { value ->
                                onSleepGraceChange(value)
                            },
                            onCustom = {
                                navigateToAdvanced(
                                    AdvancedScrollTarget.CUSTOM_DELAY
                                )
                            }
                        )
                    }
                }

                if (closedLidProtectionSupported) {
                    item {
                        SectionTitle(
                            title = stringResource(R.string.home_clamshell_options),
                            subtitle = stringResource(
                                R.string.home_clamshell_options_description
                            )
                        )
                    }

                    item {
                        ClamshellOptionsCard(
                            closedLidProtectionEnabled =
                                closedLidProtectionEnabled && closedLidAdminActive,
                            chargingSeparationSupported = chargingSeparationSupported,
                            chargingSeparationEnabled = chargingSeparationWithLidEnabled,
                            sleepOnExternalDisplayDisconnect = dockDisconnectSleeps,
                            powerButtonSleepSupported = closedLidPowerSupported,
                            powerButtonSleepsWithLidClosed = closedLidPowerSleeps,
                            onClosedLidProtectionChange = { enabled ->
                                onClosedLidProtectionChangeRequested(enabled)
                                onRefreshRequested()
                            },
                            onChargingSeparationChange = {
                                onChargingSeparationWithLidChange(it)
                                onServiceRefreshRequested()
                            },
                            onSleepOnExternalDisplayDisconnectChange = {
                                onDockDisconnectSleepsChange(it)
                            },
                            onPowerButtonSleepsWithLidClosedChange = {
                                onClosedLidPowerSleepsChange(it)
                            }
                        )
                    }
                } else if (expectedThorClamshell) {
                    item {
                        SectionTitle(
                            title = stringResource(R.string.home_clamshell_options),
                            subtitle = stringResource(
                                R.string.home_clamshell_options_description
                            )
                        )
                    }

                    item {
                        SettingsCard {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.clamshell_lid_sensor_unavailable
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(
                                        R.string.clamshell_lid_sensor_unavailable_description
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    SectionTitle(
                        title = stringResource(R.string.home_app_integrations),
                        subtitle = stringResource(R.string.home_app_integrations_description)
                    )
                }

                item {
                    SettingsCard(
                        modifier = Modifier.testTag("app_integrations_card")
                    ) {
                        val syncthingBroadcastReminder =
                            stringResource(R.string.home_syncthing_broadcast_reminder)
                        val basicSyncRemoteControlReminder =
                            stringResource(
                                if (BasicSyncController.supportsStateApi(context)) {
                                    R.string.home_basicsync_remote_control_modern
                                } else {
                                    R.string.home_basicsync_remote_control_legacy
                                }
                            )
                        val raOfflineProxyStatusText =
                            when {
                                !raOfflineProxyInstalled ->
                                    null
                                !raOfflineProxyProviderAvailable ->
                                    stringResource(
                                        R.string.raofflineproxy_api_unavailable
                                    )
                                !raOfflineProxyControlPermission ->
                                    stringResource(
                                        R.string.raofflineproxy_permission_missing
                                    )
                                !raOfflineProxyBatteryUnrestricted ->
                                    stringResource(
                                        R.string.raofflineproxy_needs_unrestricted
                                    )
                                raOfflineProxyStatus == null ->
                                    stringResource(
                                        if (raOfflineProxyStatusProbeComplete) {
                                            R.string.raofflineproxy_api_unavailable
                                        } else {
                                            R.string.checking
                                        }
                                    )
                                !raOfflineProxyApiCompatible ->
                                    stringResource(
                                        R.string.raofflineproxy_api_unsupported,
                                        raOfflineProxyStatus.version
                                    )
                                else -> {
                                    val runtime =
                                        stringResource(
                                            if (
                                                raOfflineProxyStatus.running ||
                                                raOfflineProxyStatus.shouldBeRunning
                                            ) {
                                                R.string.running
                                            } else {
                                                R.string.stopped
                                            }
                                        )
                                    when (raOfflineProxyStatus.queue.state) {
                                        RaOfflineProxyQueueState.CACHING ->
                                            runtime + " · " +
                                                stringResource(
                                                    R.string.raofflineproxy_queue_caching,
                                                    raOfflineProxyStatus.queue.count
                                                )
                                        RaOfflineProxyQueueState.WAITING ->
                                            runtime + " · " +
                                                stringResource(
                                                    R.string.raofflineproxy_queue_waiting,
                                                    raOfflineProxyStatus.queue.count
                                                )
                                        RaOfflineProxyQueueState.BLOCKED ->
                                            runtime + " · " +
                                                stringResource(
                                                    R.string.raofflineproxy_queue_blocked
                                                )
                                        RaOfflineProxyQueueState.IDLE ->
                                            runtime
                                        RaOfflineProxyQueueState.UNKNOWN ->
                                            runtime + " · " +
                                                stringResource(R.string.unknown)
                                    }
                                }
                            }

                        val integrationRows =
                            listOf(
                                IntegrationUiRow(
                                    installed = selectedTarget != null,
                                    icon = R.drawable.ic_syncthing,
                                    title = stringResource(R.string.home_syncthing_fork)
                                ) {
                                    CompactIntegrationRow(
                                        icon = R.drawable.ic_syncthing,
                                        title = stringResource(R.string.home_syncthing_fork),
                                        version = selectedTarget?.displayName
                                            ?.substringAfter("•")
                                            ?.trim()
                                            ?: stringResource(R.string.installed),
                                        status =
                                            stringResource(
                                                when (uiState.currentSyncthingState) {
                                                    SyncthingController.RuntimeState.RUNNING ->
                                                        R.string.running
                                                    SyncthingController.RuntimeState.STOPPED ->
                                                        R.string.stopped
                                                    SyncthingController.RuntimeState.UNKNOWN ->
                                                        R.string.unknown
                                                    null ->
                                                        R.string.checking
                                                }
                                            ),
                                        checked = syncthingEnabled,
                                        enabled = true,
                                        onCheckedChange = {
                                            onManageSyncthingChange(it)

                                            if (it) {
                                                Toast.makeText(
                                                    context,
                                                    syncthingBroadcastReminder,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else if (managerEnabled) {
                                                onRestoreSyncthingRequested()
                                                SleepCycleStore.completeIfRestored(context)
                                            }
                                        },
                                        onOpen = { SyncthingController.open(context) },
                                        secondaryActionLabel =
                                            if (targets.size > 1) {
                                                stringResource(R.string.change_target)
                                            } else {
                                                null
                                            },
                                        onSecondaryAction =
                                            if (targets.size > 1) {
                                                { showTargetDialog = true }
                                            } else {
                                                null
                                            }
                                    )
                                },
                                IntegrationUiRow(
                                    installed = tailscaleInstalled,
                                    icon = R.drawable.ic_tailscale,
                                    title = stringResource(R.string.integration_tailscale)
                                ) {
                                    CompactIntegrationRow(
                                        icon = R.drawable.ic_tailscale,
                                        title = stringResource(R.string.integration_tailscale),
                                        version =
                                            tailscaleVersion?.substringBefore("-")
                                                ?: stringResource(R.string.installed),
                                        status =
                                            uiState.currentTailscaleConnected?.let {
                                                stringResource(
                                                    if (it) {
                                                        R.string.connected
                                                    } else {
                                                        R.string.disconnected
                                                    }
                                                )
                                            } ?: stringResource(R.string.checking),
                                        checked = tailscaleEnabled,
                                        enabled = true,
                                        onCheckedChange = {
                                            onManageTailscaleChange(it)
                                        },
                                        onOpen = { TailscaleController.open(context) }
                                    )
                                },
                                IntegrationUiRow(
                                    installed = jamesDspTarget != null,
                                    icon = R.drawable.ic_equalizer,
                                    title = stringResource(R.string.integration_jamesdsp)
                                ) {
                                    CompactIntegrationRow(
                                        icon = R.drawable.ic_equalizer,
                                        title = stringResource(R.string.integration_jamesdsp),
                                        version =
                                            jamesDspTarget?.versionName
                                                ?: stringResource(R.string.installed),
                                        status = null,
                                        checked = jamesDspEnabled,
                                        enabled = true,
                                        onCheckedChange = {
                                            onManageJamesDspChange(it)

                                            if (!it && managerEnabled) {
                                                onRestoreJamesDspRequested()
                                                SleepCycleStore.completeIfRestored(context)
                                            }
                                        },
                                        onOpen = { JamesDspController.open(context) }
                                    )
                                },
                                IntegrationUiRow(
                                    installed = basicSyncInstalled,
                                    icon = R.drawable.ic_sync,
                                    title = stringResource(R.string.integration_basicsync)
                                ) {
                                    CompactIntegrationRow(
                                        icon = R.drawable.ic_sync,
                                        title = stringResource(R.string.integration_basicsync),
                                        version =
                                            basicSyncVersion
                                                ?: stringResource(R.string.installed),
                                        status =
                                            if (BasicSyncController.supportsStateApi(context)) {
                                                uiState.currentBasicSyncState?.let { state ->
                                                    val mode =
                                                        stringResource(
                                                            when (state.mode) {
                                                                BasicSyncController.Mode.AUTO_MODE ->
                                                                    R.string.auto_mode
                                                                BasicSyncController.Mode.MANUAL_MODE_STARTED ->
                                                                    R.string.manual_mode
                                                                BasicSyncController.Mode.MANUAL_MODE_STOPPED ->
                                                                    R.string.manual_mode
                                                            }
                                                        )
                                                    val runState =
                                                        stringResource(
                                                            when (state.runState) {
                                                                BasicSyncController.RunState.RUNNING ->
                                                                    R.string.running
                                                                BasicSyncController.RunState.NOT_RUNNING ->
                                                                    R.string.stopped
                                                                BasicSyncController.RunState.PAUSED ->
                                                                    R.string.paused
                                                                BasicSyncController.RunState.STARTING ->
                                                                    R.string.starting
                                                                BasicSyncController.RunState.STOPPING ->
                                                                    R.string.stopping
                                                                BasicSyncController.RunState.IMPORTING ->
                                                                    R.string.importing
                                                                BasicSyncController.RunState.EXPORTING ->
                                                                    R.string.exporting
                                                            }
                                                        )
                                                    val syncState =
                                                        if (
                                                            BasicSyncController
                                                                .supportsSyncCounters(context)
                                                        ) {
                                                            when (
                                                                basicSyncCompletionState(state)
                                                            ) {
                                                                SyncCompletionState.SYNCING ->
                                                                    stringResource(R.string.syncing)
                                                                SyncCompletionState.SYNCED ->
                                                                    stringResource(R.string.synced)
                                                                SyncCompletionState.UNKNOWN ->
                                                                    null
                                                            }
                                                        } else {
                                                            null
                                                        }
                                                    listOfNotNull(
                                                        mode,
                                                        runState,
                                                        syncState
                                                    ).joinToString(" · ")
                                                } ?: stringResource(R.string.checking)
                                            } else {
                                                stringResource(
                                                    R.string.home_basicsync_legacy_status
                                                )
                                            },
                                        checked = basicSyncEnabled,
                                        enabled = true,
                                        onCheckedChange = {
                                            onManageBasicSyncChange(it)

                                            if (it) {
                                                Toast.makeText(
                                                    context,
                                                    basicSyncRemoteControlReminder,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else if (managerEnabled) {
                                                onRestoreBasicSyncRequested()
                                                SleepCycleStore.completeIfRestored(context)
                                                SyncMaintenanceScheduler.cancel(context)
                                                onServiceRefreshRequested()
                                            }

                                            if (it && managerEnabled) {
                                                onServiceRefreshRequested()
                                            }
                                        },
                                        onOpen = { BasicSyncController.open(context) }
                                    )
                                },
                                IntegrationUiRow(
                                    installed = raOfflineProxyInstalled,
                                    icon = R.drawable.ic_raofflineproxy_mono,
                                    title = stringResource(
                                        R.string.integration_raofflineproxy
                                    )
                                ) {
                                    CompactIntegrationRow(
                                        icon = R.drawable.ic_raofflineproxy_mono,
                                        title = stringResource(
                                            R.string.integration_raofflineproxy
                                        ),
                                        version =
                                            raOfflineProxyVersion
                                                ?: stringResource(R.string.installed),
                                        status = raOfflineProxyStatusText,
                                        checked =
                                            raOfflineProxyEnabled &&
                                                raOfflineProxyReady,
                                        enabled = raOfflineProxyReady,
                                        onCheckedChange = {
                                            onManageRaOfflineProxyChange(it)
                                            if (it && managerEnabled) {
                                                onServiceRefreshRequested()
                                            }
                                        },
                                        onOpen = {
                                            RaOfflineProxyController.open(context)
                                        },
                                        secondaryActionLabel =
                                            if (!raOfflineProxyBatteryUnrestricted) {
                                                stringResource(
                                                    R.string.raofflineproxy_app_settings
                                                )
                                            } else {
                                                null
                                            },
                                        onSecondaryAction =
                                            if (!raOfflineProxyBatteryUnrestricted) {
                                                {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(
                                                            R.string
                                                                .raofflineproxy_unrestricted_hint
                                                        ),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    onOpenRaOfflineProxySettingsRequested()
                                                }
                                            } else {
                                                null
                                            }
                                    )
                                }
                            )

                        val installedRows = integrationRows.filter { it.installed }
                        val supportedRows = integrationRows.filterNot { it.installed }

                        installedRows.forEachIndexed { index, row ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 64.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            row.content()
                        }

                        if (supportedRows.isNotEmpty()) {
                            if (installedRows.isNotEmpty()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }

                            Text(
                                text = stringResource(
                                    if (installedRows.isEmpty()) {
                                        R.string.home_supported_apps
                                    } else {
                                        R.string.home_also_supported
                                    }
                                ),
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 14.dp,
                                    bottom = 4.dp
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            supportedRows.forEachIndexed { index, row ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 64.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                CompactSupportedAppRow(
                                    icon = row.icon,
                                    title = row.title,
                                    version = stringResource(R.string.not_installed),
                                    preserveIconColors = row.preserveIconColors
                                )
                            }
                        }
                    }
                }
                item {
                    val batteryBelowCondition =
                        stringResource(
                            R.string.home_condition_battery_below,
                            batteryBelowPercent
                        )
                    val notChargingCondition =
                        stringResource(R.string.home_condition_not_charging)
                    val batterySaverOnCondition =
                        stringResource(R.string.home_condition_battery_saver_on)
                    val batterySaverOffCondition =
                        stringResource(R.string.home_condition_battery_saver_off)
                    val scheduleCondition =
                        stringResource(
                            R.string.home_condition_schedule,
                            formatTime(scheduleStartMinutes),
                            formatTime(scheduleEndMinutes)
                        )

                    BehaviorCard(
                        wifi = wifiEnabled && helperInstalled,
                        bluetooth = bluetoothEnabled && helperInstalled,
                        batterySaver =
                            batterySaverActionEnabled && batterySaverControlSupported,
                        syncthing = syncthingEnabled && selectedTarget != null,
                        tailscale = tailscaleEnabled && tailscaleInstalled,
                        jamesDsp = jamesDspEnabled && jamesDspTarget != null,
                        basicSync = basicSyncEnabled && basicSyncInstalled,
                        raOfflineProxy =
                            raOfflineProxyEnabled &&
                                raOfflineProxyReady,
                        closedLidProtection = closedLidProtectionEnabled && closedLidAdminActive,
                        chargingSeparationWithLid =
                            chargingSeparationWithLidEnabled && chargingSeparationSupported,
                        sleepOnExternalDisplayDisconnect =
                            closedLidProtectionSupported && dockDisconnectSleeps,
                        powerButtonSleepsWithLidClosed =
                            closedLidPowerSupported && closedLidPowerSleeps,
                        sleepGraceMs = effectiveSleepDelayMs,
                        advancedConditions = buildList {
                            if (batteryConditionEnabled) add(batteryBelowCondition)
                            if (notChargingOnly) add(notChargingCondition)
                            when (batterySaverMode) {
                                AppPreferences.BATTERY_SAVER_ON ->
                                    add(batterySaverOnCondition)
                                AppPreferences.BATTERY_SAVER_OFF ->
                                    add(batterySaverOffCondition)
                            }
                            if (scheduleEnabled) add(scheduleCondition)
                        },
                        periodicSyncWhileSleeping = periodicSyncWhileSleeping,
                        syncThenStopOnSleepWake = syncThenStopOnSleepWake
                    )
                }

                if (managerEnabled && !setupComplete) {
                    item {
                        Button(
                            onClick = feedbackClick { onFinishSetupRequested() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.finish_setup))
                        }
                    }
                } else if (setupComplete) {
                    item {
                        Button(
                            onClick = feedbackClick { onFinishAppRequested() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.done))
                        }
                    }
                }

                item {
                    LastActivityCard(
                        context = context,
                        onViewLog = { navigateToActivityLog() },
                        onCopyLog = { onCopyDiagnosticsRequested() }
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
                                        context
                                    ),
                                onPeriodicSyncWhileSleepingChange = {
                                    onPeriodicSyncWhileSleepingChange(it)
                                    if (!it) {
                                        SyncMaintenanceScheduler.cancel(
                                            context
                                        )
                                    }
                                },
                                onSyncThenStopOnSleepWakeChange = {
                                    onSyncThenStopOnSleepWakeChange(it)

                                    if (managerEnabled) {
                                        // Disabling this option must hand
                                        // BasicSync back to its pre-feature state.
                                        onServiceRefreshRequested()
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
                                        !onCanScheduleExactAlarmsRequested()
                                    ) {
                                        onRequestExactAlarmAccessRequested()
                                    } else {
                                        onCustomDelayEnabledChange(enabled)

                                        if (!enabled) {
                                            onSleepGraceChange(0L)
                                        }
                                    }
                                },
                                onCustomDelayChange = {
                                    onCustomDelayChange(it)
                                },
                                onBatteryConditionEnabledChange = {
                                    onBatteryConditionEnabledChange(it)
                                },
                                onBatteryBelowPercentChange = {
                                    onBatteryBelowPercentChange(it)
                                },
                                onNotChargingOnlyChange = {
                                    onNotChargingOnlyChange(it)
                                },
                                onBatterySaverModeChange = {
                                    onBatterySaverModeChange(it)
                                    if (
                                        it !=
                                        AppPreferences.BATTERY_SAVER_IGNORE
                                    ) {
                                        onManageBatterySaverChange(false)
                                    }
                                },
                                onScheduleEnabledChange = {
                                    onScheduleEnabledChange(it)
                                },
                                onPickScheduleStart = {
                                    onShowTimePickerRequested(scheduleStartMinutes) { value ->
                                        onScheduleStartMinutesChange(value)
                                    }
                                },
                                onPickScheduleEnd = {
                                    onShowTimePickerRequested(scheduleEndMinutes) { value ->
                                        onScheduleEndMinutesChange(value)
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
                                context = context,
                                onCopyLog = { onCopyDiagnosticsRequested() }
                            )
                        }
                    }

                    AppSection.ABOUT -> {
                        item {
                            AboutPage(
                                context = context,
                                backgroundReliability = backgroundReliability,
                                automaticUpdateChecks = automaticUpdateChecks,
                                notificationsAllowed = updateNotificationsAllowed,
                                onAutomaticUpdateChecksChange = { enabled ->
                                    onAutomaticUpdateChecksChange(enabled)
                                    UpdateCheckScheduler.sync(context)
                                    if (enabled) {
                                        UpdateChecker.checkOnForegroundAsync(
                                            context,
                                            notify = true
                                        )
                                    }
                                    onRefreshRequested()
                                },
                                onRequestNotificationPermission = {
                                    onRequestUpdateNotificationPermissionRequested()
                                },
                                onOpenExternalUrl = { url ->
                                    onOpenExternalUrlRequested(url)
                                },
                                onOpenAppInfo = {
                                    onOpenAppInfoRequested()
                                },
                                onOpenBatteryOptimization = {
                                    onOpenBatteryOptimizationRequested()
                                },
                                onOpenUnusedAppRestrictions = {
                                    onOpenUnusedAppRestrictionsRequested()
                                },
                                onInstallVerifiedUpdate = { apkPath ->
                                    onInstallVerifiedUpdateRequested(apkPath)
                                },
                                installerReturnToken = uiState.installerReturnToken,
                                onUpdateStateChanged = {
                                    onRefreshRequested()
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
