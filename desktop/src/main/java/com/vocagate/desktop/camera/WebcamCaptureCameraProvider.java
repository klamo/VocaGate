package com.vocagate.desktop.camera;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.vocagate.desktop.app.DesktopCameraDevice;
import com.vocagate.desktop.app.DesktopCameraPreviewSession;
import com.vocagate.desktop.app.DesktopCameraProvider;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

public final class WebcamCaptureCameraProvider implements DesktopCameraProvider {
    @Override
    public List<DesktopCameraDevice> listDevices() {
        List<DesktopCameraDevice> devices = new ArrayList<>();
        List<Webcam> webcams;
        try {
            webcams = Webcam.getWebcams();
        } catch (RuntimeException | LinkageError e) {
            return devices;
        }
        for (int i = 0; i < webcams.size(); i++) {
            Webcam webcam = webcams.get(i);
            devices.add(new DesktopCameraDevice(idFor(webcam, i), webcam.getName()));
        }
        return devices;
    }

    @Override
    public DesktopCameraPreviewSession openPreview(DesktopCameraDevice device) {
        List<Webcam> webcams;
        try {
            webcams = Webcam.getWebcams();
        } catch (RuntimeException | LinkageError e) {
            throw new IllegalStateException("webcam-capture backend unavailable", e);
        }
        Webcam selected = null;
        for (int i = 0; i < webcams.size(); i++) {
            Webcam webcam = webcams.get(i);
            if (idFor(webcam, i).equals(device.id())) {
                selected = webcam;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("camera not found: " + device.displayName());
        }
        return new WebcamPreviewSession(selected);
    }

    private String idFor(Webcam webcam, int index) {
        return index + ":" + webcam.getName();
    }

    private static final class WebcamPreviewSession implements DesktopCameraPreviewSession {
        private final Webcam webcam;
        private final WebcamPanel panel;

        private WebcamPreviewSession(Webcam webcam) {
            this.webcam = webcam;
            Dimension[] sizes = webcam.getViewSizes();
            if (sizes.length > 0) {
                webcam.setViewSize(sizes[sizes.length - 1]);
            }
            panel = new WebcamPanel(webcam);
            panel.setFPSDisplayed(true);
            panel.setImageSizeDisplayed(true);
            panel.setMirrored(true);
            panel.start();
        }

        @Override
        public JComponent component() {
            return panel;
        }

        @Override
        public BufferedImage snapshot() {
            try {
                return webcam.getImage();
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        public void close() {
            panel.stop();
            webcam.close();
        }
    }
}
