#!/usr/bin/env bash
set -Eeuo pipefail

SERIAL="${1:-${ADB_SERIAL:-emulator-5554}}"
ALLOW_PHYSICAL_DEVICE="${ALLOW_PHYSICAL_DEVICE:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$SDK_ROOT/platform-tools/adb}"

MAIN_APK="$SCRIPT_DIR/SleepManager-debug.apk"
HELPER_APK="$SCRIPT_DIR/SleepManager-Helper-debug.apk"
TEST_APK="$SCRIPT_DIR/SleepManager-tests.apk"

if [ ! -x "$ADB_BIN" ]; then
  echo "adb not found at: $ADB_BIN" >&2
  exit 1
fi

for apk in "$MAIN_APK" "$HELPER_APK" "$TEST_APK"; do
  [ -f "$apk" ] || {
    echo "Missing APK: $apk" >&2
    exit 1
  }
done

if [[ "$SERIAL" != emulator-* && "$ALLOW_PHYSICAL_DEVICE" != "1" ]]; then
  cat >&2 <<EOF
Refusing to reset packages on '$SERIAL'.
Use an emulator serial, or set ALLOW_PHYSICAL_DEVICE=1 explicitly.
EOF
  exit 1
fi

adb_target() {
  "$ADB_BIN" -s "$SERIAL" "$@"
}

fail() {
  echo
  echo "[FAIL] $*" >&2
  adb_target logcat -d > "$SCRIPT_DIR/logcat-failure.txt" 2>/dev/null || true
  exit 1
}

echo "=== Target emulator: $SERIAL ==="
adb_target wait-for-device

BOOT_COMPLETED=""
for _ in $(seq 1 60); do
  BOOT_COMPLETED="$(adb_target shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  [ "$BOOT_COMPLETED" = "1" ] && break
  sleep 1
done
[ "$BOOT_COMPLETED" = "1" ] || fail "Emulator did not finish booting"

echo "=== Reset and install Main + Helper + test APK ==="
adb_target uninstall com.med.sleepmanager.test >/dev/null 2>&1 || true
adb_target uninstall com.med.sleepmanager >/dev/null 2>&1 || true
adb_target uninstall com.med.sleepmanager.helper >/dev/null 2>&1 || true

adb_target install -r -t "$HELPER_APK" || fail "Helper install failed"
adb_target install -r -t "$MAIN_APK" || fail "Main install failed"
adb_target install -r -t "$TEST_APK" || fail "Test APK install failed"

adb_target shell pm grant   com.med.sleepmanager   android.permission.POST_NOTIFICATIONS   >/dev/null 2>&1 || true

adb_target shell cmd appops set   com.med.sleepmanager   SCHEDULE_EXACT_ALARM   allow >/dev/null 2>&1 || true

MAIN_VERSION="$(adb_target shell dumpsys package com.med.sleepmanager   | sed -n 's/.*versionName=//p' | head -n 1 | tr -d '\r')"
HELPER_VERSION="$(adb_target shell dumpsys package com.med.sleepmanager.helper   | sed -n 's/.*versionName=//p' | head -n 1 | tr -d '\r')"

echo "Main:   $MAIN_VERSION"
echo "Helper: $HELPER_VERSION"

echo "=== Run full instrumentation/regression suite ==="
adb_target logcat -c

set +e
adb_target shell am instrument -w -r   com.med.sleepmanager.test/androidx.test.runner.AndroidJUnitRunner   | tee "$SCRIPT_DIR/instrumentation.txt"
INSTRUMENTATION_EXIT=${PIPESTATUS[0]}
set -e

[ "$INSTRUMENTATION_EXIT" -eq 0 ]   || fail "Instrumentation command exited with code $INSTRUMENTATION_EXIT"

if grep -q "FAILURES!!!" "$SCRIPT_DIR/instrumentation.txt"; then
  fail "Instrumentation reported test failures"
fi

if ! grep -Eq '^OK \([0-9]+ tests?\)' "$SCRIPT_DIR/instrumentation.txt"; then
  fail "Instrumentation did not report a successful summary"
fi

echo "=== Cold-start / process-restart smoke ==="
adb_target shell am force-stop com.med.sleepmanager
START_OUTPUT="$(adb_target shell am start -W -n com.med.sleepmanager/.MainActivity)"
printf '%s\n' "$START_OUTPUT" | tee "$SCRIPT_DIR/cold-start.txt"
printf '%s\n' "$START_OUTPUT" | grep -q "Status: ok"   || fail "Cold launch did not return Status: ok"

sleep 1
PID="$(adb_target shell pidof com.med.sleepmanager | tr -d '\r')"
[ -n "$PID" ] || fail "SleepManager process missing after cold launch"

adb_target shell input keyevent 3
sleep 1
adb_target shell am kill com.med.sleepmanager >/dev/null 2>&1 || true
sleep 1

RESTART_OUTPUT="$(adb_target shell am start -W -n com.med.sleepmanager/.MainActivity)"
printf '%s\n' "$RESTART_OUTPUT" | tee "$SCRIPT_DIR/process-restart.txt"
printf '%s\n' "$RESTART_OUTPUT" | grep -q "Status: ok"   || fail "Process restart launch did not return Status: ok"

echo "=== Portrait/landscape recreation smoke ==="
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

echo "=== Run real sleep/wake end-to-end scenarios ==="
if [ "${SKIP_REAL_E2E:-0}" != "1" ]; then
  REAL_E2E_SCRIPT="$SCRIPT_DIR/run-real-e2e.sh"
  [ -x "$REAL_E2E_SCRIPT" ] || fail "Real E2E script is missing from the bundle"
  "$REAL_E2E_SCRIPT" "$SERIAL" || fail "Real sleep/wake end-to-end suite failed"
else
  echo "SKIP_REAL_E2E=1 -> skipping real sleep/wake scenarios"
fi

echo "=== Scan runtime log for crashes / ANRs ==="
adb_target logcat -d > "$SCRIPT_DIR/logcat.txt" 2>/dev/null || true

if grep -q "ANR in com.med.sleepmanager" "$SCRIPT_DIR/logcat.txt"; then
  fail "ANR detected for SleepManager"
fi

if grep -A 6 "FATAL EXCEPTION" "$SCRIPT_DIR/logcat.txt"   | grep -q "Process: com.med.sleepmanager"; then
  fail "Fatal exception detected for SleepManager"
fi

cat <<EOF

========================================
SleepManager local emulator regression + real E2E
PASS
Target: $SERIAL
Main:   $MAIN_VERSION
Helper: $HELPER_VERSION
========================================
EOF
