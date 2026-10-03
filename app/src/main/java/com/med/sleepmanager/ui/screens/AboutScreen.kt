package com.med.sleepmanager.ui.screens

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.BuildConfig
import com.med.sleepmanager.R
import com.med.sleepmanager.data.UpdateStateStore
import com.med.sleepmanager.device.BackgroundReliability
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.ui.components.SectionTitle
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.update.UpdateChecker
import com.med.sleepmanager.update.UpdateCheckResult
import com.med.sleepmanager.update.UpdateDownloadResult
import com.med.sleepmanager.update.UpdateInstaller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.med.sleepmanager.ui.feedbackChange

internal enum class AboutScrollTarget {
    UPDATES,
    HELPER
}

@Composable
internal fun AboutPage(
    context: Context,
    backgroundReliability: BackgroundReliability.Snapshot?,
    automaticUpdateChecks: Boolean,
    notificationsAllowed: Boolean,
    onAutomaticUpdateChecksChange: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenUnusedAppRestrictions: () -> Unit,
    onInstallVerifiedUpdate: (String) -> Unit,
    installerReturnToken: Int,
    onUpdateStateChanged: () -> Unit,
    scrollTarget: AboutScrollTarget? = null,
    scrollRequestId: Int = 0,
    onScrollTargetConsumed: () -> Unit = {}
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
    val latestMainVersion = UpdateStateStore.latestReleaseVersion(context)
    val updatesRequester = remember { BringIntoViewRequester() }
    val helperRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(scrollRequestId, scrollTarget) {
        when (scrollTarget) {
            AboutScrollTarget.UPDATES -> updatesRequester.bringIntoView()
            AboutScrollTarget.HELPER -> {
                runCatching { helperRequester.bringIntoView() }
                    .onFailure { updatesRequester.bringIntoView() }
            }
            null -> Unit
        }
        if (scrollTarget != null) {
            onScrollTargetConsumed()
        }
    }

    LaunchedEffect(installerReturnToken) {
        if (installerReturnToken > 0) {
            updateCheckMessage = null
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoCard(
            title = stringResource(R.string.app_name),
            text = stringResource(
                R.string.about_summary,
                packageInfo?.versionName ?: stringResource(R.string.unknown)
            )
        )

        SettingsCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.about_what_sleepmanager_does),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.about_what_sleepmanager_does_description),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        SectionTitle(
            title = stringResource(R.string.about_app_details),
            subtitle = stringResource(R.string.about_app_details_description)
        )

        SettingsCard {
            AboutInfoRow(
                label = stringResource(R.string.app_name),
                value = packageInfo?.versionName ?: stringResource(R.string.unknown)
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutInfoRow(
                label = stringResource(R.string.about_compatibility_helper),
                value = helperVersion ?: stringResource(R.string.not_installed)
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutInfoRow(
                label = stringResource(R.string.about_android),
                value = stringResource(
                    R.string.about_android_version,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT
                )
            )
        }

        SectionTitle(
            title = stringResource(R.string.about_updates),
            subtitle = stringResource(R.string.about_updates_description),
            modifier = Modifier.bringIntoViewRequester(updatesRequester)
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
                        stringResource(R.string.about_automatic_update_checks),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.about_automatic_update_checks_description),
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
                    val installedMain =
                        packageInfo?.versionName
                            ?: context.getString(R.string.unknown)
                    append(
                        when {
                            cachedUpdate != null ->
                                context.getString(
                                    R.string.about_main_update_available_status,
                                    installedMain,
                                    cachedUpdate.versionName
                                )
                            latestMainVersion != null ->
                                context.getString(
                                    R.string.about_main_up_to_date_status,
                                    installedMain
                                )
                            else ->
                                context.getString(
                                    R.string.about_main_not_checked_status,
                                    installedMain
                                )
                        }
                    )

                    append("\n")
                    append(
                        when {
                            helperVersion == null && cachedHelperRelease != null ->
                                context.getString(
                                    R.string.about_helper_available_to_install_status,
                                    cachedHelperRelease.versionName
                                )
                            helperVersion == null ->
                                context.getString(
                                    R.string.about_helper_not_installed_not_checked_status
                                )
                            cachedHelperUpdate != null ->
                                context.getString(
                                    R.string.about_helper_update_available_status,
                                    helperVersion,
                                    cachedHelperUpdate.versionName
                                )
                            cachedHelperRelease != null ->
                                context.getString(
                                    R.string.about_helper_up_to_date_status,
                                    helperVersion
                                )
                            else ->
                                context.getString(
                                    R.string.about_helper_not_checked_status,
                                    helperVersion
                                )
                        }
                    )
                }

            AboutActionRow(
                title = stringResource(R.string.about_check_for_updates),
                subtitle = updateStatusText,
                actionLabel = stringResource(
                    if (updateCheckRunning) {
                        R.string.checking
                    } else {
                        R.string.check
                    }
                ),
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
                                context.getString(R.string.about_unable_to_check)
                            UpdateCheckResult.Disabled ->
                                context.getString(R.string.about_automatic_checks_disabled)
                            UpdateCheckResult.NotDue ->
                                context.getString(R.string.about_update_check_already_running)
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
                    title = stringResource(
                        R.string.about_sleepmanager_version,
                        update.versionName
                    ),
                    subtitle = stringResource(
                        if (update.directInstallAvailable) {
                            R.string.about_download_verify_install_main
                        } else {
                            R.string.about_direct_install_unavailable
                        }
                    ),
                    actionLabel = stringResource(
                        when {
                            mainDownloadRunning -> R.string.downloading
                            update.directInstallAvailable -> R.string.update_action
                            else -> R.string.open
                        }
                    ),
                    enabled = !mainDownloadRunning && !helperDownloadRunning,
                    secondaryActionLabel = stringResource(R.string.release_notes),
                    onSecondaryClick = {
                        onOpenExternalUrl(update.releaseUrl)
                    },
                    onClick = {
                        if (!update.directInstallAvailable) {
                            onOpenExternalUrl(update.releaseUrl)
                        } else {
                            mainDownloadRunning = true
                            updateCheckMessage =
                                context.getString(
                                    R.string.about_downloading_sleepmanager,
                                    update.versionName
                                )
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
                                            context.getString(
                                                R.string.about_main_apk_verified
                                            )
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
                val helperActionTitle =
                    if (helperActionInfo != null) {
                        stringResource(
                            R.string.about_helper_version,
                            helperActionInfo.versionName
                        )
                    } else {
                        stringResource(R.string.about_helper_name)
                    }
                val helperActionSubtitle =
                    stringResource(
                        when {
                            helperVersion == null &&
                                helperActionInfo != null &&
                                !helperActionInfo.directInstallAvailable ->
                                R.string.about_direct_install_unavailable
                            helperVersion == null ->
                                R.string.about_download_verify_install_helper_wifi_bluetooth
                            helperActionInfo?.directInstallAvailable == true ->
                                R.string.about_download_verify_install_helper
                            else ->
                                R.string.about_direct_install_unavailable
                        }
                    )
                val helperActionLabel =
                    stringResource(
                        when {
                            helperDownloadRunning -> R.string.downloading
                            helperActionInfo != null &&
                                !helperActionInfo.directInstallAvailable ->
                                R.string.open
                            helperVersion == null -> R.string.install
                            else -> R.string.update_action
                        }
                    )

                AboutActionRow(
                    modifier = Modifier.bringIntoViewRequester(helperRequester),
                    title = helperActionTitle,
                    subtitle = helperActionSubtitle,
                    actionLabel = helperActionLabel,
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
                                    context.getString(
                                        R.string.about_preparing_helper
                                    )
                                } else {
                                    context.getString(
                                        R.string.about_downloading_helper,
                                        helperActionInfo?.versionName.orEmpty()
                                    )
                                }

                            updateScope.launch {
                                val helperInfoResult = withContext(Dispatchers.IO) {
                                    runCatching {
                                        helperActionInfo
                                            ?: UpdateChecker.fetchLatestHelperForInstall(
                                                context
                                            )
                                            ?: error(
                                                context.getString(
                                                    R.string.about_no_helper_apk
                                                )
                                            )
                                    }
                                }

                                val helperInfo = helperInfoResult.getOrNull()
                                if (helperInfo == null) {
                                    helperDownloadRunning = false
                                    updateCheckMessage =
                                        helperInfoResult.exceptionOrNull()?.message
                                            ?: context.getString(
                                                R.string.about_unable_to_find_helper_release
                                            )
                                    onUpdateStateChanged()
                                    return@launch
                                }

                                if (!helperInfo.directInstallAvailable) {
                                    helperDownloadRunning = false
                                    updateCheckMessage =
                                        context.getString(
                                            R.string.about_helper_direct_install_unavailable
                                        )
                                    onUpdateStateChanged()
                                    return@launch
                                }

                                updateCheckMessage =
                                    context.getString(
                                        R.string.about_downloading_helper,
                                        helperInfo.versionName
                                    )

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
                                            context.getString(
                                                R.string.about_helper_apk_verified
                                            )
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
                        label = stringResource(R.string.about_update_notifications),
                        value = stringResource(R.string.allowed)
                    )
                } else {
                    AboutActionRow(
                        title = stringResource(R.string.about_update_notifications),
                        subtitle = stringResource(
                            R.string.about_update_notifications_description
                        ),
                        actionLabel = stringResource(R.string.enable),
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
                    title = stringResource(R.string.about_simulate_update),
                    subtitle = stringResource(
                        R.string.about_simulate_update_description,
                        "0.5.2"
                    ),
                    actionLabel = stringResource(R.string.simulate),
                    onClick = {
                        val simulated =
                            UpdateChecker.simulateAvailableUpdate(
                                context = context,
                                versionName = "0.5.2"
                            )
                        updateCheckMessage =
                            context.getString(
                                R.string.about_simulated_update,
                                simulated.versionName
                            )
                        onUpdateStateChanged()
                    }
                )
            }
        }

        SectionTitle(
            title = stringResource(R.string.about_background_reliability),
            subtitle = stringResource(
                R.string.about_background_reliability_description
            )
        )

        SettingsCard {
            AboutActionRow(
                title = stringResource(R.string.about_battery_optimization),
                subtitle = stringResource(
                    when (
                        backgroundReliability?.batteryOptimization
                    ) {
                        BackgroundReliability.Status.OK ->
                            R.string.about_reliability_disabled_recommended
                        BackgroundReliability.Status.NEEDS_ATTENTION ->
                            R.string.about_reliability_enabled_disable_recommended
                        BackgroundReliability.Status.UNAVAILABLE ->
                            R.string.about_not_available_android_version
                        BackgroundReliability.Status.UNKNOWN ->
                            R.string.about_unable_to_read_setting
                        null ->
                            R.string.checking
                    }
                ),
                actionLabel = stringResource(R.string.settings),
                enabled =
                    backgroundReliability?.batteryOptimization !=
                        BackgroundReliability.Status.UNAVAILABLE,
                textAction = true,
                onClick = onOpenBatteryOptimization
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            val unusedAppRestrictionsDeviceAdminExempt =
                backgroundReliability
                    ?.unusedAppRestrictionsExemptByDeviceAdmin == true

            AboutActionRow(
                title = stringResource(R.string.about_unused_app_restrictions),
                subtitle = stringResource(
                    when {
                        unusedAppRestrictionsDeviceAdminExempt ->
                            R.string.about_disabled_device_admin_exemption
                        backgroundReliability?.unusedAppRestrictions ==
                            BackgroundReliability.Status.OK ->
                            R.string.about_reliability_disabled_recommended
                        backgroundReliability?.unusedAppRestrictions ==
                            BackgroundReliability.Status.NEEDS_ATTENTION ->
                            R.string.about_reliability_enabled_disable_recommended
                        backgroundReliability?.unusedAppRestrictions ==
                            BackgroundReliability.Status.UNAVAILABLE ->
                            R.string.about_not_available_device
                        backgroundReliability?.unusedAppRestrictions ==
                            BackgroundReliability.Status.UNKNOWN ->
                            R.string.about_unable_to_read_setting
                        else ->
                            R.string.checking
                    }
                ),
                actionLabel = stringResource(R.string.settings),
                enabled =
                    !unusedAppRestrictionsDeviceAdminExempt &&
                        backgroundReliability?.unusedAppRestrictions !=
                            BackgroundReliability.Status.UNAVAILABLE,
                textAction = true,
                onClick = onOpenUnusedAppRestrictions
            )
        }

        SectionTitle(
            title = stringResource(R.string.about_support_project),
            subtitle = stringResource(R.string.about_support_project_description)
        )

        SettingsCard {
            AboutActionRow(
                title = stringResource(R.string.about_source_code),
                subtitle = stringResource(R.string.about_source_code_description),
                actionLabel = stringResource(R.string.open),
                onClick = {
                    onOpenExternalUrl("https://github.com/Darkaxt/SleepManager")
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutActionRow(
                title = stringResource(R.string.about_report_issue),
                subtitle = stringResource(R.string.about_report_issue_description),
                actionLabel = stringResource(R.string.open),
                onClick = {
                    onOpenExternalUrl("https://github.com/Darkaxt/SleepManager/issues")
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutActionRow(
                title = stringResource(R.string.about_android_app_info),
                subtitle = stringResource(R.string.about_android_app_info_description),
                actionLabel = stringResource(R.string.open),
                onClick = { onOpenAppInfo() }
            )
        }
    }
}

@Composable
internal fun AboutInfoRow(
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
internal fun AboutActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textAction: Boolean = false,
    secondaryActionLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val hasSecondaryAction =
            secondaryActionLabel != null && onSecondaryClick != null
        val compact = hasSecondaryAction && maxWidth < 520.dp

        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AboutActionText(
                    title = title,
                    subtitle = subtitle
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    AboutActionButtons(
                        actionLabel = actionLabel,
                        enabled = enabled,
                        textAction = textAction,
                        secondaryActionLabel = secondaryActionLabel,
                        onSecondaryClick = onSecondaryClick,
                        onClick = onClick
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AboutActionText(
                        title = title,
                        subtitle = subtitle
                    )
                }
                AboutActionButtons(
                    actionLabel = actionLabel,
                    enabled = enabled,
                    textAction = textAction,
                    secondaryActionLabel = secondaryActionLabel,
                    onSecondaryClick = onSecondaryClick,
                    onClick = onClick
                )
            }
        }
    }
}

@Composable
private fun AboutActionText(
    title: String,
    subtitle: String
) {
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

@Composable
private fun AboutActionButtons(
    actionLabel: String,
    enabled: Boolean,
    textAction: Boolean,
    secondaryActionLabel: String?,
    onSecondaryClick: (() -> Unit)?,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (
            secondaryActionLabel != null &&
            onSecondaryClick != null
        ) {
            TextButton(
                onClick = feedbackClick(onSecondaryClick),
                enabled = enabled
            ) {
                Text(secondaryActionLabel)
            }
        }

        if (textAction) {
            TextButton(
                onClick = feedbackClick(onClick),
                enabled = enabled
            ) {
                Text(actionLabel)
            }
        } else {
            OutlinedButton(
                onClick = feedbackClick(onClick),
                enabled = enabled
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun formatDuration(valueMs: Long): String =
    when (valueMs) {
        0L -> stringResource(R.string.grace_immediate)
        3000L -> stringResource(R.string.duration_3_seconds)
        5000L -> stringResource(R.string.duration_5_seconds)
        10000L -> stringResource(R.string.duration_10_seconds)
        60000L -> stringResource(R.string.duration_1_minute)
        300000L -> stringResource(R.string.duration_5_minutes)
        600000L -> stringResource(R.string.duration_10_minutes)
        1800000L -> stringResource(R.string.duration_30_minutes)
        else -> stringResource(
            R.string.duration_seconds_compact,
            valueMs / 1000L
        )
    }

internal fun formatTime(minutes: Int): String {
    val safe = minutes.coerceIn(0, 1439)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

@Composable
internal fun InfoCard(
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
