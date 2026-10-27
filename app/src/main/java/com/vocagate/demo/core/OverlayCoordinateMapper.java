package com.vocagate.demo.core;

public final class OverlayCoordinateMapper {
    private OverlayCoordinateMapper() {
    }

    public static Point2 mapFillCenter(
            float imageX,
            float imageY,
            int imageWidth,
            int imageHeight,
            int viewWidth,
            int viewHeight,
            boolean mirror
    ) {
        int safeImageWidth = Math.max(1, imageWidth);
        int safeImageHeight = Math.max(1, imageHeight);
        int safeViewWidth = Math.max(1, viewWidth);
        int safeViewHeight = Math.max(1, viewHeight);

        float scale = Math.max(
                safeViewWidth / (float) safeImageWidth,
                safeViewHeight / (float) safeImageHeight
        );
        float scaledWidth = safeImageWidth * scale;
        float scaledHeight = safeImageHeight * scale;
        float offsetX = (safeViewWidth - scaledWidth) / 2.0f;
        float offsetY = (safeViewHeight - scaledHeight) / 2.0f;
        float mappedImageX = mirror ? safeImageWidth - imageX : imageX;
        return new Point2(
                offsetX + mappedImageX * scale,
                offsetY + imageY * scale
        );
    }

    public static int orientedWidth(int imageWidth, int imageHeight, int rotationDegrees) {
        return swapsAxes(rotationDegrees) ? imageHeight : imageWidth;
    }

    public static int orientedHeight(int imageWidth, int imageHeight, int rotationDegrees) {
        return swapsAxes(rotationDegrees) ? imageWidth : imageHeight;
    }

    private static boolean swapsAxes(int rotationDegrees) {
        int normalized = ((rotationDegrees % 360) + 360) % 360;
        return normalized == 90 || normalized == 270;
    }
}
