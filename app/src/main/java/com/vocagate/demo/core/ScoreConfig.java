package com.vocagate.demo.core;

import com.vocagate.core.update.AlgorithmProfile;

public final class ScoreConfig {
    public final float rhythmWeight;
    public final float openWeight;
    public final float frontalWeight;
    public final float stabilityWeight;
    public final float occlusionPenaltyWeight;
    public final float longOpenPenaltyWeight;

    public ScoreConfig() {
        this(0.45f, 0.25f, 0.20f, 0.10f, 0.35f, 0.25f);
    }

    public ScoreConfig(
            float rhythmWeight,
            float openWeight,
            float frontalWeight,
            float stabilityWeight,
            float occlusionPenaltyWeight,
            float longOpenPenaltyWeight
    ) {
        this.rhythmWeight = rhythmWeight;
        this.openWeight = openWeight;
        this.frontalWeight = frontalWeight;
        this.stabilityWeight = stabilityWeight;
        this.occlusionPenaltyWeight = occlusionPenaltyWeight;
        this.longOpenPenaltyWeight = longOpenPenaltyWeight;
    }

    public static ScoreConfig fromProfile(AlgorithmProfile.ScoreWeights weights) {
        if (weights == null) {
            return new ScoreConfig();
        }
        return new ScoreConfig(
                weights.rhythmWeight(),
                weights.openWeight(),
                weights.frontalWeight(),
                weights.stabilityWeight(),
                weights.occlusionPenaltyWeight(),
                weights.longOpenPenaltyWeight()
        );
    }
}
