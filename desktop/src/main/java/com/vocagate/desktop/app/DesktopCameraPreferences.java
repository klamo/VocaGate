package com.vocagate.desktop.app;

import java.util.Optional;
import java.util.prefs.Preferences;

public final class DesktopCameraPreferences {
    private static final String KEY_INPUT_SOURCE = "inputSource";
    private static final String KEY_CAMERA_ID = "cameraId";

    private final Preferences preferences;

    public DesktopCameraPreferences() {
        this(Preferences.userNodeForPackage(DesktopCameraPreferences.class));
    }

    public DesktopCameraPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    public DesktopInputSource loadInputSource() {
        String raw = preferences.get(KEY_INPUT_SOURCE, DesktopInputSource.PHONE.name());
        try {
            return DesktopInputSource.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return DesktopInputSource.PHONE;
        }
    }

    public void saveInputSource(DesktopInputSource source) {
        preferences.put(KEY_INPUT_SOURCE, (source == null ? DesktopInputSource.PHONE : source).name());
    }

    public Optional<String> loadCameraId() {
        String raw = preferences.get(KEY_CAMERA_ID, "").trim();
        return raw.isEmpty() ? Optional.empty() : Optional.of(raw);
    }

    public void saveCameraId(String cameraId) {
        if (cameraId == null || cameraId.trim().isEmpty()) {
            preferences.remove(KEY_CAMERA_ID);
            return;
        }
        preferences.put(KEY_CAMERA_ID, cameraId.trim());
    }
}
