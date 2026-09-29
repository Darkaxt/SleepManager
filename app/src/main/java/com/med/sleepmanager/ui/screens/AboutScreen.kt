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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.BuildConfig
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.device.BackgroundReliability
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.ui.components.SectionTitle
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.ui.label
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
    val latestMainVersion = AppPreferences.latestReleaseVersion(context)
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
            subtitle = "Check GitHub releases and keep SleepManager and the optional Helper up to date.",
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
                        "Automatic update checks",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Check for updates when you open the app, and daily in the background.",
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
                    secondaryActionLabel = "Release notes",
                    onSecondaryClick = {
                        onOpenExternalUrl(update.releaseUrl)
                    },
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
                    modifier = Modifier.bringIntoViewRequester(helperRequester),
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
            title = "Background reliability",
            subtitle = "Android settings that can affect long-running background automation."
        )

        SettingsCard {
            AboutActionRow(
                title = "Battery optimization",
                subtitle = when (
                    backgroundReliability?.batteryOptimization
                ) {
                    BackgroundReliability.Status.OK ->
                        "✓ Disabled • Recommended for reliable background operation"
                    BackgroundReliability.Status.NEEDS_ATTENTION ->
                        "⚠ Enabled • Recommended to disable for reliable background operation"
                    BackgroundReliability.Status.UNAVAILABLE ->
                        "— Not available on this Android version"
                    BackgroundReliability.Status.UNKNOWN ->
                        "? Unable to read the current setting"
                    null ->
                        "Checking…"
                },
                actionLabel = "Settings",
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
                title = "Unused app restrictions",
                subtitle = when {
                    unusedAppRestrictionsDeviceAdminExempt ->
                        "✓ Disabled • Device administrator exemption"
                    backgroundReliability?.unusedAppRestrictions ==
                        BackgroundReliability.Status.OK ->
                        "✓ Disabled • Recommended for reliable background operation"
                    backgroundReliability?.unusedAppRestrictions ==
                        BackgroundReliability.Status.NEEDS_ATTENTION ->
                        "⚠ Enabled • Recommended to disable for reliable background operation"
                    backgroundReliability?.unusedAppRestrictions ==
                        BackgroundReliability.Status.UNAVAILABLE ->
                        "— Not available on this device"
                    backgroundReliability?.unusedAppRestrictions ==
                        BackgroundReliability.Status.UNKNOWN ->
                        "? Unable to read the current setting"
                    else ->
                        "Checking…"
                },
                actionLabel = "Settings",
                enabled =
                    !unusedAppRestrictionsDeviceAdminExempt &&
                        backgroundReliability?.unusedAppRestrictions !=
                            BackgroundReliability.Status.UNAVAILABLE,
                textAction = true,
                onClick = onOpenUnusedAppRestrictions
            )
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

internal fun formatDuration(valueMs: Long): String =
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
