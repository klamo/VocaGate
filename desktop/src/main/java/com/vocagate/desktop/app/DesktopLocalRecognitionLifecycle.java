package com.vocagate.desktop.app;

public final class DesktopLocalRecognitionLifecycle {
    private boolean desktopStarted;
    private boolean cameraPreviewStarted;

    public boolean onDesktopStarted() {
        desktopStarted = true;
        return shouldRunRecognition();
    }

    public boolean onDesktopStopped() {
        desktopStarted = false;
        return shouldRunRecognition();
    }

    public boolean onCameraPreviewStarted() {
        cameraPreviewStarted = true;
        return shouldRunRecognition();
    }

    public boolean onCameraPreviewStopped() {
        cameraPreviewStarted = false;
        return shouldRunRecognition();
    }

    public boolean shouldRunRecognition() {
        return desktopStarted && cameraPreviewStarted;
    }
}
