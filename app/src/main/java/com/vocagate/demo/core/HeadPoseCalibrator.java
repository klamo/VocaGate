package com.vocagate.demo.core;

public final class HeadPoseCalibrator {
    private static final int WARMUP_SAMPLES = 24;
    private static final float ADAPT_ALPHA = 0.02f;
    private static final float ADAPT_LIMIT_DEGREES = 18.0f;

    private int samples;
    private float baselineYaw;
    private float baselineRoll;

    public void update(float yawDegrees, float rollDegrees) {
        if (samples < WARMUP_SAMPLES) {
            samples++;
            baselineYaw += (yawDegrees - baselineYaw) / samples;
            baselineRoll += (rollDegrees - baselineRoll) / samples;
            return;
        }

        float relativeYaw = yawDegrees - baselineYaw;
        float relativeRoll = rollDegrees - baselineRoll;
        if (Math.abs(relativeYaw) <= ADAPT_LIMIT_DEGREES && Math.abs(relativeRoll) <= ADAPT_LIMIT_DEGREES) {
            baselineYaw += relativeYaw * ADAPT_ALPHA;
            baselineRoll += relativeRoll * ADAPT_ALPHA;
        }
    }

    public void reset() {
        samples = 0;
        baselineYaw = 0.0f;
        baselineRoll = 0.0f;
    }

    public float relativeYaw(float yawDegrees) {
        return yawDegrees - baselineYaw;
    }

    public float relativeRoll(float rollDegrees) {
        return rollDegrees - baselineRoll;
    }

    public float frontalScore(float yawDegrees, float rollDegrees) {
        float yaw = Math.abs(relativeYaw(yawDegrees));
        float roll = Math.abs(relativeRoll(rollDegrees));
        return MouthFeatureExtractor.clamp01(1.0f - ((yaw + roll) / 70.0f));
    }
}
