#!/usr/bin/env python3
"""MediaPipe Face Landmarker sidecar for VocaGate Desktop.

Protocol:
  stdin:  one JSON frame per line, with raw RGB bytes in base64.
  stdout: one JSON recognition result per processed frame.
  stderr: diagnostics only.
"""

from __future__ import annotations

import argparse
import base64
import json
import math
import sys
import time
from pathlib import Path
from typing import Optional

try:
    import mediapipe as mp
    import numpy as np
except Exception as exc:  # pragma: no cover - exercised on user machines.
    print(f"Missing Python dependency: {exc}", file=sys.stderr, flush=True)
    sys.exit(3)


MODEL_FILE_NAME = "face_landmarker.task"
OPEN_SCORE_THRESHOLD = 0.18

LIP_CONTOUR_INDICES = [
    61,
    146,
    91,
    181,
    84,
    17,
    314,
    405,
    321,
    375,
    291,
    409,
    270,
    269,
    267,
    0,
    37,
    39,
    40,
    185,
    61,
    78,
    95,
    88,
    178,
    87,
    14,
    317,
    402,
    318,
    324,
    308,
    415,
    310,
    311,
    312,
    13,
    82,
    81,
    80,
    191,
    78,
]
LEFT_MOUTH = 61
RIGHT_MOUTH = 291
UPPER_INNER = (13, 82, 312)
LOWER_INNER = (14, 87, 317)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="", help="Path to face_landmarker.task")
    parser.add_argument("--healthcheck", action="store_true", help="Initialize the model and exit")
    args = parser.parse_args()

    model_path = resolve_model_path(args.model)
    if model_path is None:
        print(
            "MediaPipe model not found. Run scripts/fetch_mediapipe_models.sh first.",
            file=sys.stderr,
            flush=True,
        )
        return 2

    with create_face_landmarker(model_path) as landmarker:
        if args.healthcheck:
            print('{"type":"health","status":"ok"}', flush=True)
            return 0
        state = SidecarState()
        print(json.dumps({"type": "ready", "model": str(model_path)}), flush=True)
        for raw_line in sys.stdin:
            raw_line = raw_line.strip()
            if not raw_line:
                continue
            try:
                request = json.loads(raw_line)
                if request.get("type") != "frame":
                    continue
                response = process_frame(landmarker, state, request)
            except Exception as exc:  # Keep Java alive and able to fall back.
                print(f"Sidecar frame error: {exc}", file=sys.stderr, flush=True)
                response = no_face_response("MediaPipe frame error", 0.0)
            print(json.dumps(response, separators=(",", ":"), ensure_ascii=False), flush=True)

    return 0


def create_face_landmarker(model_path: Path):
    options = mp.tasks.vision.FaceLandmarkerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_path=str(model_path)),
        running_mode=mp.tasks.vision.RunningMode.VIDEO,
        num_faces=1,
        output_face_blendshapes=True,
        min_face_detection_confidence=0.45,
        min_face_presence_confidence=0.45,
        min_tracking_confidence=0.45,
    )

    return mp.tasks.vision.FaceLandmarker.create_from_options(options)


class SidecarState:
    def __init__(self) -> None:
        self.previous_lip_points: Optional[list[tuple[float, float]]] = None
        self.previous_mouth_open_ratio: Optional[float] = None
        self.previous_mouth_center_y: Optional[float] = None
        self.previous_timestamp_ms = -1
        self.previous_output_time = time.monotonic()


def process_frame(landmarker, state: SidecarState, request: dict) -> dict:
    width = int(request["width"])
    height = int(request["height"])
    source_width = int(request.get("source_width", width))
    source_height = int(request.get("source_height", height))
    timestamp_ms = int(request.get("timestamp_ms", 0))
    if timestamp_ms <= state.previous_timestamp_ms:
        timestamp_ms = state.previous_timestamp_ms + 1
    state.previous_timestamp_ms = timestamp_ms

    rgb = base64.b64decode(request["rgb"])
    frame = np.frombuffer(rgb, dtype=np.uint8).reshape((height, width, 3))
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame)
    result = landmarker.detect_for_video(mp_image, timestamp_ms)
    fps = compute_fps(state)

    if not result.face_landmarks:
        state.previous_lip_points = None
        state.previous_mouth_open_ratio = None
        state.previous_mouth_center_y = None
        return no_face_response("MediaPipe no face", fps)

    face = result.face_landmarks[0]
    lip_points_norm = [(face[index].x, face[index].y) for index in LIP_CONTOUR_INDICES]
    lip_points_source = [
        [round(clamp01(x) * source_width, 2), round(clamp01(y) * source_height, 2)]
        for x, y in lip_points_norm
    ]

    mouth_open_ratio = compute_mouth_open_ratio(face)
    lip_motion_score = compute_motion_score(state, lip_points_norm, face)
    aperture_motion_score = compute_mouth_open_motion_score(state, mouth_open_ratio)
    mouth_center_y = compute_mouth_center_y(lip_points_norm)
    center_motion_score = compute_mouth_center_motion_score(state, mouth_center_y, lip_points_norm)
    blendshape_score = compute_blendshape_score(result)
    score = compute_speaking_score(
        mouth_open_ratio,
        aperture_motion_score,
        lip_motion_score,
        center_motion_score,
        blendshape_score,
    )
    state.previous_lip_points = lip_points_norm
    state.previous_mouth_open_ratio = mouth_open_ratio
    state.previous_mouth_center_y = mouth_center_y

    roi = compute_roi(lip_points_source, source_width, source_height)
    return {
        "type": "recognition",
        "tracking_valid": True,
        "score": round(score, 4),
        "motion": round(aperture_motion_score, 4),
        "fps": round(fps, 2),
        "status": "MediaPipe speaking" if score >= OPEN_SCORE_THRESHOLD else "MediaPipe quiet",
        "roi": roi,
        "landmarks": lip_points_source,
        "mouth_open": round(mouth_open_ratio, 4),
        "lip_motion": round(lip_motion_score, 4),
        "center_motion": round(center_motion_score, 4),
    }


def compute_mouth_open_ratio(face) -> float:
    left = face[LEFT_MOUTH]
    right = face[RIGHT_MOUTH]
    width = max(0.001, distance(left.x, left.y, right.x, right.y))
    upper_y = sum(face[index].y for index in UPPER_INNER) / len(UPPER_INNER)
    lower_y = sum(face[index].y for index in LOWER_INNER) / len(LOWER_INNER)
    return clamp01(abs(lower_y - upper_y) / width)


def compute_motion_score(state: SidecarState, lip_points: list[tuple[float, float]], face) -> float:
    if state.previous_lip_points is None or len(state.previous_lip_points) != len(lip_points):
        return 0.0
    previous_points = normalize_lip_points(state.previous_lip_points)
    current_points = normalize_lip_points(lip_points)
    total = 0.0
    for (x, y), (prev_x, prev_y) in zip(current_points, previous_points):
        total += distance(x, y, prev_x, prev_y)
    average_delta = total / len(lip_points)
    return clamp01(average_delta / 0.018)


def compute_mouth_open_motion_score(state: SidecarState, mouth_open_ratio: float) -> float:
    if state.previous_mouth_open_ratio is None:
        return 0.0
    return clamp01(abs(mouth_open_ratio - state.previous_mouth_open_ratio) / 0.028)


def compute_mouth_center_y(lip_points: list[tuple[float, float]]) -> float:
    left = point_for_landmark(lip_points, LEFT_MOUTH)
    right = point_for_landmark(lip_points, RIGHT_MOUTH)
    return (left[1] + right[1]) / 2.0


def compute_mouth_center_motion_score(
    state: SidecarState,
    mouth_center_y: float,
    lip_points: list[tuple[float, float]],
) -> float:
    if state.previous_mouth_center_y is None:
        return 0.0
    left = point_for_landmark(lip_points, LEFT_MOUTH)
    right = point_for_landmark(lip_points, RIGHT_MOUTH)
    mouth_width = max(0.001, distance(left[0], left[1], right[0], right[1]))
    normalized_delta = abs(mouth_center_y - state.previous_mouth_center_y) / mouth_width
    return clamp01(normalized_delta / 0.055)


def normalize_lip_points(lip_points: list[tuple[float, float]]) -> list[tuple[float, float]]:
    left = point_for_landmark(lip_points, LEFT_MOUTH)
    right = point_for_landmark(lip_points, RIGHT_MOUTH)
    center_x = (left[0] + right[0]) / 2.0
    center_y = (left[1] + right[1]) / 2.0
    mouth_width = max(0.001, distance(left[0], left[1], right[0], right[1]))
    angle = math.atan2(right[1] - left[1], right[0] - left[0])
    cos_a = math.cos(-angle)
    sin_a = math.sin(-angle)
    normalized = []
    for x, y in lip_points:
        dx = (x - center_x) / mouth_width
        dy = (y - center_y) / mouth_width
        normalized.append((dx * cos_a - dy * sin_a, dx * sin_a + dy * cos_a))
    return normalized


def point_for_landmark(lip_points: list[tuple[float, float]], landmark_index: int) -> tuple[float, float]:
    for ordinal, index in enumerate(LIP_CONTOUR_INDICES):
        if index == landmark_index:
            return lip_points[ordinal]
    raise ValueError(f"lip landmark {landmark_index} missing")


def compute_speaking_score(
    mouth_open_ratio: float,
    aperture_motion_score: float,
    lip_motion_score: float,
    center_motion_score: float,
    blendshape_score: float,
) -> float:
    open_score = clamp01((mouth_open_ratio - 0.025) / 0.12)
    center_suppression = clamp01(center_motion_score * 1.15)
    aperture_score = aperture_motion_score * (0.45 + open_score * 0.55)
    supported_lip_score = lip_motion_score * min(1.0, aperture_motion_score / 0.30) * 0.25
    dynamic_blendshape_score = blendshape_score * min(1.0, aperture_motion_score / 0.30) * 0.45
    return clamp01(max(aperture_score, supported_lip_score, dynamic_blendshape_score) * (1.0 - center_suppression))


def compute_blendshape_score(result) -> float:
    categories = result.face_blendshapes[0] if result.face_blendshapes else []
    best = 0.0
    for category in categories:
        name = getattr(category, "category_name", "")
        score = float(getattr(category, "score", 0.0))
        if name in {"jawOpen", "mouthOpen", "mouthFunnel", "mouthPucker"}:
            best = max(best, score)
    return clamp01(best)


def compute_roi(points: list[list[float]], source_width: int, source_height: int) -> dict:
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    min_x = min(xs)
    max_x = max(xs)
    min_y = min(ys)
    max_y = max(ys)
    pad_x = max(8.0, (max_x - min_x) * 0.28)
    pad_y = max(6.0, (max_y - min_y) * 0.45)
    x = max(0.0, min_x - pad_x)
    y = max(0.0, min_y - pad_y)
    right = min(float(source_width), max_x + pad_x)
    bottom = min(float(source_height), max_y + pad_y)
    return {
        "x": round(x, 2),
        "y": round(y, 2),
        "width": round(max(1.0, right - x), 2),
        "height": round(max(1.0, bottom - y), 2),
        "label": "MediaPipe lips",
    }


def no_face_response(status: str, fps: float) -> dict:
    return {
        "type": "recognition",
        "tracking_valid": False,
        "score": 0.0,
        "motion": 0.0,
        "fps": round(fps, 2),
        "status": status,
        "roi": {"x": 0, "y": 0, "width": 0, "height": 0, "label": status},
        "landmarks": [],
    }


def compute_fps(state: SidecarState) -> float:
    now = time.monotonic()
    elapsed = now - state.previous_output_time
    state.previous_output_time = now
    if elapsed <= 0:
        return 0.0
    return min(240.0, 1.0 / elapsed)


def resolve_model_path(configured: str) -> Optional[Path]:
    candidates: list[Path] = []
    if configured:
        candidates.append(Path(configured))
    script_dir = Path(__file__).resolve().parent
    candidates.extend(
        [
            script_dir / "models" / MODEL_FILE_NAME,
            script_dir.parent / "resources" / "mediapipe" / MODEL_FILE_NAME,
        ]
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return None


def clamp01(value: float) -> float:
    if math.isnan(value):
        return 0.0
    return max(0.0, min(1.0, value))


def distance(x1: float, y1: float, x2: float, y2: float) -> float:
    return math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))


if __name__ == "__main__":
    raise SystemExit(main())
