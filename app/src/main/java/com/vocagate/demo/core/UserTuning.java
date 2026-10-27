package com.vocagate.demo.core;

import com.vocagate.core.GateConfig;
import com.vocagate.core.update.AlgorithmProfile;

public final class UserTuning {
    public static final int DEFAULT_EARLY_PRESS = 50;
    public static final int DEFAULT_FIRST_WORD_PROTECTION_MODE = FirstWordProtection.STANDARD;
    public static final int DEFAULT_AUDIO_ASSIST_MODE = AudioAssistConfig.STANDARD;

    public final int sensitivity;
    public final int earlyPress;
    public final int responseSpeed;
    public final int hold;
    public final int firstWordProtectionMode;
    public final int audioAssistMode;

    public UserTuning(int sensitivity, int responseSpeed, int hold) {
        this(sensitivity, responseSpeed, hold, DEFAULT_EARLY_PRESS, DEFAULT_FIRST_WORD_PROTECTION_MODE, DEFAULT_AUDIO_ASSIST_MODE);
    }

    public UserTuning(int sensitivity, int responseSpeed, int hold, int firstWordProtectionMode) {
        this(sensitivity, responseSpeed, hold, DEFAULT_EARLY_PRESS, firstWordProtectionMode, DEFAULT_AUDIO_ASSIST_MODE);
    }

    public UserTuning(
            int sensitivity,
            int responseSpeed,
            int hold,
            int earlyPress,
            int firstWordProtectionMode
    ) {
        this(sensitivity, responseSpeed, hold, earlyPress, firstWordProtectionMode, DEFAULT_AUDIO_ASSIST_MODE);
    }

    public UserTuning(
            int sensitivity,
            int responseSpeed,
            int hold,
            int earlyPress,
            int firstWordProtectionMode,
            int audioAssistMode
    ) {
        this.sensitivity = clampPercent(sensitivity);
        this.earlyPress = clampPercent(earlyPress);
        this.responseSpeed = clampPercent(responseSpeed);
        this.hold = clampPercent(hold);
        this.firstWordProtectionMode = FirstWordProtection.clampMode(firstWordProtectionMode);
        this.audioAssistMode = AudioAssistConfig.clampMode(audioAssistMode);
    }

    public GateConfig toGateConfig() {
        float openThreshold = 0.78f - (0.0032f * sensitivity);
        float preOpenGap = map(earlyPress, 0, 100, 0.02f, 0.18f);
        float preOpenThreshold = clamp(openThreshold - preOpenGap, 0.18f, openThreshold);
        float closeThreshold = clamp(openThreshold - 0.24f, 0.18f, 0.55f);

        int preOpenFrames = Math.round(map(responseSpeed, 0, 100, 5.0f, 1.0f));
        int closeFrames = Math.round(map(responseSpeed, 0, 100, 10.0f, 5.0f));
        long minOpenMs = Math.round(map(responseSpeed, 0, 100, 700.0f, 300.0f));
        long holdMs = Math.round(map(hold, 0, 100, 300.0f, 1200.0f));
        long cooldownMs = Math.round(map(responseSpeed, 0, 100, 350.0f, 150.0f));

        return new GateConfig(
                openThreshold,
                preOpenThreshold,
                closeThreshold,
                preOpenFrames,
                closeFrames,
                minOpenMs,
                holdMs,
                cooldownMs
        );
    }

    public GateConfig toGateConfig(AlgorithmProfile profile) {
        if (profile == null) {
            return toGateConfig();
        }
        return toGateConfig(profile.toGateConfig());
    }

    public GateConfig toGateConfig(GateConfig baseline) {
        if (baseline == null) {
            return toGateConfig();
        }
        if (isDefaultTuning()) {
            return baseline;
        }

        int sensitivityOffset = sensitivity - 50;
        int earlyPressOffset = earlyPress - DEFAULT_EARLY_PRESS;
        int responseOffset = responseSpeed - 50;
        int holdOffset = hold - 50;

        float openThreshold = clamp(
                baseline.openThreshold - (0.0032f * sensitivityOffset),
                0.18f,
                0.90f
        );
        float basePreOpenGap = Math.max(0.0f, baseline.openThreshold - baseline.preOpenThreshold);
        float preOpenGap = clamp(basePreOpenGap + (0.0016f * earlyPressOffset), 0.02f, 0.24f);
        float closeThreshold = clamp(
                baseline.closeThreshold + (openThreshold - baseline.openThreshold),
                0.10f,
                Math.min(openThreshold, 0.70f)
        );
        float preOpenThreshold = clamp(openThreshold - preOpenGap, closeThreshold, openThreshold);

        int preOpenFrames = clampInt(
                Math.round(baseline.preOpenRequiredFrames - (responseOffset / 25.0f)),
                1,
                10
        );
        int closeFrames = clampInt(
                Math.round(baseline.closeRequiredFrames - (responseOffset / 25.0f)),
                2,
                20
        );
        long minOpenMs = clampLong(
                baseline.minOpenMs - Math.round(responseOffset * 4.0f),
                120L,
                1200L
        );
        long holdMs = clampLong(
                baseline.holdMs + Math.round(holdOffset * 9.0f),
                120L,
                1800L
        );
        long cooldownMs = clampLong(
                baseline.cooldownMs - Math.round(responseOffset * 2.0f),
                60L,
                800L
        );

        return new GateConfig(
                openThreshold,
                preOpenThreshold,
                closeThreshold,
                preOpenFrames,
                closeFrames,
                minOpenMs,
                holdMs,
                cooldownMs
        );
    }

    public static UserTuning defaults() {
        return new UserTuning(
                50,
                50,
                50,
                DEFAULT_EARLY_PRESS,
                DEFAULT_FIRST_WORD_PROTECTION_MODE,
                DEFAULT_AUDIO_ASSIST_MODE
        );
    }

    private boolean isDefaultTuning() {
        return sensitivity == 50
                && earlyPress == DEFAULT_EARLY_PRESS
                && responseSpeed == 50
                && hold == 50;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float map(int value, int inMin, int inMax, float outMin, float outMax) {
        float t = (value - inMin) / (float) (inMax - inMin);
        return outMin + t * (outMax - outMin);
    }
}
