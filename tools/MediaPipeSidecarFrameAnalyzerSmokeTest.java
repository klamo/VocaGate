import com.vocagate.core.GateState;
import com.vocagate.desktop.mediapipe.MediaPipeFaceLandmarkerFrameAnalyzer;
import com.vocagate.desktop.vision.DesktopRecognitionResult;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class MediaPipeSidecarFrameAnalyzerSmokeTest {
    public static void main(String[] args) throws Exception {
        testWarmUpConsumesReadyLineBeforeFirstFrame();
        testAnalyzerKeepsExistingRecognitionProtocol();
        System.out.println("MediaPipe sidecar frame analyzer smoke passed");
    }

    private static void testWarmUpConsumesReadyLineBeforeFirstFrame() throws Exception {
        Path fakeSidecar = Files.createTempFile("vocagate-fake-mediapipe-ready-sidecar", ".sh");
        Files.write(fakeSidecar, Arrays.asList(
                "#!/bin/sh",
                "printf '%s\\n' '{\"type\":\"ready\",\"model\":\"fake\"}'",
                "while IFS= read -r line; do",
                "  printf '%s\\n' '{\"type\":\"recognition\",\"tracking_valid\":true,\"score\":0.86,\"motion\":0.44,\"fps\":29.0,\"status\":\"MediaPipe speaking\",\"roi\":{\"x\":20,\"y\":30,\"width\":100,\"height\":44,\"label\":\"MediaPipe lips\"},\"landmarks\":[[20,40],[70,56],[120,40]]}'",
                "done"
        ), StandardCharsets.UTF_8);

        BufferedImage frame = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        try (MediaPipeFaceLandmarkerFrameAnalyzer analyzer = new MediaPipeFaceLandmarkerFrameAnalyzer(
                Arrays.asList("/bin/sh", fakeSidecar.toString())
        )) {
            analyzer.warmUp();
            DesktopRecognitionResult result = analyzer.analyze(frame, 1000L);

            if (!"MediaPipe speaking".equals(result.statusLabel())) {
                throw new AssertionError("expected recognition after warm-up but got " + result.statusLabel());
            }
        }
    }

    private static void testAnalyzerKeepsExistingRecognitionProtocol() throws Exception {
        Path fakeSidecar = Files.createTempFile("vocagate-fake-mediapipe-sidecar", ".sh");
        Files.write(fakeSidecar, Arrays.asList(
                "#!/bin/sh",
                "printf '%s\\n' '{\"type\":\"ready\",\"model\":\"fake\"}'",
                "while IFS= read -r line; do",
                "  printf '%s\\n' '{\"type\":\"recognition\",\"tracking_valid\":true,\"score\":0.86,\"motion\":0.44,\"fps\":29.0,\"status\":\"MediaPipe speaking\",\"roi\":{\"x\":20,\"y\":30,\"width\":100,\"height\":44,\"label\":\"MediaPipe lips\"},\"landmarks\":[[20,40],[70,56],[120,40]]}'",
                "done"
        ), StandardCharsets.UTF_8);

        BufferedImage frame = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        try (MediaPipeFaceLandmarkerFrameAnalyzer analyzer = new MediaPipeFaceLandmarkerFrameAnalyzer(
                Arrays.asList("/bin/sh", fakeSidecar.toString())
        )) {
            analyzer.analyze(frame, 1000L);
            analyzer.analyze(frame, 1100L);
            DesktopRecognitionResult result = analyzer.analyze(frame, 1200L);

            if (result.state() != GateState.OPEN) {
                throw new AssertionError("expected OPEN after repeated high sidecar scores but got " + result.state());
            }
            if (!result.trackingValid()) {
                throw new AssertionError("expected valid tracking");
            }
            if (!"MediaPipe speaking".equals(result.statusLabel())) {
                throw new AssertionError("unexpected status: " + result.statusLabel());
            }
            if (result.mouthRegion().landmarks().size() != 3) {
                throw new AssertionError("expected sidecar landmarks");
            }
        }
    }
}
