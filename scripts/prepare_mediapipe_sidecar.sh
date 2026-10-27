#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <macos-arm64|windows-x64>" >&2
  exit 2
fi

if [[ "$1" != "macos-arm64" && "$1" != "windows-x64" ]]; then
  echo "Unsupported target: $1 (expected macos-arm64 or windows-x64)" >&2
  exit 2
fi

TARGET="$1"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${VOCAGATE_PYTHON:-python3}"
PYTHON_DIR="$REPO_ROOT/desktop/src/main/python"
LOCK_PATH="$PYTHON_DIR/models/face_landmarker.lock.json"
REQUIREMENTS="$PYTHON_DIR/requirements-release.txt"
LOCAL_MODEL="$PYTHON_DIR/models/face_landmarker.task"
BUILD_DIR="$REPO_ROOT/desktop/build/sidecar/$TARGET"
VENV_DIR="$BUILD_DIR/venv"
MODEL_DIR="$BUILD_DIR/models"
MODEL_PATH="$MODEL_DIR/face_landmarker.task"

case "$TARGET" in
  macos-arm64)
    if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "arm64" ]]; then
      echo "macos-arm64 staging must run on a macOS ARM64 host; cross-compilation is not supported." >&2
      exit 2
    fi
    ;;
  windows-x64)
    if [[ "$(uname -s)" != MINGW* && "$(uname -s)" != MSYS* && "$(uname -s)" != CYGWIN* ]]; then
      echo "windows-x64 staging must run from the Windows .bat helper on a Windows x64 host; cross-compilation is not supported." >&2
      exit 2
    fi
    ;;
esac

eval "$("$PYTHON_BIN" - "$LOCK_PATH" <<'PY'
import json
import shlex
import sys

lock = json.load(open(sys.argv[1], encoding="utf-8"))
for key in ("url", "sha256", "model_id", "version", "license_source"):
    value = lock.get(key)
    if not isinstance(value, str) or not value:
        raise SystemExit(f"invalid model lock field: {key}")
if "/latest/" in lock["url"]:
    raise SystemExit("model lock URL must not contain /latest/")
if len(lock["sha256"]) != 64 or any(char not in "0123456789abcdef" for char in lock["sha256"]):
    raise SystemExit("model lock sha256 must be lower-case hexadecimal")
for key, value in lock.items():
    print(f"MODEL_{key.upper()}={shlex.quote(value)}")
PY
)"

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

is_verified_model() {
  [[ -f "$1" && "$(sha256_file "$1")" == "$MODEL_SHA256" ]]
}

mkdir -p "$MODEL_DIR"
if ! is_verified_model "$MODEL_PATH"; then
  rm -f "$MODEL_PATH"
  if is_verified_model "$LOCAL_MODEL"; then
    cp "$LOCAL_MODEL" "$MODEL_PATH"
  else
    temp_model="$MODEL_PATH.download"
    rm -f "$temp_model"
    curl --fail --location --retry 3 --output "$temp_model" "$MODEL_URL"
    mv "$temp_model" "$MODEL_PATH"
  fi
fi
if ! is_verified_model "$MODEL_PATH"; then
  echo "MediaPipe model SHA-256 mismatch: $MODEL_PATH" >&2
  exit 1
fi

"$PYTHON_BIN" -m venv "$VENV_DIR"
VENV_PYTHON="$VENV_DIR/bin/python"
"$VENV_PYTHON" -m pip install --requirement "$REQUIREMENTS"

rm -rf "$BUILD_DIR/vocagate-mediapipe" "$BUILD_DIR/pyinstaller-work" "$BUILD_DIR/pyinstaller-spec"
"$VENV_PYTHON" -m PyInstaller --noconfirm --clean --onedir --name vocagate-mediapipe \
  --hidden-import mediapipe.tasks.c --collect-binaries mediapipe.tasks.c \
  --distpath "$BUILD_DIR" --workpath "$BUILD_DIR/pyinstaller-work" --specpath "$BUILD_DIR/pyinstaller-spec" \
  "$PYTHON_DIR/vocagate_mediapipe_sidecar.py"

NOTICE_PATH="$BUILD_DIR/THIRD_PARTY_NOTICES.txt"
cat > "$NOTICE_PATH" <<'EOF'
VocaGate MediaPipe sidecar third-party notices

MediaPipe: Apache-2.0
https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE

NumPy: BSD-3-Clause
https://github.com/numpy/numpy/blob/main/LICENSE.txt

PyInstaller: GPL-2.0-or-later with a bootloader exception
https://github.com/pyinstaller/pyinstaller/blob/develop/COPYING.txt
EOF
"$VENV_PYTHON" -m pip show mediapipe numpy pyinstaller >> "$NOTICE_PATH"
"$VENV_PYTHON" -m pip freeze --all > "$BUILD_DIR/requirements-installed.txt"
cp "$LOCK_PATH" "$BUILD_DIR/face_landmarker.lock.json"
"$VENV_PYTHON" - "$LOCK_PATH" "$TARGET" "$BUILD_DIR/build-metadata.json" <<'PY'
import json
import sys
from pathlib import Path

lock = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
Path(sys.argv[3]).write_text(json.dumps({"target": sys.argv[2], "model": lock}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

echo "Prepared MediaPipe sidecar: $BUILD_DIR/vocagate-mediapipe/vocagate-mediapipe"
