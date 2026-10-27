package com.vocagate.demo.core;

public final class MaiShaoScoreCalculator {
    private MaiShaoScoreCalculator() {
    }

    public static MaiShaoScore calculate(
            int sampleRate,
            long totalSamples,
            long openSamples,
            double fullEnergy,
            double gatedEnergy,
            int gateTransitions
    ) {
        if (sampleRate <= 0 || totalSamples <= 0) {
            return new MaiShaoScore(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        float durationSeconds = totalSamples / (float) sampleRate;
        float openPercent = 100.0f * openSamples / (float) totalSamples;
        float suppressionPercent = fullEnergy <= 0.0
                ? 0.0f
                : clampPercent((float) (100.0 * (1.0 - (gatedEnergy / fullEnergy))));
        float transitionsPerMinute = durationSeconds <= 0.0f
                ? 0.0f
                : gateTransitions * 60.0f / durationSeconds;

        float openBalanceScore = clampPercent(100.0f - Math.abs(openPercent - 35.0f) * 1.4f);
        float chatterScore = clampPercent(100.0f - transitionsPerMinute * 2.0f);
        float value = clampPercent(
                suppressionPercent * 0.55f
                        + openBalanceScore * 0.30f
                        + chatterScore * 0.15f
        );

        return new MaiShaoScore(
                value,
                suppressionPercent,
                openPercent,
                transitionsPerMinute,
                durationSeconds
        );
    }

    private static float clampPercent(float value) {
        return Math.max(0.0f, Math.min(100.0f, value));
    }
}
