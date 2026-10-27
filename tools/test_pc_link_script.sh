#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"
DEFAULT_PORT="27183"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

test_no_authorized_device() {
  local fake_bin="$TMP_ROOT/no-device"
  mkdir -p "$fake_bin"

  cat >"$fake_bin/adb" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  start-server)
    exit 0
    ;;
  devices)
    printf 'List of devices attached\n\n'
    exit 0
    ;;
  reverse)
    echo "reverse should not be called without an authorized device" >&2
    exit 9
    ;;
  *)
    echo "unexpected adb args: $*" >&2
    exit 8
    ;;
esac
EOF
  chmod +x "$fake_bin/adb"

  set +e
  output="$(PATH="$fake_bin:$PATH" "$ROOT_DIR/scripts/setup_pc_link.sh" 2>&1)"
  status=$?
  set -e

  if [[ "$status" == "0" ]]; then
    echo "Expected setup_pc_link.sh to fail when no adb device is authorized" >&2
    exit 1
  fi

  if [[ "$output" != *"no authorized adb device found"* ]]; then
    echo "Expected a helpful no-device message, got:" >&2
    echo "$output" >&2
    exit 1
  fi
}

test_existing_reverse_is_replaced() {
  local fake_bin="$TMP_ROOT/existing-reverse"
  local adb_log="$fake_bin/adb.log"
  mkdir -p "$fake_bin"

  cat >"$fake_bin/adb" <<EOF
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "\$*" >> "$adb_log"
case "\${1:-}" in
  start-server)
    exit 0
    ;;
  devices)
    printf 'List of devices attached\nfc8a3d7\tdevice\n'
    exit 0
    ;;
  reverse)
    exit 0
    ;;
  *)
    echo "unexpected adb args: \$*" >&2
    exit 8
    ;;
esac
EOF
  chmod +x "$fake_bin/adb"

  PATH="$fake_bin:$PATH" "$ROOT_DIR/scripts/setup_pc_link.sh" >/dev/null

  if ! grep -q "^reverse --remove tcp:$DEFAULT_PORT$" "$adb_log"; then
    echo "Expected setup_pc_link.sh to remove stale tcp:$DEFAULT_PORT reverse first" >&2
    cat "$adb_log" >&2
    exit 1
  fi

  if ! grep -q "^reverse tcp:$DEFAULT_PORT tcp:$DEFAULT_PORT$" "$adb_log"; then
    echo "Expected setup_pc_link.sh to create tcp:$DEFAULT_PORT reverse" >&2
    cat "$adb_log" >&2
    exit 1
  fi
}

test_address_in_use_message_is_actionable() {
  local fake_bin="$TMP_ROOT/address-in-use"
  mkdir -p "$fake_bin"

  cat >"$fake_bin/adb" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  start-server)
    exit 0
    ;;
  devices)
    printf 'List of devices attached\nfc8a3d7\tdevice\n'
    exit 0
    ;;
  reverse)
    if [[ "${2:-}" == "--remove" ]]; then
      echo "adb: error: listener 'tcp:27183' not found" >&2
      exit 1
    fi
    echo "adb: error: cannot bind listener: Address already in use" >&2
    exit 1
    ;;
  shell)
    printf 'tcp        0      0 127.0.0.1:27183         0.0.0.0:*               LISTEN\n'
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF
  chmod +x "$fake_bin/adb"

  set +e
  output="$(PATH="$fake_bin:$PATH" "$ROOT_DIR/scripts/setup_pc_link.sh" 2>&1)"
  status=$?
  set -e

  if [[ "$status" == "0" ]]; then
    echo "Expected setup_pc_link.sh to fail when adb cannot bind the reverse listener" >&2
    exit 1
  fi

  if [[ "$output" != *"Address already in use"* || "$output" != *"adb reverse --remove-all"* ]]; then
    echo "Expected actionable Address already in use guidance, got:" >&2
    echo "$output" >&2
    exit 1
  fi
}

test_windows_batch_script_matches_usb_flow() {
  local batch_script="$ROOT_DIR/scripts/setup_pc_link.bat"
  local desktop_gradle="$ROOT_DIR/desktop/build.gradle.kts"

  if [[ ! -f "$batch_script" ]]; then
    echo "Expected Windows USB setup script at scripts/setup_pc_link.bat" >&2
    exit 1
  fi

  if ! grep -q 'set "PORT=27183"' "$batch_script"; then
    echo "Expected setup_pc_link.bat to default to tcp:27183" >&2
    exit 1
  fi

  if ! grep -q 'reverse --remove "tcp:%PORT%"' "$batch_script"; then
    echo "Expected setup_pc_link.bat to remove stale reverse first" >&2
    exit 1
  fi

  if ! grep -q 'reverse "tcp:%PORT%" "tcp:%PORT%"' "$batch_script"; then
    echo "Expected setup_pc_link.bat to create the reverse tunnel" >&2
    exit 1
  fi

  if ! grep -q 'ANDROID_HOME' "$batch_script" || ! grep -q 'ANDROID_SDK_ROOT' "$batch_script"; then
    echo "Expected setup_pc_link.bat to check Android SDK environment variables" >&2
    exit 1
  fi

  if ! grep -q 'setup_pc_link.bat' "$desktop_gradle"; then
    echo "Expected desktop distribution to include setup_pc_link.bat" >&2
    exit 1
  fi

  if ! grep -q 'setup_pc_link.sh' "$desktop_gradle"; then
    echo "Expected desktop distribution to include setup_pc_link.sh for macOS/Linux users" >&2
    exit 1
  fi
}

test_no_authorized_device
test_existing_reverse_is_replaced
test_address_in_use_message_is_actionable
test_windows_batch_script_matches_usb_flow
echo "setup_pc_link.sh smoke test passed"
