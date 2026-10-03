package com.med.sleepmanager.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
            title = stringResource(R.string.clamshell_closed_lid_protection),
            subtitle = stringResource(
                R.string.clamshell_closed_lid_protection_description
            ),
            checked = closedLidProtectionEnabled,
            enabled = true,
            onCheckedChange = onClosedLidProtectionChange
        )

        ClamshellDivider()

        SettingRow(
            icon = R.drawable.ic_lid_lock,
            title = stringResource(R.string.clamshell_sleep_on_display_disconnect),
            subtitle = stringResource(
                R.string.clamshell_sleep_on_display_disconnect_description
            ),
            checked = sleepOnExternalDisplayDisconnect,
            enabled = closedLidProtectionEnabled,
            onCheckedChange = onSleepOnExternalDisplayDisconnectChange
        )

        if (powerButtonSleepSupported) {
            ClamshellDivider()

            SettingRow(
                icon = R.drawable.ic_lid_lock,
                title = stringResource(R.string.clamshell_power_button_sleep),
                subtitle = stringResource(
                    R.string.clamshell_power_button_sleep_description
                ),
                checked = powerButtonSleepsWithLidClosed,
                enabled = closedLidProtectionEnabled,
                onCheckedChange = onPowerButtonSleepsWithLidClosedChange
            )
        }

        if (chargingSeparationSupported) {
            ClamshellDivider()

            SettingRow(
                icon = R.drawable.ic_battery,
                title = stringResource(
                    R.string.clamshell_disable_charging_separation
                ),
                subtitle = stringResource(
                    R.string.clamshell_disable_charging_separation_description
                ),
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
