import com.vocagate.desktop.mediapipe.MediaPipeSidecarFrameResult;
import com.vocagate.desktop.mediapipe.MediaPipeSidecarProtocol;
import com.vocagate.desktop.vision.DesktopPoint;

import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaPipeSidecarProtocolSmokeTest {
    public static void main(String[] args) {
        parsesRecognitionJsonLine();
        ignoresSidecarEvents();
        serializesScaledRgbFrame();
        System.out.println("MediaPipe sidecar protocol smoke passed");
    }

    private static void parsesRecognitionJsonLine() {
        String line = "{"
                + "\"type\":\"recognition\","
                + "\"tracking_valid\":true,"
                + "\"score\":0.73,"
                + "\"motion\":0.31,"
                + "\"fps\":28.5,"
                + "\"status\":\"MediaPipe mouth\","
                + "\"roi\":{\"x\":100,\"y\":150,\"width\":220,\"height\":90,\"label\":\"MediaPipe lips\"},"
                + "\"landmarks\":[[110.5,160.25],[180.0,172.0],[250.0,161.5]]"
                + "}";

        Optional<MediaPipeSidecarFrameResult> parsed = MediaPipeSidecarProtocol.parseRecognitionLine(line);
        if (!parsed.isPresent()) {
            throw new AssertionError("expected recognition result");
        }
        MediaPipeSidecarFrameResult result = parsed.get();
        assertClose("score", 0.73f, result.score());
        assertClose("motion", 0.31f, result.motionScore());
        assertClose("fps", 28.5f, result.fps());
        if (!result.trackingValid()) {
            throw new AssertionError("expected tracking valid");
        }
        if (!"MediaPipe mouth".equals(result.statusLabel())) {
            throw new AssertionError("unexpected status: " + result.statusLabel());
        }
        if (!"MediaPipe lips".equals(result.mouthRegion().label())) {
            throw new AssertionError("unexpected roi label: " + result.mouthRegion().label());
        }
        if (result.mouthRegion().x() != 100 || result.mouthRegion().y() != 150
                || result.mouthRegion().width() != 220 || result.mouthRegion().height() != 90) {
            throw new AssertionError("unexpected roi geometry");
        }
        List<DesktopPoint> landmarks = result.mouthRegion().landmarks();
        if (landmarks.size() != 3) {
            throw new AssertionError("expected 3 landmarks but got " + landmarks.size());
        }
        assertClose("landmark x", 110.5f, landmarks.get(0).x());
        assertClose("landmark y", 160.25f, landmarks.get(0).y());
    }

    private static void ignoresSidecarEvents() {
        Optional<MediaPipeSidecarFrameResult> parsed = MediaPipeSidecarProtocol.parseRecognitionLine(
                "{\"type\":\"ready\",\"model\":\"face_landmarker.task\"}"
        );
        if (parsed.isPresent()) {
            throw new AssertionError("expected ready event to be ignored");
        }
    }

    private static void serializesScaledRgbFrame() {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff0000);
        image.setRGB(1, 0, 0x00ff00);
        image.setRGB(2, 0, 0x0000ff);
        image.setRGB(3, 0, 0xffffff);
        image.setRGB(0, 1, 0x111111);
        image.setRGB(1, 1, 0x222222);
        image.setRGB(2, 1, 0x333333);
        image.setRGB(3, 1, 0x444444);

        String request = MediaPipeSidecarProtocol.frameRequestLine(image, 1234L, 2);
        assertContains(request, "\"type\":\"frame\"");
        assertContains(request, "\"timestamp_ms\":1234");
        assertContains(request, "\"source_width\":4");
        assertContains(request, "\"source_height\":2");
        assertContains(request, "\"width\":2");
        assertContains(request, "\"height\":1");

        Matcher matcher = Pattern.compile("\"rgb\":\"([^\"]+)\"").matcher(request);
        if (!matcher.find()) {
            throw new AssertionError("serialized frame has no rgb payload: " + request);
        }
        byte[] rgb = Base64.getDecoder().decode(matcher.group(1));
        if (rgb.length != 2 * 1 * 3) {
            throw new AssertionError("expected 6 rgb bytes but got " + rgb.length);
        }
    }

    private static void assertContains(String text, String expected) {
        if (!text.contains(expected)) {
            throw new AssertionError("expected to find " + expected + " in " + text);
        }
    }

    private static void assertClose(String label, float expected, float actual) {
        if (Math.abs(expected - actual) > 0.001f) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }
}
