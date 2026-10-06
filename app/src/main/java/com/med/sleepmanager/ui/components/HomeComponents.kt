package com.med.sleepmanager.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.R
import com.med.sleepmanager.ui.AppSection
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.ui.iconRes
import com.med.sleepmanager.ui.labelRes
import com.med.sleepmanager.update.HelperUpdateInfo
import com.med.sleepmanager.update.UpdateInfo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CompactSideRail(
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
                        contentDescription = stringResource(R.string.open_navigation)
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
                            contentDescription = stringResource(section.labelRes)
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun OnboardingCard(
    syncthingTarget: SyncthingController.Target?,
    tailscaleInstalled: Boolean,
    jamesDspTarget: JamesDspController.Target?,
    basicSyncInstalled: Boolean,
    raOfflineProxyInstalled: Boolean
) {
    val detectedApps = buildList {
        if (syncthingTarget != null) {
            add(stringResource(R.string.integration_syncthing_fork))
        }
        if (tailscaleInstalled) {
            add(stringResource(R.string.integration_tailscale))
        }
        if (jamesDspTarget != null) {
            add(stringResource(R.string.integration_jamesdsp))
        }
        if (basicSyncInstalled) {
            add(stringResource(R.string.integration_basicsync))
        }
        if (raOfflineProxyInstalled) {
            add(stringResource(R.string.integration_raofflineproxy))
        }
    }
    val darkTheme = isSystemInDarkTheme()
    val containerColor =
        if (darkTheme) Color(0xFF231F18) else Color(0xFFFFF3C4)
    val contentColor =
        if (darkTheme) Color(0xFFF3EEE4) else Color(0xFF3D3000)
    val accentColor =
        if (darkTheme) Color(0xFFFFC94A) else contentColor
    val secondaryContentColor =
        if (darkTheme) Color(0xFFE3DCCE) else contentColor

    val quickSetupDescription = stringResource(R.string.quick_setup_description)
    val enableSleepManagerText = stringResource(R.string.enable_sleepmanager)
    val finishSetupText = stringResource(R.string.finish_setup)
    val quickSetupDescriptionStyled = buildAnnotatedString {
        append(quickSetupDescription)
        listOf(
            "“$enableSleepManagerText”",
            "“$finishSetupText”"
        ).forEach { phrase ->
            val start = quickSetupDescription.indexOf(phrase)
            if (start >= 0) {
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    start,
                    start + phrase.length
                )
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_setup_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.quick_setup),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )

            Text(
                quickSetupDescriptionStyled,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )

            if (detectedApps.isNotEmpty()) {
                Text(
                    stringResource(R.string.quick_setup_supported_apps_detected),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Text(
                    detectedApps.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryContentColor
                )
            }
        }
    }
}

@Composable
internal fun UpdateAvailableCard(
    update: UpdateInfo?,
    helperUpdate: HelperUpdateInfo?,
    onUpdate: () -> Unit,
    onReleaseNotes: (() -> Unit)? = null
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val compact = maxWidth < 520.dp

            if (compact) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UpdateAvailableText(
                        update = update,
                        helperUpdate = helperUpdate
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UpdateAvailableActions(
                            onUpdate = onUpdate,
                            onReleaseNotes = onReleaseNotes
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        UpdateAvailableText(
                            update = update,
                            helperUpdate = helperUpdate
                        )
                    }
                    UpdateAvailableActions(
                        onUpdate = onUpdate,
                        onReleaseNotes = onReleaseNotes
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableText(
    update: UpdateInfo?,
    helperUpdate: HelperUpdateInfo?
) {
    Text(
        stringResource(
            if (update != null && helperUpdate != null) {
                R.string.updates_available
            } else {
                R.string.update_available
            }
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        when {
            update != null && helperUpdate != null ->
                stringResource(
                    R.string.update_main_and_helper_available,
                    update.versionName,
                    helperUpdate.versionName
                )
            update != null ->
                stringResource(
                    R.string.update_main_available,
                    update.versionName
                )
            helperUpdate != null ->
                stringResource(
                    R.string.update_helper_available,
                    helperUpdate.versionName
                )
            else -> stringResource(R.string.update_generic_available)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun UpdateAvailableActions(
    onUpdate: () -> Unit,
    onReleaseNotes: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedButton(onClick = feedbackClick(onUpdate)) {
            Text(
                stringResource(R.string.update_action),
                fontWeight = FontWeight.SemiBold
            )
        }
        if (onReleaseNotes != null) {
            TextButton(
                onClick = feedbackClick(onReleaseNotes)
            ) {
                Text(stringResource(R.string.release_notes))
            }
        }
    }
}

@Composable
internal fun StatusCard(
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
                        text = stringResource(
                            when {
                                active -> R.string.status_active_title
                                enabled -> R.string.status_starting_title
                                else -> R.string.status_off_title
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(
                            when {
                                active -> R.string.status_active_description
                                enabled -> R.string.status_starting_description
                                else -> R.string.status_off_description
                            }
                        ),
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
                    stringResource(
                        if (enabled) {
                            R.string.disable_sleepmanager
                        } else {
                            R.string.enable_sleepmanager
                        }
                    )
                )
            }

        }
    }
}

