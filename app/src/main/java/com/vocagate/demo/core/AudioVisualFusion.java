package com.vocagate.demo.core;

import com.vocagate.core.GateConfig;

public final class AudioVisualFusion {
    private volatile AudioAssistConfig config;

    public AudioVisualFusion(AudioAssistConfig config) {
        this.config = config == null ? AudioAssistConfig.off() : config;
    }

    public void setConfig(AudioAssistConfig config) {
        this.config = config == null ? AudioAssistConfig.off() : config;
    }

    public AudioAssistConfig getConfig() {
        return config;
    }

    public Result fuse(
            float visualScore,
            MouthFeatures features,
            AudioAssistFrame audioFrame,
            GateConfig gateConfig,
            boolean trackingValid,
            long nowMs
    ) {
        float baseScore = clamp01(visualScore);
        AudioAssistConfig localConfig = config;
        if (!canAssist(baseScore, features, audioFrame, gateConfig, localConfig, trackingValid, nowMs)) {
            return new Result(baseScore, 0.0f, false, audioConfidence(audioFrame), 0.0f);
        }

        float motionScore = mouthMotionScore(features, localConfig);
        float voiceScore = thresholdProgress(audioFrame.voiceConfidence, localConfig.voiceThreshold);
        float riseScore = thresholdProgress(audioFrame.riseScore, localConfig.riseThreshold);
        float syncScore = clamp01((motionScore * 0.60f) + (riseScore * 0.40f));
        float boost = localConfig.maxBoost * voiceScore * syncScore;
        float fusedScore = clamp01(baseScore + boost);

        if (fusedScore < gateConfig.preOpenThreshold) {
            float bridgeDistance = gateConfig.preOpenThreshold - baseScore;
            if (bridgeDistance <= localConfig.maxBoost * 0.95f && syncScore >= 0.40f && voiceScore >= 0.55f) {
                fusedScore = gateConfig.preOpenThreshold;
                boost = fusedScore - baseScore;
            }
        }

        boolean assisted = boost > 0.0001f && fusedScore > baseScore;
        return new Result(fusedScore, Math.max(0.0f, boost), assisted, audioFrame.voiceConfidence, syncScore);
    }

    private boolean canAssist(
            float visualScore,
            MouthFeatures features,
            AudioAssistFrame audioFrame,
            GateConfig gateConfig,
            AudioAssistConfig config,
            boolean trackingValid,
            long nowMs
    ) {
        if (config == null || !config.enabled || gateConfig == null || !trackingValid) {
            return false;
        }
        if (features == null || audioFrame == null || !audioFrame.isRecent(nowMs, config.syncWindowMs)) {
            return false;
        }
        if (visualScore < config.visualFloor || visualScore >= gateConfig.openThreshold) {
            return false;
        }
        if (audioFrame.voiceConfidence < config.voiceThreshold || audioFrame.riseScore < config.riseThreshold) {
            return false;
        }
        return mouthMotionScore(features, config) > 0.0f;
    }

    private float mouthMotionScore(MouthFeatures features, AudioAssistConfig config) {
        float deltaScore = thresholdProgress(features.deltaMouth, config.minMouthDelta);
        float rhythmScore = thresholdProgress(features.mouthMotionRhythm, config.minMouthRhythm);
        return clamp01(Math.max(deltaScore, rhythmScore));
    }

    private float audioConfidence(AudioAssistFrame audioFrame) {
        return audioFrame == null ? 0.0f : audioFrame.voiceConfidence;
    }

    private static float thresholdProgress(float value, float threshold) {
        if (threshold <= 0.0f) {
            return clamp01(value);
        }
        return clamp01((value - threshold) / Math.max(0.0001f, 1.0f - threshold));
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static final class Result {
        public final float score;
        public final float boost;
        public final boolean audioAssisted;
        public final float audioConfidence;
        public final float syncScore;

        private Result(
                float score,
                float boost,
                boolean audioAssisted,
                float audioConfidence,
                float syncScore
        ) {
            this.score = clamp01(score);
            this.boost = Math.max(0.0f, boost);
            this.audioAssisted = audioAssisted;
            this.audioConfidence = clamp01(audioConfidence);
            this.syncScore = clamp01(syncScore);
        }
    }
}
