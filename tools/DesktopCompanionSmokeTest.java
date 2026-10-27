import com.vocagate.desktop.app.DesktopDefaults;
import com.vocagate.desktop.app.DesktopCameraDevice;
import com.vocagate.desktop.app.DesktopCameraPreferences;
import com.vocagate.desktop.app.DesktopCameraProvider;
import com.vocagate.desktop.app.DesktopCameraProviderFactory;
import com.vocagate.desktop.app.DesktopCameraPreviewSession;
import com.vocagate.desktop.app.DesktopInputSource;
import com.vocagate.desktop.app.DesktopKeyOptions;
import com.vocagate.desktop.app.DesktopLocalGateBridge;
import com.vocagate.desktop.core.GateHoldController;
import com.vocagate.desktop.core.GateMessage;
import com.vocagate.desktop.core.GateProtocolParser;
import com.vocagate.desktop.core.KeyActuator;
import com.vocagate.desktop.core.KeyStrokeSpec;
import com.vocagate.desktop.server.ConnectionChannel;
import com.vocagate.desktop.server.ConnectionMode;
import com.vocagate.desktop.server.ConnectionPolicy;
import com.vocagate.desktop.server.DesktopGateServer;
import com.vocagate.desktop.vision.CascadingMouthRegionProvider;
import com.vocagate.desktop.vision.DesktopMotionScoreAnalyzer;
import com.vocagate.desktop.vision.DesktopMouthRegion;
import com.vocagate.desktop.vision.DesktopMouthRegionProvider;
import com.vocagate.desktop.vision.DesktopMouthRegionProviderFactory;
import com.vocagate.desktop.vision.DesktopRecognitionResult;
import com.vocagate.desktop.vision.DesktopPoint;
import com.vocagate.core.GateState;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.awt.image.BufferedImage;

public final class DesktopCompanionSmokeTest {
    public static void main(String[] args) throws Exception {
        testProtocolParser();
        testProtocolParserPreOpenAndPairing();
        testConnectionPolicyPrefersUsbInAutoMode();
        testKeyStrokeParsing();
        testDefaultKeyIsWindowsFriendly();
        testCustomKeyOptionResolution();
        testDesktopInputSourceLabels();
        testCameraPreferenceRoundTrip();
        testCameraDeviceFallsBackWhenRememberedDeviceIsMissing();
        testCameraProviderDefaultsSkipWebcamCaptureOnMacArm();
        testCameraProviderFactoryFallsBackWhenFirstProviderFails();
        testCameraProviderFactoryFallsBackWhenFirstProviderHasNativeLinkageError();
        testMotionAnalyzerKeepsStaticFramesBlocked();
        testMotionAnalyzerRaisesScoreWhenLowerCenterChanges();
        testMotionAnalyzerReturnsMouthRegionForPreviewOverlay();
        testMouthRegionCanCarryLandmarkPoints();
        testMotionAnalyzerSamplesInjectedMouthRegionOnly();
        testCascadingProviderFallsBackWhenPrimaryMisses();
        testDefaultMouthRegionProviderFactoryReturnsUsableProvider();
        testControllerPressesAndReleasesOnce();
        testControllerReleasesOnTimeout();
        testControllerReleasesOnDisconnect();
        testServerAcceptsStateLines();
        testServerReportsKeyActionFailuresSeparately();
        testLocalCameraBridgePressesAndReleasesController();
        System.out.println("Desktop companion smoke tests passed");
    }

    private static void testProtocolParser() {
        GateMessage open = GateProtocolParser.parseLine("STATE OPEN 1200 0.82");
        assertTrue(open.isState(), "STATE OPEN should be state message");
        assertTrue(open.isOpen(), "STATE OPEN should be open");
        assertTrue(open.androidTimeMs() == 1200L, "android time should parse");
        assertNear(0.82f, open.score(), 0.001f, "score should parse");

        GateMessage blocked = GateProtocolParser.parseLine("STATE BLOCKED 1300 0.21");
        assertTrue(blocked.isState(), "STATE BLOCKED should be state message");
        assertTrue(!blocked.isOpen(), "STATE BLOCKED should be blocked");

        GateMessage ping = GateProtocolParser.parseLine("PING 1400 OPEN");
        assertTrue(ping.isHeartbeat(), "PING should be heartbeat message");
        assertTrue(ping.isOpen(), "PING OPEN should preserve latest state");
    }

    private static void testProtocolParserPreOpenAndPairing() {
        GateMessage preOpen = GateProtocolParser.parseLine("STATE PRE_OPEN 1500 0.66");
        assertTrue(preOpen.isState(), "PRE_OPEN should be state message");
        assertTrue(preOpen.isOpen(), "PRE_OPEN should be treated as key-down state");
        assertTrue(preOpen.gateLabel().equals("PRE_OPEN"), "PRE_OPEN label should be preserved");

        GateMessage pair = GateProtocolParser.parseLine("PAIR 493812");
        assertTrue(pair.isPairing(), "PAIR should be pairing message");
        assertTrue(pair.pairingCode().equals("493812"), "pairing code should parse");
    }

    private static void testConnectionPolicyPrefersUsbInAutoMode() {
        assertTrue(
                ConnectionPolicy.shouldAccept(ConnectionMode.AUTO, null, ConnectionChannel.WIFI),
                "auto should accept initial wifi"
        );
        assertTrue(
                ConnectionPolicy.shouldReplaceActive(ConnectionMode.AUTO, ConnectionChannel.WIFI, ConnectionChannel.USB),
                "auto should let usb replace wifi"
        );
        assertTrue(
                !ConnectionPolicy.shouldReplaceActive(ConnectionMode.AUTO, ConnectionChannel.USB, ConnectionChannel.WIFI),
                "auto should not let wifi replace usb"
        );
        assertTrue(
                !ConnectionPolicy.shouldAccept(ConnectionMode.USB, null, ConnectionChannel.WIFI),
                "usb mode should reject wifi"
        );
        assertTrue(
                !ConnectionPolicy.shouldAccept(ConnectionMode.WIFI, null, ConnectionChannel.USB),
                "wifi mode should reject usb"
        );
    }

    private static void testKeyStrokeParsing() {
        KeyStrokeSpec single = KeyStrokeSpec.parse("F13");
        assertTrue(single.displayName().equals("F13"), "single key display name");
        assertTrue(single.keyCodes().length == 1, "single key code count");

        KeyStrokeSpec combo = KeyStrokeSpec.parse("CTRL+SHIFT+M");
        assertTrue(combo.displayName().equals("Ctrl+Shift+M"), "combo display name");
        assertTrue(combo.keyCodes().length == 3, "combo key code count");
    }

    private static void testDefaultKeyIsWindowsFriendly() {
        assertTrue(
                DesktopDefaults.DEFAULT_KEY_SPEC.equals("CTRL+SHIFT+M"),
                "default key should avoid F13 because Windows Java Robot can reject extended function keys"
        );
    }

    private static void testCustomKeyOptionResolution() {
        String[] options = DesktopKeyOptions.keyOptions();
        assertTrue(options[0].equals(DesktopDefaults.DEFAULT_KEY_SPEC), "default key should be first");
        assertTrue(options[options.length - 1].equals(DesktopKeyOptions.CUSTOM_OPTION), "custom key option should be last");

        KeyStrokeSpec preset = DesktopKeyOptions.resolveKeySpec("SPACE", "ALT+V");
        assertTrue(preset.displayName().equals("Space"), "preset key should ignore custom text");

        KeyStrokeSpec custom = DesktopKeyOptions.resolveKeySpec(DesktopKeyOptions.CUSTOM_OPTION, "ALT+V");
        assertTrue(custom.displayName().equals("Alt+V"), "custom combo should parse");
        assertTrue(custom.keyCodes().length == 2, "custom combo key code count");
    }

    private static void testDesktopInputSourceLabels() {
        assertTrue(DesktopInputSource.PHONE.displayName().equals("手机 VocaGate"), "phone source label");
        assertTrue(DesktopInputSource.LOCAL_CAMERA.displayName().equals("本机摄像头"), "local camera source label");
        assertTrue(DesktopInputSource.fromDisplayName("本机摄像头") == DesktopInputSource.LOCAL_CAMERA, "source by label");
        assertTrue(DesktopInputSource.fromDisplayName("missing") == DesktopInputSource.PHONE, "unknown source should default to phone");
    }

    private static void testCameraPreferenceRoundTrip() throws Exception {
        Preferences node = Preferences.userRoot().node("/com/vocagate/tests/" + System.nanoTime());
        DesktopCameraPreferences preferences = new DesktopCameraPreferences(node);
        try {
            assertTrue(preferences.loadInputSource() == DesktopInputSource.PHONE, "default source should be phone");
            assertTrue(!preferences.loadCameraId().isPresent(), "default camera id should be empty");

            preferences.saveInputSource(DesktopInputSource.LOCAL_CAMERA);
            preferences.saveCameraId("camera-2");

            assertTrue(preferences.loadInputSource() == DesktopInputSource.LOCAL_CAMERA, "source should round trip");
            assertTrue(preferences.loadCameraId().orElse("").equals("camera-2"), "camera id should round trip");
        } finally {
            node.removeNode();
        }
    }

    private static void testCameraDeviceFallsBackWhenRememberedDeviceIsMissing() {
        List<DesktopCameraDevice> devices = Arrays.asList(
                new DesktopCameraDevice("camera-1", "Built-in Camera"),
                new DesktopCameraDevice("camera-2", "USB Camera")
        );

        Optional<DesktopCameraDevice> remembered = DesktopCameraDevice.selectPreferred(devices, "camera-2");
        assertTrue(remembered.isPresent(), "remembered camera should be found");
        assertTrue(remembered.get().id().equals("camera-2"), "remembered camera id");

        Optional<DesktopCameraDevice> fallback = DesktopCameraDevice.selectPreferred(devices, "missing");
        assertTrue(fallback.isPresent(), "fallback camera should be found");
        assertTrue(fallback.get().id().equals("camera-1"), "fallback should use first device");

        assertTrue(!DesktopCameraDevice.selectPreferred(new ArrayList<>(), "camera-1").isPresent(), "empty list should have no camera");
    }

    private static void testCameraProviderDefaultsSkipWebcamCaptureOnMacArm() {
        List<String> providers = DesktopCameraProviderFactory.defaultProviderClassNamesForPlatform(
                "Mac OS X",
                "aarch64"
        );

        assertTrue(
                providers.contains("com.vocagate.desktop.camera.OpenCvVideoCaptureCameraProvider"),
                "mac arm defaults should keep OpenCV camera provider"
        );
        assertTrue(
                !providers.contains("com.vocagate.desktop.camera.WebcamCaptureCameraProvider"),
                "mac arm defaults should skip webcam-capture because BridJ is x86_64-only in this dependency"
        );
    }

    private static void testCameraProviderFactoryFallsBackWhenFirstProviderFails() {
        DesktopCameraProvider provider = DesktopCameraProviderFactory.createFirstAvailable(Arrays.asList(
                ThrowingCameraProvider.class.getName(),
                OneDeviceCameraProvider.class.getName()
        ));

        List<DesktopCameraDevice> devices = provider.listDevices();

        assertTrue(devices.size() == 1, "factory should fall back to second provider when first listDevices fails");
        assertTrue(devices.get(0).id().equals("fake:0"), "fallback provider device id");
    }

    private static void testCameraProviderFactoryFallsBackWhenFirstProviderHasNativeLinkageError() {
        DesktopCameraProvider provider = DesktopCameraProviderFactory.createFirstAvailable(Arrays.asList(
                LinkageErrorCameraProvider.class.getName(),
                OneDeviceCameraProvider.class.getName()
        ));

        List<DesktopCameraDevice> devices = provider.listDevices();

        assertTrue(devices.size() == 1, "factory should fall back when first camera provider has native linkage error");
        assertTrue(devices.get(0).id().equals("fake:0"), "linkage fallback provider device id");
    }

    private static void testMotionAnalyzerKeepsStaticFramesBlocked() {
        DesktopMotionScoreAnalyzer analyzer = new DesktopMotionScoreAnalyzer();
        BufferedImage frame = syntheticFrame(80);

        analyzer.analyze(frame, 0L);
        DesktopRecognitionResult result = analyzer.analyze(frame, 100L);

        assertTrue(result.score() < 0.08f, "static frames should keep score low");
        assertTrue(!result.isLikelySpeaking(), "static frames should stay blocked");
    }

    private static void testMotionAnalyzerRaisesScoreWhenLowerCenterChanges() {
        DesktopMotionScoreAnalyzer analyzer = new DesktopMotionScoreAnalyzer();

        analyzer.analyze(syntheticFrame(40), 0L);
        DesktopRecognitionResult result = analyzer.analyze(syntheticFrame(220), 100L);

        assertTrue(result.score() > 0.35f, "changed lower-center region should raise speaking score");
        assertTrue(result.isLikelySpeaking(), "changed lower-center region should look like speaking in the prototype");
        assertTrue(result.state() == GateState.PRE_OPEN, "desktop prototype should expose shared gate state");
    }

    private static void testMotionAnalyzerReturnsMouthRegionForPreviewOverlay() {
        DesktopMouthRegionProvider provider = image -> DesktopMouthRegion.tracked(8, 10, 34, 18, "overlay-mouth");
        DesktopMotionScoreAnalyzer analyzer = new DesktopMotionScoreAnalyzer(provider);

        analyzer.analyze(syntheticFrameWithPatches(60, 60), 0L);
        DesktopRecognitionResult result = analyzer.analyze(syntheticFrameWithPatches(220, 60), 100L);

        assertTrue(result.mouthRegion().trackingValid(), "recognition result should expose the tracked mouth region");
        assertTrue(result.mouthRegion().x() == 8, "overlay mouth x should round trip");
        assertTrue(result.mouthRegion().y() == 10, "overlay mouth y should round trip");
        assertTrue(result.mouthRegion().width() == 34, "overlay mouth width should round trip");
        assertTrue(result.mouthRegion().height() == 18, "overlay mouth height should round trip");
    }

    private static void testMouthRegionCanCarryLandmarkPoints() {
        List<DesktopPoint> points = Arrays.asList(
                new DesktopPoint(10.0f, 20.0f),
                new DesktopPoint(30.0f, 24.0f),
                new DesktopPoint(28.0f, 36.0f),
                new DesktopPoint(12.0f, 34.0f)
        );

        DesktopMouthRegion region = DesktopMouthRegion.trackedWithLandmarks(10, 20, 20, 16, "landmark-mouth", points);

        assertTrue(region.landmarks().size() == 4, "mouth region should preserve landmark points for overlay");
        assertNear(30.0f, region.landmarks().get(1).x(), 0.001f, "landmark x should round trip");
        assertNear(36.0f, region.landmarks().get(2).y(), 0.001f, "landmark y should round trip");
    }

    private static void testMotionAnalyzerSamplesInjectedMouthRegionOnly() {
        DesktopMouthRegionProvider provider = image -> DesktopMouthRegion.tracked(8, 8, 32, 16, "fake-mouth");
        DesktopMotionScoreAnalyzer analyzer = new DesktopMotionScoreAnalyzer(provider);

        analyzer.analyze(syntheticFrameWithPatches(60, 60), 0L);
        DesktopRecognitionResult outsideChange = analyzer.analyze(syntheticFrameWithPatches(60, 220), 100L);
        DesktopRecognitionResult insideChange = analyzer.analyze(syntheticFrameWithPatches(220, 220), 200L);

        assertTrue(outsideChange.score() < 0.08f, "changes outside provider mouth region should be ignored");
        assertTrue(insideChange.score() > 0.35f, "changes inside provider mouth region should drive score");
        assertTrue(insideChange.trackingValid(), "provider-tracked ROI should be marked tracking valid");
        assertTrue(insideChange.mouthRegionLabel().equals("fake-mouth"), "provider label should be surfaced");
    }

    private static void testCascadingProviderFallsBackWhenPrimaryMisses() {
        DesktopMouthRegionProvider primary = image -> DesktopMouthRegion.missing("primary-miss");
        DesktopMouthRegionProvider fallback = image -> DesktopMouthRegion.tracked(4, 6, 20, 10, "fallback-mouth");
        DesktopMouthRegionProvider provider = new CascadingMouthRegionProvider(primary, fallback);

        DesktopMouthRegion region = provider.detect(syntheticFrame(80));

        assertTrue(region.trackingValid(), "fallback provider should recover a missing primary ROI");
        assertTrue(region.label().equals("fallback-mouth"), "fallback provider label should be preserved");
    }

    private static void testDefaultMouthRegionProviderFactoryReturnsUsableProvider() {
        DesktopMouthRegionProvider provider = DesktopMouthRegionProviderFactory.createDefault();

        DesktopMouthRegion region = provider.detect(syntheticFrame(80));

        assertTrue(region != null, "default provider should return a region object");
        assertTrue(region.trackingValid(), "default provider should be usable on a normal frame");
    }

    private static void testControllerPressesAndReleasesOnce() {
        RecordingActuator actuator = new RecordingActuator();
        GateHoldController controller = new GateHoldController(
                actuator,
                KeyStrokeSpec.parse("F13"),
                500L
        );

        controller.onMessage(GateProtocolParser.parseLine("STATE OPEN 1000 0.80"), 10L);
        controller.onMessage(GateProtocolParser.parseLine("STATE OPEN 1010 0.81"), 20L);
        controller.onMessage(GateProtocolParser.parseLine("STATE BLOCKED 1200 0.20"), 30L);
        controller.onMessage(GateProtocolParser.parseLine("STATE BLOCKED 1210 0.19"), 40L);

        assertEvents(actuator.events, "press:F13", "release:F13");
    }

    private static void testControllerReleasesOnTimeout() {
        RecordingActuator actuator = new RecordingActuator();
        GateHoldController controller = new GateHoldController(
                actuator,
                KeyStrokeSpec.parse("F14"),
                500L
        );

        controller.onMessage(GateProtocolParser.parseLine("STATE OPEN 1000 0.80"), 100L);
        controller.checkTimeout(599L);
        assertEvents(actuator.events, "press:F14");

        controller.checkTimeout(601L);
        assertEvents(actuator.events, "press:F14", "release:F14");
    }

    private static void testControllerReleasesOnDisconnect() {
        RecordingActuator actuator = new RecordingActuator();
        GateHoldController controller = new GateHoldController(
                actuator,
                KeyStrokeSpec.parse("CTRL+SHIFT+M"),
                500L
        );

        controller.onMessage(GateProtocolParser.parseLine("STATE OPEN 1000 0.80"), 100L);
        controller.onDisconnect();
        controller.onDisconnect();

        assertEvents(actuator.events, "press:Ctrl+Shift+M", "release:Ctrl+Shift+M");
    }

    private static void testServerAcceptsStateLines() {
        RecordingActuator actuator = new RecordingActuator();
        GateHoldController controller = new GateHoldController(
                actuator,
                KeyStrokeSpec.parse("F15"),
                500L
        );
        RecordingServerListener listener = new RecordingServerListener();
        DesktopGateServer server = new DesktopGateServer(0, controller, listener);
        server.start();
        try {
            try (Socket socket = new Socket("127.0.0.1", server.port());
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                writer.println("VOCAGATE 1");
                writer.println("STATE OPEN 1000 0.80");
                waitForEventCount(actuator.events, 1);
                writer.println("STATE BLOCKED 1050 0.20");
                waitForEventCount(actuator.events, 2);
            }
        } catch (Exception e) {
            throw new AssertionError("server socket test failed", e);
        } finally {
            server.stop();
        }

        assertEvents(actuator.events, "press:F15", "release:F15");
        assertTrue(listener.messages >= 2, "listener should observe state messages");
    }

    private static void testServerReportsKeyActionFailuresSeparately() {
        ThrowingActuator actuator = new ThrowingActuator();
        GateHoldController controller = new GateHoldController(
                actuator,
                KeyStrokeSpec.parse("F13"),
                500L
        );
        RecordingServerListener listener = new RecordingServerListener();
        DesktopGateServer server = new DesktopGateServer(0, controller, listener);
        server.start();
        try {
            try (Socket socket = new Socket("127.0.0.1", server.port());
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                writer.println("STATE OPEN 1000 0.80");
                waitForStatus(listener.statuses, "Key action failed: invalid key code");
            }
        } catch (Exception e) {
            throw new AssertionError("server key failure test failed", e);
        } finally {
            server.stop();
        }

        assertTrue(
                !listener.statuses.contains("Ignored invalid line: invalid key code"),
                "key actuator failure should not be reported as invalid protocol"
        );
    }

    private static void testLocalCameraBridgePressesAndReleasesController() {
        RecordingActuator actuator = new RecordingActuator();
        GateHoldController controller = new GateHoldController(
                actuator,
                KeyStrokeSpec.parse("CTRL+SHIFT+M"),
                500L
        );
        DesktopLocalGateBridge bridge = new DesktopLocalGateBridge(controller);

        bridge.onRecognitionResult(new DesktopRecognitionResult(
                0.72f,
                0.70f,
                30.0f,
                true,
                GateState.PRE_OPEN,
                true,
                DesktopMouthRegion.tracked(8, 10, 34, 18, "landmark-mouth"),
                "landmark-mouth",
                "PRE_OPEN"
        ), 1000L);
        bridge.onRecognitionResult(new DesktopRecognitionResult(
                0.02f,
                0.02f,
                30.0f,
                false,
                GateState.CLOSED,
                true,
                DesktopMouthRegion.tracked(8, 10, 34, 18, "landmark-mouth"),
                "landmark-mouth",
                "安静/未开麦"
        ), 1100L);

        assertEvents(actuator.events, "press:Ctrl+Shift+M", "release:Ctrl+Shift+M");
    }

    private static BufferedImage syntheticFrame(int mouthBrightness) {
        BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB);
        fill(image, 0, 0, image.getWidth(), image.getHeight(), rgb(96));
        fill(image, 50, 72, 60, 24, rgb(mouthBrightness));
        return image;
    }

    private static BufferedImage syntheticFrameWithPatches(int mouthBrightness, int outsideBrightness) {
        BufferedImage image = new BufferedImage(96, 72, BufferedImage.TYPE_INT_RGB);
        fill(image, 0, 0, image.getWidth(), image.getHeight(), rgb(96));
        fill(image, 8, 8, 32, 16, rgb(mouthBrightness));
        fill(image, 56, 40, 28, 18, rgb(outsideBrightness));
        return image;
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int rgb) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                image.setRGB(column, row, rgb);
            }
        }
    }

    private static int rgb(int value) {
        return (value << 16) | (value << 8) | value;
    }

    private static void assertEvents(List<String> actual, String... expected) {
        assertTrue(actual.size() == expected.length, "expected " + expected.length + " events but got " + actual);
        for (int i = 0; i < expected.length; i++) {
            assertTrue(actual.get(i).equals(expected[i]), "event " + i + " expected " + expected[i] + " but got " + actual.get(i));
        }
    }

    private static void assertNear(float expected, float actual, float tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void waitForEventCount(List<String> events, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < deadline) {
            if (events.size() >= expected) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("timed out waiting for " + expected + " events, got " + events);
    }

    private static void waitForStatus(List<String> statuses, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < deadline) {
            if (statuses.contains(expected)) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("timed out waiting for status " + expected + ", got " + statuses);
    }

    private static final class RecordingActuator implements KeyActuator {
        private final List<String> events = new ArrayList<>();

        @Override
        public void press(KeyStrokeSpec spec) {
            events.add("press:" + spec.displayName());
        }

        @Override
        public void release(KeyStrokeSpec spec) {
            events.add("release:" + spec.displayName());
        }
    }

    private static final class ThrowingActuator implements KeyActuator {
        @Override
        public void press(KeyStrokeSpec spec) {
            throw new IllegalArgumentException("invalid key code");
        }

        @Override
        public void release(KeyStrokeSpec spec) {
        }
    }

    private static final class RecordingServerListener implements DesktopGateServer.Listener {
        private int messages = 0;
        private final List<String> statuses = new ArrayList<>();

        @Override
        public void onStatus(String status) {
            statuses.add(status);
        }

        @Override
        public void onMessage(GateMessage message) {
            if (message.isState()) {
                messages++;
            }
        }
    }

    public static final class ThrowingCameraProvider implements DesktopCameraProvider {
        @Override
        public List<DesktopCameraDevice> listDevices() {
            throw new RuntimeException("native camera backend failed");
        }

        @Override
        public DesktopCameraPreviewSession openPreview(DesktopCameraDevice device) {
            throw new RuntimeException("native camera backend failed");
        }
    }

    public static final class LinkageErrorCameraProvider implements DesktopCameraProvider {
        @Override
        public List<DesktopCameraDevice> listDevices() {
            throw new NoClassDefFoundError("Could not initialize native camera backend");
        }

        @Override
        public DesktopCameraPreviewSession openPreview(DesktopCameraDevice device) {
            throw new NoClassDefFoundError("Could not initialize native camera backend");
        }
    }

    public static final class OneDeviceCameraProvider implements DesktopCameraProvider {
        @Override
        public List<DesktopCameraDevice> listDevices() {
            return Arrays.asList(new DesktopCameraDevice("fake:0", "Fake Camera"));
        }

        @Override
        public DesktopCameraPreviewSession openPreview(DesktopCameraDevice device) {
            throw new UnsupportedOperationException("not needed by smoke test");
        }
    }
}
