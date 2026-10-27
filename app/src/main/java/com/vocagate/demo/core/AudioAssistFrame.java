package com.vocagate.demo.core;

public final class AudioAssistFrame {
    public static final AudioAssistFrame SILENCE = new AudioAssistFrame(0L, -120.0f, -120.0f, 0.0f, 0.0f);

    public final long timestampMs;
    public final float rmsDb;
    public final float noiseFloorDb;
    public final float voiceConfidence;
    public final float riseScore;

    public AudioAssistFrame(
            long timestampMs,
            float rmsDb,
            float noiseFloorDb,
            float voiceConfidence,
            float riseScore
    ) {
        this.timestampMs = Math.max(0L, timestampMs);
        this.rmsDb = sanitizeDb(rmsDb);
        this.noiseFloorDb = sanitizeDb(noiseFloorDb);
        this.voiceConfidence = clamp01(voiceConfidence);
        this.riseScore = clamp01(riseScore);
    }

    public boolean isRecent(long nowMs, long maxAgeMs) {
        if (timestampMs <= 0L) {
            return false;
        }
        long ageMs = Math.abs(nowMs - timestampMs);
        return ageMs <= maxAgeMs;
    }

    private static float sanitizeDb(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return -120.0f;
        }
        return Math.max(-120.0f, Math.min(12.0f, value));
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
