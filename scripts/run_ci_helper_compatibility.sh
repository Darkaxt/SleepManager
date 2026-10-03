#!/usr/bin/env bash
set -Eeuo pipefail

SERIAL="${1:-${ADB_SERIAL:-emulator-5554}}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$SDK_ROOT/platform-tools/adb}"

adb_target() {
  "$ADB_BIN" -s "$SERIAL" "$@"
}

adb_target wait-for-device

for apk in \
  "$ROOT_DIR/helper/build/outputs/apk/debug/helper-debug.apk" \
  "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" \
  "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"; do
  [ -f "$apk" ] || {
    echo "Missing APK: $apk" >&2
    exit 1
  }
done

adb_target uninstall com.med.sleepmanager.test >/dev/null 2>&1 || true
adb_target uninstall com.med.sleepmanager >/dev/null 2>&1 || true
adb_target uninstall com.med.sleepmanager.helper >/dev/null 2>&1 || true

adb_target install -r -t "$ROOT_DIR/helper/build/outputs/apk/debug/helper-debug.apk"
adb_target install -r -t "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
adb_target install -r -t "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

adb_target shell pm grant \
  com.med.sleepmanager \
  android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb_target shell cmd appops set \
  com.med.sleepmanager \
  SCHEDULE_EXACT_ALARM allow >/dev/null 2>&1 || true

# Match the previously validated sequential path: let Android start the Main
# package once before replacing Helper 1.1.2 with the legacy 1.1.1 APK.
# On a completely fresh emulator this also settles first-run package state
# before the signature-protected Helper broadcasts are exercised.
adb_target shell am start -W \
  -n com.med.sleepmanager/.MainActivity >/dev/null
sleep 1
adb_target shell input keyevent 3 >/dev/null 2>&1 || true
sleep 1
adb_target shell am force-stop com.med.sleepmanager >/dev/null 2>&1 || true

"$ROOT_DIR/scripts/test_helper_version_compatibility.sh" "$SERIAL"
