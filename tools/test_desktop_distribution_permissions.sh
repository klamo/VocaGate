#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ZIP_PATH="$ROOT_DIR/desktop/build/distributions/desktop.zip"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

unzip -q "$ZIP_PATH" -d "$WORK_DIR"

if [[ ! -x "$WORK_DIR/desktop/bin/setup_mediapipe_sidecar.sh" ]]; then
  echo "setup_mediapipe_sidecar.sh is not executable in desktop.zip" >&2
  ls -l "$WORK_DIR/desktop/bin/setup_mediapipe_sidecar.sh" >&2
  exit 1
fi

if [[ ! -x "$WORK_DIR/desktop/bin/setup_pc_link.sh" ]]; then
  echo "setup_pc_link.sh is not executable in desktop.zip" >&2
  ls -l "$WORK_DIR/desktop/bin/setup_pc_link.sh" >&2
  exit 1
fi

echo "Desktop distribution permissions smoke passed"
