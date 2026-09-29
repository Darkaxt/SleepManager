package com.med.sleepmanager.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.R

@Composable
internal fun ClamshellOptionsCard(
    closedLidProtectionEnabled: Boolean,
    chargingSeparationSupported: Boolean,
    chargingSeparationEnabled: Boolean,
    sleepOnExternalDisplayDisconnect: Boolean,
    powerButtonSleepSupported: Boolean,
    powerButtonSleepsWithLidClosed: Boolean,
    onClosedLidProtectionChange: (Boolean) -> Unit,
    onChargingSeparationChange: (Boolean) -> Unit,
    onSleepOnExternalDisplayDisconnectChange: (Boolean) -> Unit,
    onPowerButtonSleepsWithLidClosedChange: (Boolean) -> Unit
) {
    SettingsCard {
        SettingRow(
            icon = R.drawable.ic_lid_lock,
            title = "Closed-lid protection",
            subtitle = "Return the device to sleep after accidental wake-ups while the lid is still closed. External displays remain usable.",
            checked = closedLidProtectionEnabled,
            enabled = true,
            onCheckedChange = onClosedLidProtectionChange
        )

        ClamshellDivider()

        SettingRow(
            icon = R.drawable.ic_lid_lock,
            title = "Sleep when external display disconnects",
            subtitle = "With the lid closed, put the device to sleep when an external display is disconnected. Off keeps the device's default behavior.",
            checked = sleepOnExternalDisplayDisconnect,
            enabled = closedLidProtectionEnabled,
            onCheckedChange = onSleepOnExternalDisplayDisconnectChange
        )

        if (powerButtonSleepSupported) {
            ClamshellDivider()

            SettingRow(
                icon = R.drawable.ic_lid_lock,
                title = "Power button sleeps with lid closed",
                subtitle = "With the lid closed and the device awake, press Power to put it back to sleep—docked or after disconnecting the external display. Off keeps the device\'s default behavior.",
                checked = powerButtonSleepsWithLidClosed,
                enabled = closedLidProtectionEnabled,
                onCheckedChange = onPowerButtonSleepsWithLidClosedChange
            )
        }

        if (chargingSeparationSupported) {
            ClamshellDivider()

            SettingRow(
                icon = R.drawable.ic_battery,
                title = "Disable Charging Separation with lid closed",
                subtitle = "Temporarily disable Charging Separation while the lid is closed so the battery can charge. External-display mode keeps your original setting.",
                checked = chargingSeparationEnabled,
                enabled = true,
                onCheckedChange = onChargingSeparationChange
            )
        }
    }
}

@Composable
private fun ClamshellDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
