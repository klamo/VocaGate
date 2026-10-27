package com.vocagate.demo.core;

public final class FirstWordProtection {
    public static final int OFF = 0;
    public static final int CONSERVATIVE = 1;
    public static final int STANDARD = 2;
    public static final int AGGRESSIVE = 3;

    private static final float MIN_FACE_SCORE = 0.65f;
    private static final float MIN_MOUTH_RATIO = 0.055f;

    private int mode;
    private float previousRatio = -1.0f;
    private long activeUntilMs = -1L;

    public FirstWordProtection(int mode) {
        this.mode = clampMode(mode);
    }

    public void setMode(int mode) {
        this.mode = clampMode(mode);
        reset();
    }

    public Result update(MouthFeatures features, boolean trackingValid, float frontalFaceScore, long nowMs) {
        if (mode == OFF || features == null || !trackingValid || frontalFaceScore < MIN_FACE_SCORE) {
            remember(features);
            activeUntilMs = -1L;
            return new Result(false, false, "off");
        }

        boolean triggered = isMouthRise(features);
        if (triggered) {
            activeUntilMs = nowMs + holdMsForMode(mode);
        }
        remember(features);

        boolean active = activeUntilMs >= nowMs;
        return new Result(active, triggered, active ? "onset" : "idle");
    }

    public void reset() {
        previousRatio = -1.0f;
        activeUntilMs = -1L;
    }

    public int mode() {
        return mode;
    }

    private boolean isMouthRise(MouthFeatures features) {
        if (previousRatio < 0.0f) {
            return false;
        }
        float rise = features.mouthOpenRatio - previousRatio;
        return features.mouthOpenRatio >= MIN_MOUTH_RATIO && rise >= riseThresholdForMode(mode);
    }

    private void remember(MouthFeatures features) {
        if (features != null) {
            previousRatio = features.mouthOpenRatio;
        }
    }

    public static String modeName(int mode) {
        switch (clampMode(mode)) {
            case CONSERVATIVE:
                return "保守";
            case STANDARD:
                return "标准";
            case AGGRESSIVE:
                return "激进";
            case OFF:
            default:
                return "关闭";
        }
    }

    public static long holdMsForMode(int mode) {
        switch (clampMode(mode)) {
            case CONSERVATIVE:
                return 120L;
            case STANDARD:
                return 180L;
            case AGGRESSIVE:
                return 250L;
            case OFF:
            default:
                return 0L;
        }
    }

    public static float riseThresholdForMode(int mode) {
        switch (clampMode(mode)) {
            case CONSERVATIVE:
                return 0.080f;
            case STANDARD:
                return 0.055f;
            case AGGRESSIVE:
                return 0.035f;
            case OFF:
            default:
                return Float.MAX_VALUE;
        }
    }

    public static int clampMode(int mode) {
        if (mode < OFF) {
            return OFF;
        }
        if (mode > AGGRESSIVE) {
            return AGGRESSIVE;
        }
        return mode;
    }

    public static final class Result {
        public final boolean active;
        public final boolean justTriggered;
        public final String sourceLabel;

        public Result(boolean active, boolean justTriggered, String sourceLabel) {
            this.active = active;
            this.justTriggered = justTriggered;
            this.sourceLabel = sourceLabel;
        }
    }
}
