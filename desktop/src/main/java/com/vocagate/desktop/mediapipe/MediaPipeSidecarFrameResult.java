package com.vocagate.desktop.mediapipe;

import com.vocagate.desktop.vision.DesktopMouthRegion;

public final class MediaPipeSidecarFrameResult {
    private final float score;
    private final float motionScore;
    private final float fps;
    private final boolean trackingValid;
    private final DesktopMouthRegion mouthRegion;
    private final String statusLabel;

    public MediaPipeSidecarFrameResult(
            float score,
            float motionScore,
            float fps,
            boolean trackingValid,
            DesktopMouthRegion mouthRegion,
            String statusLabel
    ) {
        this.score = clamp01(score);
        this.motionScore = clamp01(motionScore);
        this.fps = Math.max(0.0f, fps);
        this.trackingValid = trackingValid;
        this.mouthRegion = mouthRegion == null ? DesktopMouthRegion.missing(statusLabel) : mouthRegion;
        this.statusLabel = statusLabel == null || statusLabel.trim().isEmpty() ? "-" : statusLabel;
    }

    public float score() {
        return score;
    }

    public float motionScore() {
        return motionScore;
    }

    public float fps() {
        return fps;
    }

    public boolean trackingValid() {
        return trackingValid;
    }

    public DesktopMouthRegion mouthRegion() {
        return mouthRegion;
    }

    public String statusLabel() {
        return statusLabel;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }
}
