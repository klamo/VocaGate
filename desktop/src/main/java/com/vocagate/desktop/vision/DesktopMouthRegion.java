package com.vocagate.desktop.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DesktopMouthRegion {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final boolean trackingValid;
    private final String label;
    private final List<DesktopPoint> landmarks;

    private DesktopMouthRegion(
            int x,
            int y,
            int width,
            int height,
            boolean trackingValid,
            String label,
            List<DesktopPoint> landmarks
    ) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.trackingValid = trackingValid;
        this.label = label;
        this.landmarks = immutableLandmarks(landmarks);
    }

    public static DesktopMouthRegion tracked(int x, int y, int width, int height, String label) {
        return trackedWithLandmarks(x, y, width, height, label, Collections.emptyList());
    }

    public static DesktopMouthRegion trackedWithLandmarks(
            int x,
            int y,
            int width,
            int height,
            String label,
            List<DesktopPoint> landmarks
    ) {
        return new DesktopMouthRegion(
                Math.max(0, x),
                Math.max(0, y),
                Math.max(0, width),
                Math.max(0, height),
                true,
                label == null || label.trim().isEmpty() ? "mouth" : label,
                landmarks
        );
    }

    public static DesktopMouthRegion missing(String label) {
        return new DesktopMouthRegion(
                0,
                0,
                0,
                0,
                false,
                label == null ? "no-mouth-region" : label,
                Collections.emptyList()
        );
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean trackingValid() {
        return trackingValid && width > 0 && height > 0;
    }

    public String label() {
        return label;
    }

    public List<DesktopPoint> landmarks() {
        return landmarks;
    }

    private static List<DesktopPoint> immutableLandmarks(List<DesktopPoint> landmarks) {
        if (landmarks == null || landmarks.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(landmarks));
    }
}
