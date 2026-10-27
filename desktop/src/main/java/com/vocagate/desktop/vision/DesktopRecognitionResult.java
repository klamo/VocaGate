package com.vocagate.desktop.vision;

import com.vocagate.core.GateState;

public final class DesktopRecognitionResult {
    private final float score;
    private final float motionScore;
    private final float fps;
    private final boolean likelySpeaking;
    private final GateState state;
    private final boolean trackingValid;
    private final DesktopMouthRegion mouthRegion;
    private final String mouthRegionLabel;
    private final String statusLabel;

    public DesktopRecognitionResult(
            float score,
            float motionScore,
            float fps,
            boolean likelySpeaking,
            GateState state,
            boolean trackingValid,
            DesktopMouthRegion mouthRegion,
            String mouthRegionLabel,
            String statusLabel
    ) {
        this.score = clamp(score);
        this.motionScore = clamp(motionScore);
        this.fps = Math.max(0.0f, fps);
        this.likelySpeaking = likelySpeaking;
        this.state = state;
        this.trackingValid = trackingValid;
        this.mouthRegion = mouthRegion == null ? DesktopMouthRegion.missing(mouthRegionLabel) : mouthRegion;
        this.mouthRegionLabel = mouthRegionLabel == null ? "-" : mouthRegionLabel;
        this.statusLabel = statusLabel;
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

    public boolean isLikelySpeaking() {
        return likelySpeaking;
    }

    public GateState state() {
        return state;
    }

    public boolean trackingValid() {
        return trackingValid;
    }

    public DesktopMouthRegion mouthRegion() {
        return mouthRegion;
    }

    public String mouthRegionLabel() {
        return mouthRegionLabel;
    }

    public String statusLabel() {
        return statusLabel;
    }

    public static DesktopRecognitionResult noFrame() {
        DesktopMouthRegion region = DesktopMouthRegion.missing("无画面");
        return new DesktopRecognitionResult(0.0f, 0.0f, 0.0f, false, GateState.SUSPEND, false, region, "无画面", "等待画面");
    }

    private static float clamp(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }
}
