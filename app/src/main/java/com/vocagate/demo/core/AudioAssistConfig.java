package com.vocagate.demo.core;

public final class AudioAssistConfig {
    public static final int OFF = 0;
    public static final int CONSERVATIVE = 1;
    public static final int STANDARD = 2;
    public static final int AGGRESSIVE = 3;

    public final int mode;
    public final boolean enabled;
    public final float voiceThreshold;
    public final float riseThreshold;
    public final float visualFloor;
    public final float minMouthDelta;
    public final float minMouthRhythm;
    public final float maxBoost;
    public final long syncWindowMs;

    private AudioAssistConfig(
            int mode,
            boolean enabled,
            float voiceThreshold,
            float riseThreshold,
            float visualFloor,
            float minMouthDelta,
            float minMouthRhythm,
            float maxBoost,
            long syncWindowMs
    ) {
        this.mode = clampMode(mode);
        this.enabled = enabled;
        this.voiceThreshold = clamp01(voiceThreshold);
        this.riseThreshold = clamp01(riseThreshold);
        this.visualFloor = clamp01(visualFloor);
        this.minMouthDelta = Math.max(0.0f, minMouthDelta);
        this.minMouthRhythm = clamp01(minMouthRhythm);
        this.maxBoost = clamp01(maxBoost);
        this.syncWindowMs = Math.max(20L, syncWindowMs);
    }

    public static AudioAssistConfig forMode(int mode) {
        switch (clampMode(mode)) {
            case CONSERVATIVE:
                return conservative();
            case AGGRESSIVE:
                return aggressive();
            case OFF:
                return off();
            case STANDARD:
            default:
                return standard();
        }
    }

    public static AudioAssistConfig off() {
        return new AudioAssistConfig(OFF, false, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 180L);
    }

    public static AudioAssistConfig conservative() {
        return new AudioAssistConfig(CONSERVATIVE, true, 0.72f, 0.45f, 0.44f, 0.035f, 0.30f, 0.08f, 180L);
    }

    public static AudioAssistConfig standard() {
        return new AudioAssistConfig(STANDARD, true, 0.62f, 0.36f, 0.40f, 0.026f, 0.22f, 0.12f, 220L);
    }

    public static AudioAssistConfig aggressive() {
        return new AudioAssistConfig(AGGRESSIVE, true, 0.54f, 0.28f, 0.34f, 0.018f, 0.16f, 0.16f, 260L);
    }

    public static String modeName(int mode) {
        switch (clampMode(mode)) {
            case OFF:
                return "关闭";
            case CONSERVATIVE:
                return "保守";
            case AGGRESSIVE:
                return "激进";
            case STANDARD:
            default:
                return "标准";
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

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
