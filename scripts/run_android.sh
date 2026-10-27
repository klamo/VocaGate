#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.vocagate.demo"
MAIN_ACTIVITY="com.vocagate.demo/.MainActivity"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
START_APP=1
DEVICE_SERIAL=""

usage() {
  cat <<'EOF'
Usage: scripts/run_android.sh [options]

Build, install, and start the VocaGate Android demo from the command line.

Options:
  --device SERIAL   Use a specific adb device serial, such as 192.168.1.23:43210.
  --no-start        Install the debug APK but do not launch the app.
  --help           Show this help.

Before running:
  1. Install Android SDK command-line tools or Android Studio SDK.
  2. Make adb available on PATH, or set ANDROID_HOME / ANDROID_SDK_ROOT.
  3. Connect a phone with adb over USB or wireless debugging.
  4. Install Gradle, or add a Gradle wrapper to this project.
EOF
}

fail() {
  echo "Error: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      [[ $# -ge 2 ]] || fail "--device requires a serial"
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --no-start)
      START_APP=0
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "unknown option: $1"
      ;;
  esac
done

find_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi

  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -n "$sdk_root" && -x "$sdk_root/platform-tools/adb" ]]; then
    echo "$sdk_root/platform-tools/adb"
    return
  fi

  return 1
}

find_gradle() {
  if [[ -x "$ROOT_DIR/gradlew" ]]; then
    echo "$ROOT_DIR/gradlew"
    return
  fi

  if command -v gradle >/dev/null 2>&1; then
    command -v gradle
    return
  fi

  return 1
}

ADB="$(find_adb)" || fail "adb not found. Install Android SDK platform-tools, then add platform-tools to PATH or set ANDROID_HOME."
GRADLE_CMD="$(find_gradle)" || fail "gradle not found and ./gradlew is missing. Install Gradle or generate a Gradle wrapper."

if [[ -n "$DEVICE_SERIAL" ]]; then
  ADB_DEVICE_ARGS=(-s "$DEVICE_SERIAL")
else
  ADB_DEVICE_ARGS=()
fi

echo "Using adb: $ADB"
echo "Using Gradle: $GRADLE_CMD"

"$ADB" start-server >/dev/null

connected_devices="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
if [[ -z "$connected_devices" ]]; then
  fail "no authorized adb device found. Pair/connect wireless debugging first, or connect by USB and authorize debugging."
fi

if [[ -z "$DEVICE_SERIAL" ]]; then
  device_count="$(printf '%s\n' "$connected_devices" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "$device_count" != "1" ]]; then
    echo "Connected devices:" >&2
    printf '%s\n' "$connected_devices" >&2
    fail "multiple devices found. Re-run with --device SERIAL."
  fi
fi

cd "$ROOT_DIR"
"$GRADLE_CMD" :app:installDebug

if [[ "$START_APP" == "1" ]]; then
  "$ADB" "${ADB_DEVICE_ARGS[@]}" shell am force-stop "$APP_ID" >/dev/null || true
  "$ADB" "${ADB_DEVICE_ARGS[@]}" shell am start -n "$MAIN_ACTIVITY"
fi

echo "VocaGate Android demo installed."
