package com.vocagate.desktop.app;

import com.vocagate.desktop.core.KeyStrokeSpec;

public final class DesktopKeyOptions {
    public static final String CUSTOM_OPTION = "自定义...";

    private static final String[] OPTIONS = new String[]{
            DesktopDefaults.DEFAULT_KEY_SPEC,
            "SPACE",
            "F13",
            "F14",
            "F15",
            "F16",
            CUSTOM_OPTION
    };

    private DesktopKeyOptions() {
    }

    public static String[] keyOptions() {
        return OPTIONS.clone();
    }

    public static boolean isCustomOption(String value) {
        return CUSTOM_OPTION.equals(value);
    }

    public static KeyStrokeSpec resolveKeySpec(String selectedOption, String customText) {
        if (isCustomOption(selectedOption)) {
            if (customText == null || customText.trim().isEmpty()) {
                throw new IllegalArgumentException("custom key is empty");
            }
            return KeyStrokeSpec.parse(customText);
        }
        return KeyStrokeSpec.parse(selectedOption);
    }
}
