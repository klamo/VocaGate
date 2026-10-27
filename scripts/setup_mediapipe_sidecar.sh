#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PYTHON_BIN="${VOCAGATE_PYTHON:-python3}"
VENV_DIR="$ROOT_DIR/.venv-mediapipe"
if [[ -d "$ROOT_DIR/sidecar" ]]; then
  MODEL_DIR="$ROOT_DIR/sidecar/models"
else
  MODEL_DIR="$ROOT_DIR/desktop/src/main/python/models"
fi
MODEL_PATH="$MODEL_DIR/face_landmarker.task"
MODEL_URL="https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"

"$PYTHON_BIN" -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/python" -m pip install "mediapipe>=0.10.35,<0.11" "numpy>=1.26"
mkdir -p "$MODEL_DIR"
curl -L --fail --retry 3 --output "$MODEL_PATH" "$MODEL_URL"

echo "MediaPipe sidecar is ready."
echo "Python: $VENV_DIR/bin/python"
