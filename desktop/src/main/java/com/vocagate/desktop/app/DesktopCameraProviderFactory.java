package com.vocagate.desktop.app;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DesktopCameraProviderFactory {
    private static final String OPENCV_PROVIDER = "com.vocagate.desktop.camera.OpenCvVideoCaptureCameraProvider";
    private static final String WEBCAM_CAPTURE_PROVIDER = "com.vocagate.desktop.camera.WebcamCaptureCameraProvider";

    private DesktopCameraProviderFactory() {
    }

    public static DesktopCameraProvider createDefault() {
        return createFirstAvailable(defaultProviderClassNamesForPlatform(
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", "")
        ));
    }

    public static List<String> defaultProviderClassNamesForPlatform(String osName, String osArch) {
        if (isMacArm(osName, osArch)) {
            return Collections.singletonList(OPENCV_PROVIDER);
        }
        return Arrays.asList(OPENCV_PROVIDER, WEBCAM_CAPTURE_PROVIDER);
    }

    public static DesktopCameraProvider createFirstAvailable(List<String> providerClassNames) {
        List<DesktopCameraProvider> providers = new ArrayList<>();
        for (String className : providerClassNames == null ? Collections.<String>emptyList() : providerClassNames) {
            DesktopCameraProvider provider = createProvider(className);
            if (provider != null) {
                providers.add(provider);
            }
        }
        if (providers.isEmpty()) {
            return DesktopCameraProvider.unavailable("desktop camera provider unavailable");
        }
        return new FirstAvailableCameraProvider(providers);
    }

    private static DesktopCameraProvider createProvider(String className) {
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        try {
            Class<?> type = Class.forName(className.trim());
            Constructor<?> constructor = type.getDeclaredConstructor();
            Object instance = constructor.newInstance();
            if (instance instanceof DesktopCameraProvider) {
                return (DesktopCameraProvider) instance;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
        return null;
    }

    private static boolean isMacArm(String osName, String osArch) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase();
        String normalizedArch = osArch == null ? "" : osArch.toLowerCase();
        return normalizedOs.contains("mac")
                && (normalizedArch.contains("aarch64") || normalizedArch.contains("arm64"));
    }

    private static final class FirstAvailableCameraProvider implements DesktopCameraProvider {
        private final List<DesktopCameraProvider> providers;

        private FirstAvailableCameraProvider(List<DesktopCameraProvider> providers) {
            this.providers = providers;
        }

        @Override
        public List<DesktopCameraDevice> listDevices() {
            RuntimeException lastError = null;
            for (DesktopCameraProvider provider : providers) {
                try {
                    List<DesktopCameraDevice> devices = provider.listDevices();
                    if (devices != null && !devices.isEmpty()) {
                        return devices;
                    }
                } catch (RuntimeException | LinkageError e) {
                    lastError = new RuntimeException(e);
                }
            }
            if (lastError != null && providers.size() == 1) {
                throw lastError;
            }
            return Collections.emptyList();
        }

        @Override
        public DesktopCameraPreviewSession openPreview(DesktopCameraDevice device) {
            RuntimeException lastError = null;
            for (DesktopCameraProvider provider : providers) {
                try {
                    return provider.openPreview(device);
                } catch (RuntimeException | LinkageError e) {
                    lastError = new RuntimeException(e);
                }
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new IllegalStateException("No camera provider available");
        }
    }
}
