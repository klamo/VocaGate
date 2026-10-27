package com.vocagate.core;

public final class GateConfig {
    public final float openThreshold;
    public final float preOpenThreshold;
    public final float closeThreshold;
    public final int preOpenRequiredFrames;
    public final int closeRequiredFrames;
    public final long minOpenMs;
    public final long holdMs;
    public final long cooldownMs;

    public GateConfig() {
        this(0.62f, 0.52f, 0.38f, 3, 8, 500L, 700L, 250L);
    }

    public GateConfig(
            float openThreshold,
            float closeThreshold,
            int preOpenRequiredFrames,
            int closeRequiredFrames,
            long minOpenMs,
            long holdMs,
            long cooldownMs
    ) {
        this(
                openThreshold,
                Math.max(closeThreshold, openThreshold - 0.10f),
                closeThreshold,
                preOpenRequiredFrames,
                closeRequiredFrames,
                minOpenMs,
                holdMs,
                cooldownMs
        );
    }

    public GateConfig(
            float openThreshold,
            float preOpenThreshold,
            float closeThreshold,
            int preOpenRequiredFrames,
            int closeRequiredFrames,
            long minOpenMs,
            long holdMs,
            long cooldownMs
    ) {
        this.openThreshold = openThreshold;
        this.preOpenThreshold = Math.min(openThreshold, Math.max(closeThreshold, preOpenThreshold));
        this.closeThreshold = closeThreshold;
        this.preOpenRequiredFrames = preOpenRequiredFrames;
        this.closeRequiredFrames = closeRequiredFrames;
        this.minOpenMs = minOpenMs;
        this.holdMs = holdMs;
        this.cooldownMs = cooldownMs;
    }
}
