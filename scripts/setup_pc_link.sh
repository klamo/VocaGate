#!/usr/bin/env bash
set -euo pipefail

PORT="27183"
DEVICE_SERIAL=""

usage() {
  cat <<'EOF'
Usage: scripts/setup_pc_link.sh [options]

Create the adb reverse tunnel used by the Android app to reach the desktop companion.

Options:
  --device SERIAL   Use a specific adb device serial.
  --port PORT       Use a custom port. Default: 27183.
  --remove          Remove the reverse tunnel instead of creating it.
  --help            Show this help.
EOF
}

fail() {
  echo "Error: $*" >&2
  exit 1
}

REMOVE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      [[ $# -ge 2 ]] || fail "--device requires a serial"
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --port)
      [[ $# -ge 2 ]] || fail "--port requires a value"
      PORT="$2"
      shift 2
      ;;
    --remove)
      REMOVE=1
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

ADB="$(find_adb)" || fail "adb not found. Install Android SDK platform-tools, then add platform-tools to PATH or set ANDROID_HOME."

if [[ -n "$DEVICE_SERIAL" ]]; then
  ADB_DEVICE_ARGS=(-s "$DEVICE_SERIAL")
else
  ADB_DEVICE_ARGS=()
fi

"$ADB" start-server >/dev/null

connected_devices="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
if [[ -z "$connected_devices" ]]; then
  fail "no authorized adb device found. Connect the phone by USB, enable USB debugging, unlock the phone, and tap Allow on the authorization prompt."
fi

if [[ -z "$DEVICE_SERIAL" ]]; then
  device_count="$(printf '%s\n' "$connected_devices" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "$device_count" != "1" ]]; then
    echo "Connected devices:" >&2
    printf '%s\n' "$connected_devices" >&2
    fail "multiple devices found. Re-run with --device SERIAL."
  fi
fi

if [[ "$REMOVE" == "1" ]]; then
  "$ADB" "${ADB_DEVICE_ARGS[@]}" reverse --remove "tcp:$PORT" || true
  echo "Removed adb reverse tcp:$PORT."
else
  "$ADB" "${ADB_DEVICE_ARGS[@]}" reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  set +e
  reverse_output="$("$ADB" "${ADB_DEVICE_ARGS[@]}" reverse "tcp:$PORT" "tcp:$PORT" 2>&1)"
  reverse_status=$?
  set -e
  if [[ "$reverse_status" != "0" ]]; then
    echo "$reverse_output" >&2
    if [[ "$reverse_output" == *"Address already in use"* ]]; then
      echo "Error: tcp:$PORT is already occupied on the Android device." >&2
      echo "Try:" >&2
      echo "  adb reverse --remove-all" >&2
      echo "  adb shell am force-stop com.vocagate.demo" >&2
      echo "  adb shell \"toybox netstat -an | grep $PORT || true\"" >&2
      echo "Then run scripts/setup_pc_link.sh again." >&2
    fi
    exit "$reverse_status"
  fi
  echo "Created adb reverse tcp:$PORT -> tcp:$PORT."
fi
