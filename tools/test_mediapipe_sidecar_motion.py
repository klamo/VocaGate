#!/usr/bin/env python3
import importlib.util
import sys
import types
from pathlib import Path


def main() -> None:
    module = load_sidecar_module()
    rejects_whole_mouth_translation(module)
    keeps_mouth_position_shift_quiet(module)
    keeps_vertical_mouth_shift_with_aperture_jitter_quiet(module)
    scores_mouth_shape_motion(module)
    keeps_static_open_mouth_quiet(module)
    scores_dynamic_open_mouth(module)
    print("MediaPipe sidecar motion smoke passed")


def load_sidecar_module():
    sys.modules.setdefault("mediapipe", types.SimpleNamespace())
    sys.modules.setdefault("numpy", types.SimpleNamespace())
    script_path = Path(__file__).resolve().parents[1] / "desktop/src/main/python/vocagate_mediapipe_sidecar.py"
    spec = importlib.util.spec_from_file_location("vocagate_mediapipe_sidecar", script_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def rejects_whole_mouth_translation(module) -> None:
    previous_face = make_face(module)
    current_face = make_face(module, shift_x=0.025, shift_y=0.018)
    state = module.SidecarState()
    state.previous_lip_points = lip_points(module, previous_face)

    score = module.compute_motion_score(state, lip_points(module, current_face), current_face)
    if score >= 0.08:
        raise AssertionError(f"whole-mouth translation should be low motion, got {score}")


def keeps_mouth_position_shift_quiet(module) -> None:
    previous_face = make_face(module)
    current_face = make_face(module, mouth_shift_x=0.032, mouth_shift_y=0.018)
    state = module.SidecarState()
    state.previous_lip_points = lip_points(module, previous_face)
    state.previous_mouth_open_ratio = module.compute_mouth_open_ratio(previous_face)
    state.previous_mouth_center_y = module.compute_mouth_center_y(lip_points(module, previous_face))

    lip_motion_score = module.compute_motion_score(state, lip_points(module, current_face), current_face)
    mouth_open_ratio = module.compute_mouth_open_ratio(current_face)
    aperture_motion_score = module.compute_mouth_open_motion_score(state, mouth_open_ratio)
    center_motion_score = module.compute_mouth_center_motion_score(
        state,
        module.compute_mouth_center_y(lip_points(module, current_face)),
        lip_points(module, current_face),
    )
    score = module.compute_speaking_score(
        mouth_open_ratio=mouth_open_ratio,
        aperture_motion_score=aperture_motion_score,
        lip_motion_score=lip_motion_score,
        center_motion_score=center_motion_score,
        blendshape_score=0.5,
    )
    if score >= 0.12:
        raise AssertionError(
            f"mouth position shift should stay quiet, got score={score}, "
            f"lip_motion={lip_motion_score}, aperture_motion={aperture_motion_score}"
        )


def keeps_vertical_mouth_shift_with_aperture_jitter_quiet(module) -> None:
    previous_face = make_face(module)
    current_face = make_face(module, mouth_shift_y=0.035, open_delta=0.010)
    state = module.SidecarState()
    state.previous_lip_points = lip_points(module, previous_face)
    state.previous_mouth_open_ratio = module.compute_mouth_open_ratio(previous_face)
    state.previous_mouth_center_y = module.compute_mouth_center_y(lip_points(module, previous_face))

    lip_motion_score = module.compute_motion_score(state, lip_points(module, current_face), current_face)
    mouth_open_ratio = module.compute_mouth_open_ratio(current_face)
    aperture_motion_score = module.compute_mouth_open_motion_score(state, mouth_open_ratio)
    center_motion_score = module.compute_mouth_center_motion_score(
        state,
        module.compute_mouth_center_y(lip_points(module, current_face)),
        lip_points(module, current_face),
    )
    score = module.compute_speaking_score(
        mouth_open_ratio=mouth_open_ratio,
        aperture_motion_score=aperture_motion_score,
        lip_motion_score=lip_motion_score,
        center_motion_score=center_motion_score,
        blendshape_score=0.65,
    )
    if score >= 0.12:
        raise AssertionError(
            f"vertical mouth shift with aperture jitter should stay quiet, got score={score}, "
            f"aperture_motion={aperture_motion_score}, center_motion={center_motion_score}"
        )


def scores_mouth_shape_motion(module) -> None:
    previous_face = make_face(module)
    current_face = make_face(module, open_delta=0.026)
    state = module.SidecarState()
    state.previous_lip_points = lip_points(module, previous_face)

    score = module.compute_motion_score(state, lip_points(module, current_face), current_face)
    if score <= 0.25:
        raise AssertionError(f"mouth shape change should be visible motion, got {score}")


def keeps_static_open_mouth_quiet(module) -> None:
    score = module.compute_speaking_score(
        mouth_open_ratio=0.09,
        aperture_motion_score=0.0,
        lip_motion_score=0.0,
        center_motion_score=0.0,
        blendshape_score=0.75,
    )
    if score >= 0.12:
        raise AssertionError(f"static open mouth should stay quiet, got score {score}")


def scores_dynamic_open_mouth(module) -> None:
    score = module.compute_speaking_score(
        mouth_open_ratio=0.09,
        aperture_motion_score=0.55,
        lip_motion_score=0.55,
        center_motion_score=0.0,
        blendshape_score=0.25,
    )
    if score <= 0.25:
        raise AssertionError(f"dynamic mouth motion should score as speaking, got score {score}")


def make_face(module, shift_x=0.0, shift_y=0.0, mouth_shift_x=0.0, mouth_shift_y=0.0, open_delta=0.0):
    face = [Point(0.5 + shift_x, 0.5 + shift_y) for _ in range(500)]
    indices = list(dict.fromkeys(module.LIP_CONTOUR_INDICES))
    for ordinal, index in enumerate(indices):
        column = ordinal % 11
        row = ordinal // 11
        face[index] = Point(
            0.5 + (column - 5) * 0.014 + shift_x + mouth_shift_x,
            0.5 + (row - 2) * 0.008 + shift_y + mouth_shift_y,
        )
    face[module.LEFT_MOUTH] = Point(0.39 + shift_x + mouth_shift_x, 0.5 + shift_y + mouth_shift_y)
    face[module.RIGHT_MOUTH] = Point(0.61 + shift_x + mouth_shift_x, 0.5 + shift_y + mouth_shift_y)
    for index in module.UPPER_INNER:
        face[index] = Point(face[index].x, face[index].y - open_delta)
    for index in module.LOWER_INNER:
        face[index] = Point(face[index].x, face[index].y + open_delta)
    return face


def lip_points(module, face):
    return [(face[index].x, face[index].y) for index in module.LIP_CONTOUR_INDICES]


class Point:
    def __init__(self, x: float, y: float) -> None:
        self.x = x
        self.y = y


if __name__ == "__main__":
    main()
