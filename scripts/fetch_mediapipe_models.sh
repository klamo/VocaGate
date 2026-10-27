#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -d "$ROOT_DIR/sidecar" ]]; then
  MODEL_DIR="$ROOT_DIR/sidecar/models"
else
  MODEL_DIR="$ROOT_DIR/desktop/src/main/python/models"
fi
MODEL_PATH="$MODEL_DIR/face_landmarker.task"
MODEL_URL="https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"

mkdir -p "$MODEL_DIR"

echo "Downloading MediaPipe Face Landmarker model..."
curl -L --fail --retry 3 --output "$MODEL_PATH" "$MODEL_URL"
echo "Saved: $MODEL_PATH"
