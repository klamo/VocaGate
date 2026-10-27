package com.vocagate.demo.core;

public final class SpeakScoreEstimator {
    private final ScoreConfig config;

    public SpeakScoreEstimator(ScoreConfig config) {
        this.config = config;
    }

    public float estimate(
            MouthFeatures features,
            float frontalFaceScore,
            float trackStability,
            float occlusionPenalty,
            float longOpenPenalty
    ) {
        if (features == null) {
            return 0.0f;
        }

        float score =
                config.rhythmWeight * features.mouthMotionRhythm
                        + config.openWeight * features.normalizedMouthOpen
                        + config.frontalWeight * MouthFeatureExtractor.clamp01(frontalFaceScore)
                        + config.stabilityWeight * MouthFeatureExtractor.clamp01(trackStability)
                        - config.occlusionPenaltyWeight * MouthFeatureExtractor.clamp01(occlusionPenalty)
                        - config.longOpenPenaltyWeight * MouthFeatureExtractor.clamp01(longOpenPenalty);

        return MouthFeatureExtractor.clamp01(score);
    }
}
