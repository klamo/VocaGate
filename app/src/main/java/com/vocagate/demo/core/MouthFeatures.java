package com.vocagate.demo.core;

public final class MouthFeatures {
    public final float mouthOpen;
    public final float mouthWidth;
    public final float mouthOpenRatio;
    public final float deltaMouth;
    public final float mouthMotionRhythm;
    public final float normalizedMouthOpen;

    public MouthFeatures(
            float mouthOpen,
            float mouthWidth,
            float mouthOpenRatio,
            float deltaMouth,
            float mouthMotionRhythm,
            float normalizedMouthOpen
    ) {
        this.mouthOpen = mouthOpen;
        this.mouthWidth = mouthWidth;
        this.mouthOpenRatio = mouthOpenRatio;
        this.deltaMouth = deltaMouth;
        this.mouthMotionRhythm = mouthMotionRhythm;
        this.normalizedMouthOpen = normalizedMouthOpen;
    }
}
