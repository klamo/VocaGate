package com.vocagate.desktop.app;

public enum DesktopInputSource {
    PHONE("手机 VocaGate"),
    LOCAL_CAMERA("本机摄像头");

    private final String displayName;

    DesktopInputSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static DesktopInputSource fromDisplayName(String displayName) {
        for (DesktopInputSource source : values()) {
            if (source.displayName.equals(displayName)) {
                return source;
            }
        }
        return PHONE;
    }
}
