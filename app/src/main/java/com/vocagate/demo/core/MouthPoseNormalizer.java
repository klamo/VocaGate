package com.vocagate.demo.core;

import java.util.ArrayList;
import java.util.List;

public final class MouthPoseNormalizer {
    private MouthPoseNormalizer() {
    }

    public static List<Point2> normalize(List<Point2> points, float relativeRollDegrees) {
        return rotateAroundCenter(points, -relativeRollDegrees);
    }

    public static List<Point2> normalize(List<Point2> points, Point2 center, float relativeRollDegrees) {
        return rotateAroundCenter(points, center, -relativeRollDegrees);
    }

    public static List<Point2> rotateAroundCenter(List<Point2> points, float degrees) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }
        return rotateAroundCenter(points, centerOf(points), degrees);
    }

    public static List<Point2> rotateAroundCenter(List<Point2> points, Point2 center, float degrees) {
        List<Point2> result = new ArrayList<>();
        if (points == null || points.isEmpty()) {
            return result;
        }

        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        for (Point2 point : points) {
            float x = point.x - center.x;
            float y = point.y - center.y;
            float rotatedX = (float) (x * cos - y * sin) + center.x;
            float rotatedY = (float) (x * sin + y * cos) + center.y;
            result.add(new Point2(rotatedX, rotatedY));
        }
        return result;
    }

    public static Point2 centerOf(List<Point2> points) {
        if (points == null || points.isEmpty()) {
            return new Point2(0.0f, 0.0f);
        }
        float sumX = 0.0f;
        float sumY = 0.0f;
        for (Point2 point : points) {
            sumX += point.x;
            sumY += point.y;
        }
        return new Point2(sumX / points.size(), sumY / points.size());
    }
}
