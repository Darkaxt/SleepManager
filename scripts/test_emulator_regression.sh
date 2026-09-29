#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${1:-${ADB_SERIAL:-emulator-5554}}"
SKIP_STATIC="${SKIP_STATIC:-0}"
ALLOW_PHYSICAL_DEVICE="${ALLOW_PHYSICAL_DEVICE:-0}"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$SDK_ROOT/platform-tools/adb}"

if [ -x "$ROOT_DIR/gradlew" ]; then
  GRADLE_CMD=("$ROOT_DIR/gradlew")
else
  GRADLE_CMD=("${GRADLE:-gradle}")
fi

REPORT_DIR="$ROOT_DIR/build/emulator-regression/$SERIAL"
INSTRUMENTATION_LOG="$REPORT_DIR/instrumentation.txt"
LOGCAT_FILE="$REPORT_DIR/logcat.txt"
SUMMARY_FILE="$REPORT_DIR/summary.txt"

mkdir -p "$REPORT_DIR"
: > "$SUMMARY_FILE"

say() {
  printf '\n=== %s ===\n' "$*"
  printf '%s\n' "$*" >> "$SUMMARY_FILE"
}

fail() {
  printf '\n[FAIL] %s\n' "$*" >&2
  printf 'FAIL: %s\n' "$*" >> "$SUMMARY_FILE"
  collect_diagnostics
  exit 1
}

adb_target() {
  "$ADB_BIN" -s "$SERIAL" "$@"
}

collect_diagnostics() {
  adb_target logcat -d > "$LOGCAT_FILE" 2>/dev/null || true
  adb_target shell dumpsys activity services com.med.sleepmanager     > "$REPORT_DIR/services.txt" 2>/dev/null || true
  adb_target shell dumpsys package com.med.sleepmanager     > "$REPORT_DIR/main-package.txt" 2>/dev/null || true
  adb_target shell dumpsys package com.med.sleepmanager.helper     > "$REPORT_DIR/helper-package.txt" 2>/dev/null || true
}

if [ ! -x "$ADB_BIN" ]; then
  echo "adb not found at: $ADB_BIN" >&2
  exit 1
fi

if [[ "$SERIAL" != emulator-* && "$ALLOW_PHYSICAL_DEVICE" != "1" ]]; then
  cat >&2 <<EOF
Refusing to run destructive regression setup on '$SERIAL'.
This suite uninstalls and reinstalls SleepManager packages.
Use an emulator serial (recommended), or set ALLOW_PHYSICAL_DEVICE=1 explicitly.
EOF
  exit 1
fi

say "Target emulator: $SERIAL"
adb_target wait-for-device

BOOT_COMPLETED=""
for _ in $(seq 1 60); do
  BOOT_COMPLETED="$(adb_target shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  [ "$BOOT_COMPLETED" = "1" ] && break
  sleep 1
done
[ "$BOOT_COMPLETED" = "1" ] || fail "Emulator did not finish booting"

cd "$ROOT_DIR"

if [ "$SKIP_STATIC" != "1" ]; then
  say "Unit tests + Android lint"
  "${GRADLE_CMD[@]}"     :app:testDebugUnitTest     :app:lintDebug     :helper:lintDebug     | tee "$REPORT_DIR/static-checks.txt"
fi

say "Build debug Main, Helper and instrumentation APKs"
"${GRADLE_CMD[@]}"   :helper:assembleDebug   :app:assembleDebug   :app:assembleDebugAndroidTest   | tee "$REPORT_DIR/build.txt"

MAIN_APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
HELPER_APK="$ROOT_DIR/helper/build/outputs/apk/debug/helper-debug.apk"
TEST_APK="$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

for apk in "$MAIN_APK" "$HELPER_APK" "$TEST_APK"; do
  [ -f "$apk" ] || fail "Missing APK: $apk"
done

say "Clean-install Main + Helper + instrumentation package"
adb_target uninstall com.med.sleepmanager.test >/dev/null 2>&1 || true
adb_target uninstall com.med.sleepmanager >/dev/null 2>&1 || true
adb_target uninstall com.med.sleepmanager.helper >/dev/null 2>&1 || true

adb_target install -r -t "$HELPER_APK"   | tee "$REPORT_DIR/install-helper.txt"
adb_target install -r -t "$MAIN_APK"   | tee "$REPORT_DIR/install-main.txt"
adb_target install -r -t "$TEST_APK"   | tee "$REPORT_DIR/install-test.txt"

adb_target shell pm grant   com.med.sleepmanager   android.permission.POST_NOTIFICATIONS   >/dev/null 2>&1 || true

# Custom delay uses Android's exact-alarm special access. Grant it on the
# disposable emulator so the real Advanced-settings toggle can be exercised.
adb_target shell cmd appops set   com.med.sleepmanager   SCHEDULE_EXACT_ALARM   allow >/dev/null 2>&1 || true

EXPECTED_MAIN_VERSION="$(sed -n 's/^SLEEPMANAGER_VERSION_NAME=//p' gradle.properties)"
EXPECTED_HELPER_VERSION="$(sed -n 's/^SLEEPMANAGER_HELPER_VERSION_NAME=//p' gradle.properties)"
ACTUAL_MAIN_VERSION="$(adb_target shell dumpsys package com.med.sleepmanager   | sed -n 's/.*versionName=//p' | head -n 1 | tr -d '\r')"
ACTUAL_HELPER_VERSION="$(adb_target shell dumpsys package com.med.sleepmanager.helper   | sed -n 's/.*versionName=//p' | head -n 1 | tr -d '\r')"

[ "$ACTUAL_MAIN_VERSION" = "$EXPECTED_MAIN_VERSION" ]   || fail "Main version mismatch: expected $EXPECTED_MAIN_VERSION, got $ACTUAL_MAIN_VERSION"
[ "$ACTUAL_HELPER_VERSION" = "$EXPECTED_HELPER_VERSION" ]   || fail "Helper version mismatch: expected $EXPECTED_HELPER_VERSION, got $ACTUAL_HELPER_VERSION"

say "Run complete instrumentation/regression suite"
adb_target logcat -c

set +e
adb_target shell am instrument -w -r   com.med.sleepmanager.test/androidx.test.runner.AndroidJUnitRunner   | tee "$INSTRUMENTATION_LOG"
INSTRUMENTATION_EXIT=${PIPESTATUS[0]}
set -e

[ "$INSTRUMENTATION_EXIT" -eq 0 ]   || fail "Instrumentation command exited with code $INSTRUMENTATION_EXIT"

if grep -q "FAILURES!!!" "$INSTRUMENTATION_LOG"; then
  fail "Instrumentation reported test failures"
fi

if ! grep -Eq '^OK \([0-9]+ tests?\)' "$INSTRUMENTATION_LOG"; then
  fail "Instrumentation did not report a successful test summary"
fi

say "Cold-start / background / process-restart lifecycle smoke"
adb_target shell am force-stop com.med.sleepmanager
START_OUTPUT="$(adb_target shell am start -W -n com.med.sleepmanager/.MainActivity)"
printf '%s\n' "$START_OUTPUT" | tee "$REPORT_DIR/cold-start.txt"

printf '%s\n' "$START_OUTPUT" | grep -q "Status: ok"   || fail "Cold launch did not return Status: ok"

sleep 1
PID="$(adb_target shell pidof com.med.sleepmanager | tr -d '\r')"
[ -n "$PID" ] || fail "SleepManager process missing after cold launch"

adb_target shell input keyevent 3
sleep 1
adb_target shell am kill com.med.sleepmanager >/dev/null 2>&1 || true
sleep 1

RESTART_OUTPUT="$(adb_target shell am start -W -n com.med.sleepmanager/.MainActivity)"
printf '%s\n' "$RESTART_OUTPUT" | tee "$REPORT_DIR/process-restart.txt"
printf '%s\n' "$RESTART_OUTPUT" | grep -q "Status: ok"   || fail "Process restart launch did not return Status: ok"

say "Portrait/landscape recreation smoke"
ORIGINAL_ACCEL="$(adb_target shell settings get system accelerometer_rotation | tr -d '\r')"
ORIGINAL_ROTATION="$(adb_target shell settings get system user_rotation | tr -d '\r')"

adb_target shell settings put system accelerometer_rotation 0
adb_target shell settings put system user_rotation 1
sleep 1
adb_target shell settings put system user_rotation 0
sleep 1

if [ "$ORIGINAL_ACCEL" != "null" ] && [ -n "$ORIGINAL_ACCEL" ]; then
  adb_target shell settings put system accelerometer_rotation "$ORIGINAL_ACCEL"
fi
if [ "$ORIGINAL_ROTATION" != "null" ] && [ -n "$ORIGINAL_ROTATION" ]; then
  adb_target shell settings put system user_rotation "$ORIGINAL_ROTATION"
fi

adb_target shell wm size reset >/dev/null 2>&1 || true
adb_target shell wm density reset >/dev/null 2>&1 || true

say "Scan runtime log for app crashes / ANRs"
collect_diagnostics

if grep -q "ANR in com.med.sleepmanager" "$LOGCAT_FILE"; then
  fail "ANR detected for SleepManager"
fi

if grep -A 6 "FATAL EXCEPTION" "$LOGCAT_FILE"   | grep -q "Process: com.med.sleepmanager"; then
  fail "Fatal exception detected for SleepManager"
fi

printf '\nPASS\n' >> "$SUMMARY_FILE"
cat <<EOF

========================================
SleepManager $EXPECTED_MAIN_VERSION emulator regression
PASS
Target: $SERIAL
Main:   $ACTUAL_MAIN_VERSION
Helper: $ACTUAL_HELPER_VERSION
Report: $REPORT_DIR
========================================
EOF
