package com.vocagate.demo.core;

import java.util.List;

public final class MouthFeatureExtractor {
    private static final float EPSILON = 0.0001f;
    private static final float NORMAL_OPEN_RATIO = 0.45f;

    private MouthFeatureExtractor() {
    }

    public static MouthFeatures fromLipContours(
            List<Point2> upperInner,
            List<Point2> lowerInner,
            List<Point2> allLipPoints,
            float previousOpenRatio,
            float motionRhythm
    ) {
        if (upperInner == null || lowerInner == null || upperInner.isEmpty() || lowerInner.isEmpty()) {
            return empty(previousOpenRatio);
        }

        Point2 upperCenter = centerPoint(upperInner);
        Point2 lowerCenter = centerPoint(lowerInner);
        float mouthOpen = Math.abs(lowerCenter.y - upperCenter.y);
        float mouthWidth = widthOf(allLipPoints);

        if (mouthWidth <= EPSILON) {
            return empty(previousOpenRatio);
        }

        float ratio = clamp01(mouthOpen / mouthWidth);
        float delta = Math.abs(ratio - previousOpenRatio);
        float normalizedOpen = clamp01(ratio / NORMAL_OPEN_RATIO);

        return new MouthFeatures(
                mouthOpen,
                mouthWidth,
                ratio,
                delta,
                clamp01(motionRhythm),
                normalizedOpen
        );
    }

    private static MouthFeatures empty(float previousOpenRatio) {
        return new MouthFeatures(0.0f, 0.0f, 0.0f, Math.abs(previousOpenRatio), 0.0f, 0.0f);
    }

    private static Point2 centerPoint(List<Point2> points) {
        return points.get(points.size() / 2);
    }

    private static float widthOf(List<Point2> points) {
        if (points == null || points.isEmpty()) {
            return 0.0f;
        }

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        for (Point2 point : points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
        }
        return Math.max(0.0f, maxX - minX);
    }

    public static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
