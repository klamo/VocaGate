import com.vocagate.desktop.mediapipe.DesktopSidecarLaunchSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DesktopSidecarLaunchSpecSmokeTest {
    public static void main(String[] args) throws Exception {
        usesPackagedSidecarBeforeWorkspacePython();
        usesExplicitDevelopmentOverrides();
        System.out.println("Desktop sidecar launch spec smoke tests passed");
    }

    private static void usesPackagedSidecarBeforeWorkspacePython() throws Exception {
        Path appHome = Files.createTempDirectory("vocagate-installed-app");
        Path executable = appHome.resolve("sidecar/vocagate-mediapipe");
        Path model = appHome.resolve("sidecar/models/face_landmarker.task");
        Files.createDirectories(executable.getParent());
        Files.createDirectories(model.getParent());
        Files.writeString(executable, "placeholder");
        Files.writeString(model, "model");

        DesktopSidecarLaunchSpec spec = DesktopSidecarLaunchSpec.resolve(
                appHome,
                Map.of(),
                appHome.resolve("workspace"),
                "Mac OS X"
        );

        assertEquals(List.of(executable.toString(), "--model", model.toString()), spec.command());
        assertTrue(spec.isPackaged(), "installed sidecar should be selected first");
    }

    private static void usesExplicitDevelopmentOverrides() throws Exception {
        Path root = Files.createTempDirectory("vocagate-sidecar-override");
        Path script = root.resolve("custom-sidecar.py");
        Path model = root.resolve("custom-model.task");
        Files.writeString(script, "placeholder");
        Files.writeString(model, "model");

        DesktopSidecarLaunchSpec spec = DesktopSidecarLaunchSpec.resolve(
                root.resolve("missing-app"),
                Map.of(
                        "VOCAGATE_PYTHON", "/custom/python",
                        "VOCAGATE_MEDIAPIPE_SIDECAR", script.toString(),
                        "VOCAGATE_MEDIAPIPE_MODEL", model.toString()
                ),
                root,
                "Mac OS X"
        );

        assertEquals(List.of("/custom/python", script.toString(), "--model", model.toString()), spec.command());
        assertTrue(!spec.isPackaged(), "development override is not packaged");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
