package com.vocagate.desktop.vision;

import java.lang.reflect.Method;

public final class DesktopMouthRegionProviderFactory {
    private static final String OPENCV_YUNET_PROVIDER =
            "com.vocagate.desktop.vision.OpenCvYuNetMouthRegionProvider";

    private DesktopMouthRegionProviderFactory() {
    }

    public static DesktopMouthRegionProvider createDefault() {
        DesktopMouthRegionProvider fallback = new CenteredMouthRegionProvider();
        DesktopMouthRegionProvider yuNetProvider = tryCreateProvider(OPENCV_YUNET_PROVIDER);
        if (yuNetProvider != null) {
            return new CascadingMouthRegionProvider(yuNetProvider, fallback);
        }
        return fallback;
    }

    private static DesktopMouthRegionProvider tryCreateProvider(String className) {
        try {
            Class<?> providerClass = Class.forName(className);
            Method createDefault = providerClass.getMethod("createDefault");
            Object provider = createDefault.invoke(null);
            if (provider instanceof DesktopMouthRegionProvider) {
                return (DesktopMouthRegionProvider) provider;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
        return null;
    }
}
