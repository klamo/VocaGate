package com.vocagate.desktop.vision;

public final class DesktopPoint {
    private final float x;
    private final float y;

    public DesktopPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }
}
