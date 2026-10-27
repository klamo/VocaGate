package com.vocagate.demo.core;

public final class ScreenShieldState {
    public static final float DIM_BRIGHTNESS = 0.14f;

    private volatile boolean active;
    private float previousBrightness;

    public void activate(float currentBrightness) {
        if (active) {
            return;
        }
        previousBrightness = currentBrightness;
        active = true;
    }

    public float deactivate() {
        active = false;
        return previousBrightness;
    }

    public boolean isActive() {
        return active;
    }

    public boolean shouldRenderCameraPreview() {
        return !active;
    }

    public boolean shouldRenderOverlay() {
        return !active;
    }

    public boolean shouldUpdateStatusText() {
        return !active;
    }

    public boolean shouldUpdateShieldHud() {
        return active;
    }

    public float previousBrightness() {
        return previousBrightness;
    }

    public float activeBrightness() {
        return DIM_BRIGHTNESS;
    }
}
