package com.vocagate.desktop.vision;

import java.awt.image.BufferedImage;

public final class CenteredMouthRegionProvider implements DesktopMouthRegionProvider {
    @Override
    public DesktopMouthRegion detect(BufferedImage image) {
        if (image == null || image.getWidth() < 16 || image.getHeight() < 16) {
            return DesktopMouthRegion.missing("无画面");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int faceX = width / 4;
        int faceY = Math.max(0, height / 8);
        int faceWidth = Math.max(1, width / 2);
        int faceHeight = Math.max(1, Math.min(height - faceY, (int) (height * 0.74f)));
        int mouthX = faceX + Math.round(faceWidth * 0.18f);
        int mouthY = faceY + Math.round(faceHeight * 0.58f);
        int mouthWidth = Math.max(1, Math.round(faceWidth * 0.64f));
        int mouthHeight = Math.max(1, Math.round(faceHeight * 0.20f));

        if (mouthX + mouthWidth > width) {
            mouthWidth = width - mouthX;
        }
        if (mouthY + mouthHeight > height) {
            mouthHeight = height - mouthY;
        }
        if (mouthWidth <= 0 || mouthHeight <= 0) {
            return DesktopMouthRegion.missing("中心 fallback 无效");
        }
        return DesktopMouthRegion.tracked(mouthX, mouthY, mouthWidth, mouthHeight, "中心嘴部 fallback");
    }
}
