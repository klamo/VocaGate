#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODEL_DIR="$ROOT_DIR/desktop/src/main/resources/opencv"
YUNET_URL="https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
YUNET_FILE="$MODEL_DIR/face_detection_yunet_2023mar.onnx"

mkdir -p "$MODEL_DIR"

echo "Downloading YuNet face landmark model..."
curl -L --fail --retry 3 -o "$YUNET_FILE" "$YUNET_URL"
echo "Saved: $YUNET_FILE"
