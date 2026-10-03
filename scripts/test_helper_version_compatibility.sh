#!/usr/bin/env bash
set -Eeuo pipefail

SERIAL="${1:-${ADB_SERIAL:-emulator-5554}}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$SDK_ROOT/platform-tools/adb}"
STABLE_HELPER_APK="${STABLE_HELPER_APK:-}"
STABLE_MAIN_APK="${STABLE_MAIN_APK:-}"
CURRENT_HELPER_APK="$ROOT_DIR/helper/build/outputs/apk/debug/helper-debug.apk"
CURRENT_HELPER_VERSION=$(sed -n 's/^SLEEPMANAGER_HELPER_VERSION_NAME=//p' "$ROOT_DIR/gradle.properties")
STABLE_HELPER_VERSION="${STABLE_HELPER_VERSION:-1.1.1}"
STABLE_MAIN_VERSION="${STABLE_MAIN_VERSION:-0.6.1}"
TEST_RUNNER="com.med.sleepmanager.test/androidx.test.runner.AndroidJUnitRunner"
LEGACY_TEST="com.med.sleepmanager.HelperVersionCompatibilityTest#legacyHelper111_realRoundTripWithoutCycleId_isAcceptedByMain"
CURRENT_TEST="com.med.sleepmanager.HelperVersionCompatibilityTest#helper112_realRoundTripEchoesCycleId_andCompletesMain"
STABLE_MAIN_TEST="com.med.sleepmanager.StableMainHelperCompatibilityTest#stableMain061_acceptsHelper112CorrelatedResults"
REPORT_DIR="$ROOT_DIR/build/helper-compatibility"

mkdir -p "$REPORT_DIR"

adb_target() {
  "$ADB_BIN" -s "$SERIAL" "$@"
}

fail() {
  echo "[FAIL] $*" >&2
  adb_target logcat -d > "$REPORT_DIR/logcat-failure.txt" 2>/dev/null || true
  exit 1
}

helper_version() {
  adb_target shell dumpsys package com.med.sleepmanager.helper \
    | sed -n 's/.*versionName=//p' \
    | head -n 1 \
    | tr -d '\r'
}

ensure_awake() {
  adb_target shell input keyevent 224 >/dev/null 2>&1 || true
  local deadline=$((SECONDS + 8))
  while (( SECONDS < deadline )); do
    if adb_target shell dumpsys power 2>/dev/null | grep -Eq 'mWakefulness=(Awake|Dreaming)'; then
      return 0
    fi
    sleep 1
  done
  return 1
}

run_case() {
  local test_name="$1"
  local log_name="$2"
  local mode_key="${3:-helperCompatibility}"

  ensure_awake || fail "Emulator did not reach awake state before $test_name"
  sleep 1

  set +e
  adb_target shell am instrument -w -r \
    -e "$mode_key" true \
    -e expectedCurrentHelperVersion "$CURRENT_HELPER_VERSION" \
    -e expectedStableHelperVersion "$STABLE_HELPER_VERSION" \
    -e expectedStableMainVersion "$STABLE_MAIN_VERSION" \
    -e class "$test_name" "$TEST_RUNNER" \
    | tee "$REPORT_DIR/$log_name"
  local status=${PIPESTATUS[0]}
  set -e

  [ "$status" -eq 0 ] || fail "Instrumentation failed for $test_name"
  grep -Eq '^OK \([0-9]+ tests?\)' "$REPORT_DIR/$log_name" \
    || fail "No successful instrumentation summary for $test_name"
}

[ -x "$ADB_BIN" ] || { echo "adb not found at $ADB_BIN" >&2; exit 1; }
[ -n "$STABLE_HELPER_APK" ] || { echo "STABLE_HELPER_APK is required" >&2; exit 1; }
[ -f "$STABLE_HELPER_APK" ] || { echo "Stable Helper APK not found: $STABLE_HELPER_APK" >&2; exit 1; }
[ -n "$STABLE_MAIN_APK" ] || { echo "STABLE_MAIN_APK is required" >&2; exit 1; }
[ -f "$STABLE_MAIN_APK" ] || { echo "Stable Main APK not found: $STABLE_MAIN_APK" >&2; exit 1; }
[ -f "$CURRENT_HELPER_APK" ] || { echo "Current Helper APK not found: $CURRENT_HELPER_APK" >&2; exit 1; }

adb_target wait-for-device
adb_target shell pm path com.med.sleepmanager >/dev/null 2>&1 \
  || fail "Current Main is not installed"
adb_target shell pm path com.med.sleepmanager.test >/dev/null 2>&1 \
  || fail "Instrumentation APK is not installed"

echo "=== Main 0.7 + stable Helper 1.1.1 ==="
adb_target shell am force-stop com.med.sleepmanager >/dev/null 2>&1 || true
adb_target uninstall com.med.sleepmanager.helper >/dev/null 2>&1 || true
adb_target install -t "$STABLE_HELPER_APK" >/dev/null \
  || fail "Unable to install stable Helper 1.1.1"

[ "$(helper_version)" = "$STABLE_HELPER_VERSION" ] \
  || fail "Expected Helper $STABLE_HELPER_VERSION, got $(helper_version)"
run_case "$LEGACY_TEST" "helper-1.1.1.txt"

echo "=== Upgrade Helper 1.1.1 -> 1.1.2 in place ==="
adb_target install -r -t "$CURRENT_HELPER_APK" >/dev/null \
  || fail "Unable to upgrade Helper to 1.1.2"

[ "$(helper_version)" = "$CURRENT_HELPER_VERSION" ] \
  || fail "Expected Helper $CURRENT_HELPER_VERSION after upgrade, got $(helper_version)"
run_case "$CURRENT_TEST" "helper-1.1.2.txt"

echo "=== Stable Main 0.6.1 + Helper 1.1.2 ==="
adb_target shell am force-stop com.med.sleepmanager >/dev/null 2>&1 || true
adb_target install -r -d -t "$STABLE_MAIN_APK" >/dev/null \
  || fail "Unable to downgrade debug Main to stable 0.6.1 in place"

MAIN_VERSION=$(adb_target shell dumpsys package com.med.sleepmanager \
  | sed -n 's/.*versionName=//p' \
  | head -n 1 \
  | tr -d '\r')
[ "$MAIN_VERSION" = "$STABLE_MAIN_VERSION" ] \
  || fail "Expected Main $STABLE_MAIN_VERSION after downgrade, got $MAIN_VERSION"

run_case "$STABLE_MAIN_TEST" "stable-main-0.6.1-helper-1.1.2.txt" "stableMainHelperCompatibility"

echo "PASS: Main 0.7 accepts Helper 1.1.1/1.1.2 and stable Main 0.6.1 accepts Helper 1.1.2"
