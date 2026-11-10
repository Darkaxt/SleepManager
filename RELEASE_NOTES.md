# SleepManager 0.7.3.3 / Helper 1.1.7.1

> **Note**
>
> **Recent AYN and Retroid devices:** SleepManager supports direct Wi-Fi and Bluetooth control, so the **Compatibility Helper is no longer required** on supported recent AYN and Retroid handhelds.
>
> **Other devices / brands:** install the new **SleepManager Helper** when prompted and grant its **battery optimization exemption**. If SleepManager reports that Wi-Fi control access is required, enable it in **Settings → Apps → Special app access → Wi-Fi control → SleepManager Helper**.

> [!WARNING]
> **AYANEO / KONKR devices — disable DuraSpeed.**
>
> On devices with **DuraSpeed**, turn it **OFF** in Android Settings. DuraSpeed may restrict SleepManager, its Compatibility Helper and sync integrations in the background, preventing Wi-Fi/Bluetooth restoration after long standby.
>
> **Battery usage → Unrestricted is not enough:** DuraSpeed must be disabled separately. This resolved overnight restoration failures on a KONKR Pocket Advance.

## What's New

- Incorporates the complete upstream GPL-3.0 license and README/badge history through `90419395a08f`. Runtime behavior and the Helper remain unchanged. The upstream badge writer stays scoped to its own repository.

- **RAOfflineProxy v2.2 compatibility** — when the installed version requires the Android runtime **CONTROL_PROXY** permission, SleepManager can request it through the system permission dialog. Older versions continue to use the existing permission guidance.
- **Read-only Status API v1** — compatible launchers can query whether SleepManager is **enabled** and whether its foreground service is **running**. This API does not change settings or expose private diagnostics. See [Status API documentation](https://github.com/Darkaxt/SleepManager/blob/main/docs/status-api.md).

## First Install

1. Install **SleepManager 0.7.3.3** from the release assets below.
2. Open SleepManager and choose what you want it to manage.
3. If needed, SleepManager will guide you through installing and setting up the **Compatibility Helper**.
4. Enable **SleepManager**, then tap **Finish setup**.


## Maintained fork compatibility

Main 0.7.3.3 (code 570), unchanged Helper 1.1.7.1 (code 1122).
Official Tailscale and TailDNS remain supported with active-VPN package selection
and exact disconnect/restore ownership. Both APKs retain the permanent fork
certificate and signature-protected Helper contract. Updates come only from
Darkaxt/SleepManager. Existing settings are preserved by in-place fork updates.
This publication does not install or control any device.
