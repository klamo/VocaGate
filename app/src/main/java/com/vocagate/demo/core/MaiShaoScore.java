package com.vocagate.demo.core;

public final class MaiShaoScore {
    public final float value;
    public final float suppressionPercent;
    public final float openPercent;
    public final float transitionsPerMinute;
    public final float durationSeconds;

    public MaiShaoScore(
            float value,
            float suppressionPercent,
            float openPercent,
            float transitionsPerMinute,
            float durationSeconds
    ) {
        this.value = value;
        this.suppressionPercent = suppressionPercent;
        this.openPercent = openPercent;
        this.transitionsPerMinute = transitionsPerMinute;
        this.durationSeconds = durationSeconds;
    }
}
