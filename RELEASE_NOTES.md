# SleepManager 0.7.2.1 / Helper 1.1.7.1

> **Note**
>
> **Recent AYN and Retroid devices:** SleepManager 0.7.2 can now control Wi-Fi and Bluetooth directly, so the **Compatibility Helper is no longer required** on supported recent AYN and Retroid handhelds.
>
> **Other devices / brands:** install the new **SleepManager Helper** when prompted and grant its **battery optimization exemption**. If SleepManager reports that Wi-Fi control access is required, enable it in **Settings → Apps → Special app access → Wi-Fi control → SleepManager Helper**.

> [!WARNING]
> **AYANEO / KONKR devices — disable DuraSpeed.**
>
> On devices with **DuraSpeed**, turn it **OFF** in Android Settings. DuraSpeed may restrict SleepManager, its Compatibility Helper and sync integrations in the background, preventing Wi-Fi/Bluetooth restoration after long standby.
>
> **Battery usage → Unrestricted is not enough:** DuraSpeed must be disabled separately. This resolved overnight restoration failures on a KONKR Pocket Advance.

## What’s New

- **Direct Wi-Fi & Bluetooth control** on supported recent AYN and Retroid devices — no Helper required.
- **Improved sleep/wake reliability**, including safer radio restoration, recovery after interrupted cycles and stronger closed-lid handling.
- **Diagnostics 2.0** with improved Activity history, multi-cycle diagnostics, false-wake tracking, system/memory snapshots and Android process-exit information.
- **RAOfflineProxy improvements**, including pending-award handling and safer coordination before sleep.
- Numerous reliability, compatibility and UI fixes.

## Compatibility

- SleepManager **0.7.2.1 / versionCode 561**
- SleepManager Helper **1.1.7.1 / versionCode 1122**
- Android **9 / API 28 or newer**
- Recent compatible **AYN and Retroid** devices can control Wi-Fi and Bluetooth directly and **do not require the Helper**
- Other devices use the **SleepManager Compatibility Helper** for Wi-Fi and Bluetooth control
- RAOfflineProxy **v2.0.0-alpha1 or newer** is required for SleepManager integration
- Existing settings are preserved when updating
- No root, Shizuku or ADB is required for normal use

## First Install

1. Install **SleepManager 0.7.2.1** from the release assets below.
2. Open SleepManager and choose what you want it to manage.
3. If needed, SleepManager will guide you through installing and setting up the **Compatibility Helper**.
4. Enable **SleepManager**, then tap **Finish setup**.

## Updating

Install **SleepManager 0.7.2.1** over your existing fork version or use the built-in updater from **About → Updates**. Your existing SleepManager settings are preserved.

On recent compatible **AYN and Retroid** devices, the Helper is no longer required and SleepManager will use direct Wi-Fi/Bluetooth control automatically.

On other devices, update to **SleepManager Helper 1.1.7.1** when prompted and grant its battery optimization exemption.

## Maintained fork compatibility

Official Tailscale and TailDNS remain supported. Sleep control selects the active
supported VPN owner and restores only the exact package successfully disconnected.
Both APKs retain the permanent fork certificate and signature-protected Helper
contract. Updates come only from Darkaxt/SleepManager, not upstream-signed builds.
Install fork APKs over previous fork builds to preserve settings. No device is
installed or modified automatically by this release.
