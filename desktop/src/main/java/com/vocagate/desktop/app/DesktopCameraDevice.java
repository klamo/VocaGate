package com.vocagate.desktop.app;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DesktopCameraDevice {
    private final String id;
    private final String displayName;

    public DesktopCameraDevice(String id, String displayName) {
        this.id = requireText(id, "id");
        this.displayName = requireText(displayName, "displayName");
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static Optional<DesktopCameraDevice> selectPreferred(
            List<DesktopCameraDevice> devices,
            String rememberedId
    ) {
        if (devices == null || devices.isEmpty()) {
            return Optional.empty();
        }
        if (rememberedId != null && !rememberedId.trim().isEmpty()) {
            for (DesktopCameraDevice device : devices) {
                if (device.id.equals(rememberedId.trim())) {
                    return Optional.of(device);
                }
            }
        }
        return Optional.of(devices.get(0));
    }

    private static String requireText(String value, String label) {
        String trimmed = Objects.requireNonNull(value, label).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " is empty");
        }
        return trimmed;
    }
}
