package com.vocagate.core.update;

import com.vocagate.core.GateConfig;

public final class AlgorithmProfile {
    private final int schemaVersion;
    private final String profileId;
    private final String engine;
    private final String version;
    private final String minAppVersion;
    private final GateConfig gateConfig;
    private final ScoreWeights score;
    private final FirstWordConfig firstWord;

    public AlgorithmProfile(
            int schemaVersion,
            String profileId,
            String engine,
            String version,
            String minAppVersion,
            GateConfig gateConfig,
            ScoreWeights score,
            FirstWordConfig firstWord
    ) {
        if (schemaVersion <= 0) {
            throw new UpdateProtocolException("profile schema_version must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.profileId = ManifestAsset.requireText(profileId, "profile id");
        this.engine = ManifestAsset.requireText(engine, "profile engine");
        this.version = ManifestAsset.requireText(version, "profile version");
        this.minAppVersion = ManifestAsset.requireText(minAppVersion, "profile min_app_version");
        if (gateConfig == null) {
            throw new UpdateProtocolException("profile gate config is required");
        }
        this.gateConfig = gateConfig;
        this.score = score == null ? ScoreWeights.defaults() : score;
        this.firstWord = firstWord == null ? FirstWordConfig.defaults() : firstWord;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String profileId() {
        return profileId;
    }

    public String engine() {
        return engine;
    }

    public String version() {
        return version;
    }

    public String minAppVersion() {
        return minAppVersion;
    }

    public GateConfig toGateConfig() {
        return gateConfig;
    }

    public ScoreWeights score() {
        return score;
    }

    public FirstWordConfig firstWord() {
        return firstWord;
    }

    public static final class ScoreWeights {
        private final float rhythmWeight;
        private final float openWeight;
        private final float frontalWeight;
        private final float stabilityWeight;
        private final float occlusionPenaltyWeight;
        private final float longOpenPenaltyWeight;

        public ScoreWeights(
                float rhythmWeight,
                float openWeight,
                float frontalWeight,
                float stabilityWeight,
                float occlusionPenaltyWeight,
                float longOpenPenaltyWeight
        ) {
            this.rhythmWeight = clampNonNegative(rhythmWeight);
            this.openWeight = clampNonNegative(openWeight);
            this.frontalWeight = clampNonNegative(frontalWeight);
            this.stabilityWeight = clampNonNegative(stabilityWeight);
            this.occlusionPenaltyWeight = clampNonNegative(occlusionPenaltyWeight);
            this.longOpenPenaltyWeight = clampNonNegative(longOpenPenaltyWeight);
        }

        public static ScoreWeights defaults() {
            return new ScoreWeights(0.45f, 0.25f, 0.20f, 0.10f, 0.35f, 0.25f);
        }

        public float rhythmWeight() {
            return rhythmWeight;
        }

        public float openWeight() {
            return openWeight;
        }

        public float frontalWeight() {
            return frontalWeight;
        }

        public float stabilityWeight() {
            return stabilityWeight;
        }

        public float occlusionPenaltyWeight() {
            return occlusionPenaltyWeight;
        }

        public float longOpenPenaltyWeight() {
            return longOpenPenaltyWeight;
        }
    }

    public static final class FirstWordConfig {
        private final boolean enabled;
        private final float riseThreshold;
        private final long holdMs;

        public FirstWordConfig(boolean enabled, float riseThreshold, long holdMs) {
            this.enabled = enabled;
            this.riseThreshold = clampNonNegative(riseThreshold);
            this.holdMs = Math.max(0L, holdMs);
        }

        public static FirstWordConfig defaults() {
            return new FirstWordConfig(true, 0.055f, 180L);
        }

        public boolean enabled() {
            return enabled;
        }

        public float riseThreshold() {
            return riseThreshold;
        }

        public long holdMs() {
            return holdMs;
        }
    }

    private static float clampNonNegative(float value) {
        if (Float.isNaN(value) || value < 0.0f) {
            return 0.0f;
        }
        return value;
    }
}
