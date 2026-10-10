package com.med.sleepmanager.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.med.sleepmanager.R
import com.med.sleepmanager.ui.controller.controllerFocusHighlight
import com.med.sleepmanager.ui.feedbackClick

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.med.sleepmanager.ui.feedbackChange

@Composable
internal fun SectionTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
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
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingRow(
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
    val toggleContentDescription =
        stringResource(R.string.toggle_content_description, title)
    var toggleFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .controllerFocusHighlight(toggleFocused)
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
            modifier = Modifier
                .onFocusChanged { toggleFocused = it.isFocused }
                .semantics {
                    contentDescription = toggleContentDescription
                }
        )
    }
}

@Composable
internal fun CompactIntegrationRow(
    @DrawableRes icon: Int,
    title: String,
    version: String,
    status: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: (() -> Unit)? = null,
    preserveIconColors: Boolean = false,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val toggleContentDescription =
        stringResource(R.string.toggle_content_description, title)
    var toggleFocused by remember { mutableStateOf(false) }

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
                tint =
                    if (preserveIconColors) {
                        Color.Unspecified
                    } else {
                        when {
                            checked && enabled -> MaterialTheme.colorScheme.onPrimary
                            enabled -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
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
                        Text(stringResource(R.string.open))
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
                .controllerFocusHighlight(toggleFocused)
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
                    modifier = Modifier
                        .onFocusChanged { toggleFocused = it.isFocused }
                        .semantics {
                        contentDescription = toggleContentDescription
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
                .controllerFocusHighlight(toggleFocused)
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
                modifier = Modifier
                        .onFocusChanged { toggleFocused = it.isFocused }
                        .semantics {
                    contentDescription = toggleContentDescription
                }
            )
        }
    }
}


@Composable
internal fun CompactSupportedAppRow(
    @DrawableRes icon: Int,
    title: String,
    version: String,
    preserveIconColors: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint =
                    if (preserveIconColors) {
                        Color.Unspecified
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    },
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                modifier = Modifier.testTag("integration_title_$title"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
            Text(
                version,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
    }
}

