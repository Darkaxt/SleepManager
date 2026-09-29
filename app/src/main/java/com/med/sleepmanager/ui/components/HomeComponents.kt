package com.med.sleepmanager.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.R
import com.med.sleepmanager.ui.AppSection
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.ui.iconRes
import com.med.sleepmanager.ui.label
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
internal fun OnboardingCard(
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
                "Update",
                fontWeight = FontWeight.SemiBold
            )
        }
        if (onReleaseNotes != null) {
            TextButton(
                onClick = feedbackClick(onReleaseNotes)
            ) {
                Text("Release notes")
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

