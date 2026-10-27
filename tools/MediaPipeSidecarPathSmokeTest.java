package com.vocagate.desktop.mediapipe;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MediaPipeSidecarPathSmokeTest {
    public static void main(String[] args) throws Exception {
        Path appHome = Files.createTempDirectory("vocagate-app-home");
        Path unrelatedWorkingDirectory = Files.createTempDirectory("vocagate-unrelated-cwd");
        Path appVenvPython = appHome.resolve(".venv-mediapipe").resolve("bin").resolve("python");
        Files.createDirectories(appVenvPython.getParent());
        Files.write(appVenvPython, new byte[]{});

        String resolved = MediaPipeFaceLandmarkerFrameAnalyzer.defaultPythonCommandFor(
                "",
                appHome,
                unrelatedWorkingDirectory,
                "Mac OS X"
        );
        if (!appVenvPython.toString().equals(resolved)) {
            throw new AssertionError("expected app-home venv python but got " + resolved);
        }

        Path cacheRoot = Files.createTempDirectory("vocagate-cache-root");
        java.util.Map<String, String> env = MediaPipeFaceLandmarkerFrameAnalyzer.sidecarEnvironmentFor(
                new java.util.HashMap<>(),
                cacheRoot
        );
        String matplotlibDir = env.get("MPLCONFIGDIR");
        if (matplotlibDir == null || !matplotlibDir.contains("matplotlib")) {
            throw new AssertionError("expected stable MPLCONFIGDIR under cache root but got " + matplotlibDir);
        }
        if (!Files.isDirectory(Path.of(matplotlibDir))) {
            throw new AssertionError("expected MPLCONFIGDIR to be created");
        }
        if (!"Agg".equals(env.get("MPLBACKEND"))) {
            throw new AssertionError("expected non-GUI matplotlib backend");
        }
        if (!env.containsKey("PYTHONPYCACHEPREFIX")) {
            throw new AssertionError("expected stable python pycache prefix");
        }

        System.out.println("MediaPipe sidecar path smoke passed");
    }
}
