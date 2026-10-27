package com.vocagate.desktop.app;

import java.util.Collections;
import java.util.List;

public interface DesktopCameraProvider {
    List<DesktopCameraDevice> listDevices();

    DesktopCameraPreviewSession openPreview(DesktopCameraDevice device);

    static DesktopCameraProvider unavailable(String reason) {
        return new DesktopCameraProvider() {
            @Override
            public List<DesktopCameraDevice> listDevices() {
                return Collections.emptyList();
            }

            @Override
            public DesktopCameraPreviewSession openPreview(DesktopCameraDevice device) {
                throw new IllegalStateException(reason);
            }
        };
    }
}
