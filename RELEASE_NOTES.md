# SleepManager 0.8.0.1 / Helper 1.1.7.1

SleepManager 0.8.0 adds **state-aware Key Mapper integration** and makes the **Home screen smoother**, especially on high-refresh-rate Android handhelds.

> **Note**
>
> **Recent compatible AYN and Retroid devices:** Wi-Fi and Bluetooth can be controlled directly, without the Compatibility Helper.
>
> **Other devices:** the optional **SleepManager Helper 1.1.7.1** remains available for Wi-Fi/Bluetooth control. If prompted, exempt the Helper from battery optimization and allow Wi-Fi control access in Android settings.

> [!WARNING]
> **AYANEO / KONKR devices with DuraSpeed:** disable **DuraSpeed** in Android Settings. Battery usage set to Unrestricted does not override DuraSpeed's background restrictions.

## What's New

### Key Mapper 4.7+ Integration

SleepManager can now manage **Key Mapper mappings** as part of a normal sleep/wake cycle.

- **Pause on sleep:** when mappings are running, SleepManager requests a pause when its sleep actions begin.
- **Resume on wake:** SleepManager requests a resume only when it was responsible for pausing those mappings.
- **Respect manual pauses:** mappings already paused before sleep are left untouched, including after wake.
- **Safe when the state is unknown:** SleepManager does not send a blind pause request before receiving Key Mapper's state.
- **Optional and off by default:** enable Key Mapper in **App Integrations**. Key Mapper's accessibility service and saved mappings are not modified.

Requires **Key Mapper 4.7.0 or newer**, which exposes the mapping-state API used by SleepManager.

### Smoother Home Screen

Home screen updates now run in the background and refresh only what has changed. This makes scrolling smoother while keeping battery, integration and Activity information up to date.

## Compatibility

- SleepManager **0.8.0.1 / versionCode 571**
- SleepManager Helper **1.1.7.1 / versionCode 1122** (unchanged)
- Android **9 / API 28 or newer**
- Key Mapper **4.7.0+** for the new integration
- Existing SleepManager settings are retained when updating an official signed build
- No root, Shizuku or ADB is required for normal use

## Installation and Updating

Install **SleepManager-0.8.0.1.apk** over your existing Darkaxt fork version, or use **About → Updates** to install the new stable release.

On a first install, open SleepManager, select the actions and integrations you want, enable SleepManager, then tap **Finish setup**. Install the optional Helper only when your device requires it.

To use Key Mapper integration, install **Key Mapper 4.7.0+** and enable its integration in SleepManager after Key Mapper reports its mapping state.

## Maintained fork compatibility

This release merges exact upstream76d1f0ca689a259bdbf87ca40fe526ae1cc0190b.
Official Tailscale and TailDNS retain package-aware active-VPN selection and exact
disconnect/restore ownership. Both APKs use the existing permanent fork certificate
and signature-protected Helper contract. Updates come only from Darkaxt/SleepManager;
fork settings/data remain compatible. No device installation or validation is claimed.
