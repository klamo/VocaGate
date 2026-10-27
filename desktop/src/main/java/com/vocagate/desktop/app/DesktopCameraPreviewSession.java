package com.vocagate.desktop.app;

import com.vocagate.desktop.vision.DesktopRecognitionResult;

import java.awt.image.BufferedImage;

import javax.swing.JComponent;

public interface DesktopCameraPreviewSession extends AutoCloseable {
    JComponent component();

    default BufferedImage snapshot() {
        return null;
    }

    default void updateRecognitionResult(DesktopRecognitionResult result) {
    }

    @Override
    void close();
}
