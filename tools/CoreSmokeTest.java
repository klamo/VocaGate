import com.vocagate.core.GateConfig;
import com.vocagate.core.GateState;
import com.vocagate.core.GateStateMachine;
import com.vocagate.demo.core.MouthFeatureExtractor;
import com.vocagate.demo.core.MouthFeatures;
import com.vocagate.demo.core.OverlayCoordinateMapper;
import com.vocagate.demo.core.AnalysisLoadController;
import com.vocagate.demo.core.AudioAssistConfig;
import com.vocagate.demo.core.AudioAssistFrame;
import com.vocagate.demo.core.AudioAssistSignalAnalyzer;
import com.vocagate.demo.core.AudioVisualFusion;
import com.vocagate.demo.core.FirstWordProtection;
import com.vocagate.demo.core.HeadPoseCalibrator;
import com.vocagate.demo.core.MouthPoseNormalizer;
import com.vocagate.demo.core.Point2;
import com.vocagate.demo.core.ResponsiveControlLayout;
import com.vocagate.demo.core.ScreenShieldState;
import com.vocagate.demo.core.StatusTextFormatter;
import com.vocagate.demo.core.ScoreConfig;
import com.vocagate.demo.core.ShieldHudState;
import com.vocagate.demo.core.SpeakScoreEstimator;
import com.vocagate.demo.core.MaiShaoScore;
import com.vocagate.demo.core.MaiShaoScoreCalculator;
import com.vocagate.demo.core.UserTuning;
import com.vocagate.demo.audio.AudioSessionFiles;
import com.vocagate.demo.pc.PcLinkEndpoint;
import com.vocagate.demo.pc.PcLinkMode;
import com.vocagate.demo.pc.PcLinkProtocol;
import com.vocagate.demo.pc.PcLinkSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class CoreSmokeTest {
    public static void main(String[] args) throws IOException {
        testMouthOpenRatio();
        testScoreClampingAndPenalty();
        testStateMachineOpenAndSuspend();
        testStateMachinePreOpenStartsBeforeOpenThreshold();
        testStateMachinePreOpenFallsBackWhenTrendDisappears();
        testStateMachineHoldExtendsDuringContinuedSpeechTrend();
        testUserTuningMapping();
        testMaiShaoScore();
        testLatestRecordingSessionClearsOldFiles();
        testPcLinkProtocolFormatting();
        testPcLinkEndpointSelection();
        testOverlayFillCenterMappingSupportsLandscapeAndPortrait();
        testResponsiveControlLayoutUsesSidePanelInLandscape();
        testStatusTextFormatterUsesCompactLandscapeText();
        testAnalysisLoadControllerThrottlesHotDevices();
        testFirstWordProtectionModesAreUserFriendly();
        testFirstWordProtectionTriggersOnMouthRiseOnly();
        testHeadPoseCalibratorTreatsStandAngleAsBaseline();
        testMouthPoseNormalizerCompensatesRoll();
        testScreenShieldStateSuspendsVisualWork();
        testShieldHudStatePrioritizesActionableStatus();
        testAudioAssistBoostsBorderlineVisualSpeech();
        testAudioAssistNeverOpensFromAudioOnly();
        testAudioAssistSignalAnalyzerDetectsVoiceLikeRise();
        System.out.println("Core smoke tests passed");
    }

    private static void testMouthOpenRatio() {
        List<Point2> upperInner = Arrays.asList(
                new Point2(0.35f, 0.50f),
                new Point2(0.50f, 0.52f),
                new Point2(0.65f, 0.50f)
        );
        List<Point2> lowerInner = Arrays.asList(
                new Point2(0.35f, 0.58f),
                new Point2(0.50f, 0.62f),
                new Point2(0.65f, 0.58f)
        );
        List<Point2> allLipPoints = Arrays.asList(
                new Point2(0.30f, 0.50f),
                new Point2(0.70f, 0.50f),
                new Point2(0.30f, 0.62f),
                new Point2(0.70f, 0.62f)
        );

        MouthFeatures features = MouthFeatureExtractor.fromLipContours(
                upperInner,
                lowerInner,
                allLipPoints,
                0.10f,
                0.40f
        );

        assertNear(0.25f, features.mouthOpenRatio, 0.001f, "mouth open ratio");
        assertTrue(features.deltaMouth > 0.0f, "delta should be positive");
    }

    private static void testScoreClampingAndPenalty() {
        SpeakScoreEstimator estimator = new SpeakScoreEstimator(new ScoreConfig());

        float speakingScore = estimator.estimate(
                new MouthFeatures(0.10f, 0.40f, 0.25f, 0.20f, 0.85f, 0.25f),
                0.95f,
                0.95f,
                0.0f,
                0.0f
        );
        float blockedScore = estimator.estimate(
                new MouthFeatures(0.10f, 0.40f, 0.25f, 0.20f, 0.85f, 0.25f),
                0.95f,
                0.95f,
                1.0f,
                1.0f
        );

        assertTrue(speakingScore > 0.60f, "speaking score should exceed open threshold");
        assertTrue(blockedScore < speakingScore, "penalties should reduce score");
        assertTrue(blockedScore >= 0.0f && blockedScore <= 1.0f, "score should be clamped");
    }

    private static void testStateMachineOpenAndSuspend() {
        GateStateMachine machine = new GateStateMachine(new GateConfig());
        long nowMs = 0L;

        machine.update(0.70f, true, nowMs);
        machine.update(0.72f, true, nowMs + 70L);
        machine.update(0.74f, true, nowMs + 140L);

        assertTrue(machine.getState() == GateState.OPEN, "state should open after 3 high frames");
        assertTrue(machine.isGateOpen(), "gate should be open");

        machine.update(0.80f, false, nowMs + 210L);

        assertTrue(machine.getState() == GateState.SUSPEND, "invalid tracking should suspend");
        assertTrue(!machine.isGateOpen(), "suspend should block gate");
    }

    private static void testStateMachinePreOpenStartsBeforeOpenThreshold() {
        GateConfig config = new GateConfig(
                0.62f,
                0.52f,
                0.38f,
                3,
                8,
                500L,
                700L,
                250L
        );
        GateStateMachine machine = new GateStateMachine(config);

        machine.update(0.54f, true, 0L);

        assertTrue(machine.getState() == GateState.PRE_OPEN, "trend score should enter PRE_OPEN early");
        assertTrue(!machine.isGateOpen(), "PRE_OPEN should not count as gate-core OPEN");

        machine.update(0.63f, true, 70L);
        machine.update(0.64f, true, 140L);
        machine.update(0.65f, true, 210L);

        assertTrue(machine.getState() == GateState.OPEN, "confirmed high score should enter OPEN");
        assertTrue(machine.isGateOpen(), "confirmed OPEN should open gate core");
    }

    private static void testStateMachinePreOpenFallsBackWhenTrendDisappears() {
        GateConfig config = new GateConfig(
                0.62f,
                0.52f,
                0.38f,
                3,
                8,
                500L,
                700L,
                250L
        );
        GateStateMachine machine = new GateStateMachine(config);

        machine.update(0.54f, true, 0L);
        machine.update(0.49f, true, 70L);

        assertTrue(machine.getState() == GateState.CLOSED, "PRE_OPEN should release if the trend disappears");
    }

    private static void testStateMachineHoldExtendsDuringContinuedSpeechTrend() {
        GateConfig config = new GateConfig(
                0.62f,
                0.52f,
                0.38f,
                2,
                2,
                120L,
                220L,
                120L
        );
        GateStateMachine machine = new GateStateMachine(config);

        machine.update(0.64f, true, 0L);
        machine.update(0.65f, true, 70L);
        assertTrue(machine.getState() == GateState.OPEN, "setup should enter OPEN");

        machine.update(0.20f, true, 210L);
        machine.update(0.22f, true, 280L);
        assertTrue(machine.getState() == GateState.HOLD, "brief low score should enter HOLD");
        assertTrue(machine.isGateOpen(), "HOLD should keep gate open");

        machine.update(0.54f, true, 430L);
        machine.update(0.55f, true, 650L);
        machine.update(0.53f, true, 870L);

        assertTrue(machine.getState() == GateState.HOLD, "continued mouth trend should extend HOLD instead of entering cooldown");
        assertTrue(machine.isGateOpen(), "continued mouth trend should keep gate open");
    }

    private static void testUserTuningMapping() {
        UserTuning defaultTuning = new UserTuning(50, 50, 50);
        GateConfig defaultConfig = defaultTuning.toGateConfig();
        assertNear(0.62f, defaultConfig.openThreshold, 0.01f, "default open threshold");
        assertNear(0.52f, defaultConfig.preOpenThreshold, 0.01f, "default pre-open threshold");
        assertTrue(defaultConfig.preOpenRequiredFrames == 3, "default response should require 3 frames");
        assertTrue(defaultConfig.holdMs >= 700L && defaultConfig.holdMs <= 800L, "default hold should be around 750ms");

        UserTuning sensitiveTuning = new UserTuning(90, 80, 20);
        GateConfig sensitiveConfig = sensitiveTuning.toGateConfig();
        assertTrue(sensitiveConfig.openThreshold < defaultConfig.openThreshold, "higher sensitivity should lower open threshold");
        assertTrue(sensitiveConfig.preOpenThreshold < sensitiveConfig.openThreshold, "pre-open threshold should stay below open threshold");
        assertTrue(sensitiveConfig.preOpenRequiredFrames < defaultConfig.preOpenRequiredFrames, "faster response should use fewer confirmation frames");
        assertTrue(sensitiveConfig.holdMs < defaultConfig.holdMs, "lower hold slider should shorten hold");

        UserTuning earlyPressTuning = new UserTuning(50, 50, 50, 90, FirstWordProtection.STANDARD);
        GateConfig earlyPressConfig = earlyPressTuning.toGateConfig();
        assertTrue(
                earlyPressConfig.preOpenThreshold < defaultConfig.preOpenThreshold,
                "higher early press should enter PRE_OPEN earlier"
        );

        UserTuning cautiousPressTuning = new UserTuning(50, 50, 50, 10, FirstWordProtection.STANDARD);
        GateConfig cautiousPressConfig = cautiousPressTuning.toGateConfig();
        assertTrue(
                cautiousPressConfig.preOpenThreshold > defaultConfig.preOpenThreshold,
                "lower early press should wait longer before PRE_OPEN"
        );

        UserTuning protectedTuning = new UserTuning(70, 70, 50, 60, FirstWordProtection.AGGRESSIVE);
        assertTrue(protectedTuning.earlyPress == 60, "user tuning should keep early press");
        assertTrue(
                protectedTuning.firstWordProtectionMode == FirstWordProtection.AGGRESSIVE,
                "user tuning should keep first word protection mode"
        );
    }

    private static void testMaiShaoScore() {
        MaiShaoScore score = MaiShaoScoreCalculator.calculate(
                16000,
                160000,
                48000,
                1000000.0,
                250000.0,
                6
        );

        assertNear(75.0f, score.suppressionPercent, 0.01f, "suppression percent");
        assertNear(30.0f, score.openPercent, 0.01f, "open percent");
        assertTrue(score.transitionsPerMinute > 30.0f, "transitions per minute should be calculated");
        assertTrue(score.value >= 0.0f && score.value <= 100.0f, "mai shao score should be clamped");
    }

    private static void testAudioAssistBoostsBorderlineVisualSpeech() {
        GateConfig gateConfig = new GateConfig(
                0.62f,
                0.52f,
                0.38f,
                3,
                8,
                500L,
                700L,
                250L
        );
        AudioVisualFusion fusion = new AudioVisualFusion(AudioAssistConfig.standard());
        MouthFeatures borderlineMouth = new MouthFeatures(
                0.06f,
                0.38f,
                0.16f,
                0.045f,
                0.42f,
                0.16f
        );
        AudioAssistFrame voiceFrame = new AudioAssistFrame(
                1010L,
                -35.0f,
                -56.0f,
                0.84f,
                0.78f
        );

        AudioVisualFusion.Result result = fusion.fuse(
                0.49f,
                borderlineMouth,
                voiceFrame,
                gateConfig,
                true,
                1020L
        );

        assertTrue(result.audioAssisted, "borderline visual speech should use audio assist");
        assertTrue(result.boost > 0.0f, "audio assist should add a positive boost");
        assertTrue(result.score >= gateConfig.preOpenThreshold, "audio assist should reach PRE_OPEN floor");
    }

    private static void testAudioAssistNeverOpensFromAudioOnly() {
        GateConfig gateConfig = new GateConfig();
        AudioVisualFusion fusion = new AudioVisualFusion(AudioAssistConfig.standard());
        MouthFeatures staticMouth = new MouthFeatures(
                0.02f,
                0.40f,
                0.05f,
                0.004f,
                0.03f,
                0.05f
        );
        AudioAssistFrame loudVoiceFrame = new AudioAssistFrame(
                2000L,
                -25.0f,
                -58.0f,
                0.95f,
                0.92f
        );

        AudioVisualFusion.Result result = fusion.fuse(
                0.20f,
                staticMouth,
                loudVoiceFrame,
                gateConfig,
                true,
                2010L
        );

        assertTrue(!result.audioAssisted, "audio without mouth motion should not assist");
        assertNear(0.20f, result.score, 0.001f, "audio-only score should remain visual score");
        assertNear(0.0f, result.boost, 0.001f, "audio-only boost should be zero");
    }

    private static void testAudioAssistSignalAnalyzerDetectsVoiceLikeRise() {
        AudioAssistSignalAnalyzer analyzer = new AudioAssistSignalAnalyzer(16000);
        byte[] quiet = pcmSine(16000, 320, 220.0, 0.001);
        byte[] voiceLike = pcmSine(16000, 320, 440.0, 0.24);

        AudioAssistFrame quietFrame = analyzer.analyzePcm16(quiet, 0, quiet.length, 1000L);
        AudioAssistFrame voiceFrame = analyzer.analyzePcm16(voiceLike, 0, voiceLike.length, 1020L);

        assertTrue(quietFrame.voiceConfidence < 0.20f, "quiet frame should not look like voice");
        assertTrue(voiceFrame.voiceConfidence > 0.55f, "voice-like energy rise should produce confidence");
        assertTrue(voiceFrame.riseScore > quietFrame.riseScore, "louder voice-like frame should rise above noise");
        assertTrue(voiceFrame.rmsDb > quietFrame.rmsDb, "voice-like frame should have higher RMS");
    }

    private static void testLatestRecordingSessionClearsOldFiles() throws IOException {
        File root = new File("/tmp/vocagate-session-test-" + System.nanoTime());
        File oldSession = new File(root, "session_old");
        File oldFile = new File(oldSession, "full.wav");
        assertTrue(oldSession.mkdirs(), "old session dir should be created");
        try (FileOutputStream output = new FileOutputStream(oldFile)) {
            output.write(new byte[]{1, 2, 3});
        }

        AudioSessionFiles files = AudioSessionFiles.prepareLatest(root);

        assertTrue(files.sessionDir.exists(), "latest session dir should exist");
        assertTrue("latest".equals(files.sessionDir.getName()), "session dir should be latest");
        assertTrue(!oldFile.exists(), "old recording should be removed");
        assertTrue(files.fullWav.getName().equals("full.wav"), "full wav name");
        assertTrue(files.gatedWav.getName().equals("gated.wav"), "gated wav name");
    }

    private static void testPcLinkProtocolFormatting() {
        assertTrue("VOCAGATE 1".equals(PcLinkProtocol.helloLine()), "hello line");
        assertTrue(
                "STATE OPEN 1200 0.82".equals(PcLinkProtocol.stateLine(true, 1200L, 0.82f)),
                "open state line"
        );
        assertTrue(
                "STATE PRE_OPEN 1250 0.66".equals(PcLinkProtocol.preOpenLine(1250L, 0.66f)),
                "pre-open state line"
        );
        assertTrue(
                "STATE BLOCKED 1300 0.21".equals(PcLinkProtocol.stateLine(false, 1300L, 0.21f)),
                "blocked state line"
        );
        assertTrue("PAIR 493812".equals(PcLinkProtocol.pairLine("493812")), "pair line");
        assertTrue(
                "PING 1400 OPEN".equals(PcLinkProtocol.pingLine(1400L, true)),
                "ping line"
        );
    }

    private static void testPcLinkEndpointSelection() {
        assertTrue(PcLinkSettings.DEFAULT_PORT == 27183, "default pc link port");

        PcLinkSettings autoWithWifi = new PcLinkSettings(PcLinkMode.AUTO, "192.168.1.20", "493812", 0);
        List<PcLinkEndpoint> autoEndpoints = autoWithWifi.endpoints();
        assertTrue(autoWithWifi.port() == 27183, "invalid port should fall back to default");
        assertTrue(autoEndpoints.size() == 2, "auto with wifi should try two endpoints");
        assertTrue(autoEndpoints.get(0).channelName().equals("USB"), "auto should try usb first");
        assertTrue(autoEndpoints.get(0).host().equals("127.0.0.1"), "usb endpoint host");
        assertTrue(autoEndpoints.get(0).port() == 27183, "usb endpoint default port");
        assertTrue(autoEndpoints.get(1).channelName().equals("Wi-Fi"), "auto should try wifi second");
        assertTrue(autoEndpoints.get(1).host().equals("192.168.1.20"), "wifi endpoint host");
        assertTrue(autoEndpoints.get(1).port() == 27183, "wifi endpoint default port");

        PcLinkSettings usbOnly = new PcLinkSettings(PcLinkMode.USB, "192.168.1.20", "493812", 27183);
        assertTrue(usbOnly.endpoints().size() == 1, "usb mode should have one endpoint");
        assertTrue(usbOnly.endpoints().get(0).channelName().equals("USB"), "usb mode endpoint");

        PcLinkSettings wifiMissing = new PcLinkSettings(PcLinkMode.WIFI, "", "", 27183);
        assertTrue(wifiMissing.endpoints().isEmpty(), "wifi mode without host should have no endpoints");
    }

    private static void testOverlayFillCenterMappingSupportsLandscapeAndPortrait() {
        Point2 portraitCenter = OverlayCoordinateMapper.mapFillCenter(
                320.0f,
                180.0f,
                640,
                360,
                360,
                640,
                false
        );
        assertNear(180.0f, portraitCenter.x, 0.01f, "portrait center x");
        assertNear(320.0f, portraitCenter.y, 0.01f, "portrait center y");

        Point2 landscapePoint = OverlayCoordinateMapper.mapFillCenter(
                160.0f,
                90.0f,
                640,
                360,
                640,
                360,
                false
        );
        assertNear(160.0f, landscapePoint.x, 0.01f, "landscape point x");
        assertNear(90.0f, landscapePoint.y, 0.01f, "landscape point y");

        Point2 mirroredPoint = OverlayCoordinateMapper.mapFillCenter(
                160.0f,
                90.0f,
                640,
                360,
                640,
                360,
                true
        );
        assertNear(480.0f, mirroredPoint.x, 0.01f, "mirrored landscape point x");
        assertNear(90.0f, mirroredPoint.y, 0.01f, "mirrored landscape point y");

        assertTrue(
                OverlayCoordinateMapper.orientedWidth(640, 360, 90) == 360,
                "90 degree rotation should swap overlay width"
        );
        assertTrue(
                OverlayCoordinateMapper.orientedHeight(640, 360, 90) == 640,
                "90 degree rotation should swap overlay height"
        );
        assertTrue(
                OverlayCoordinateMapper.orientedWidth(640, 360, 0) == 640,
                "0 degree rotation should keep overlay width"
        );
    }

    private static void testResponsiveControlLayoutUsesSidePanelInLandscape() {
        ResponsiveControlLayout.Spec landscape = ResponsiveControlLayout.forViewport(2048, 922, 2.0f);
        assertTrue(landscape.landscape, "wide viewport should use landscape layout");
        assertTrue(landscape.sidePanel, "landscape should use side panel");
        assertTrue(landscape.compactStatus, "landscape status should be compact");
        assertTrue(landscape.panelWidthPx <= 660, "side panel should not cover too much landscape width");
        assertTrue(landscape.panelWidthPx >= 520, "side panel should still fit controls");

        ResponsiveControlLayout.Spec portrait = ResponsiveControlLayout.forViewport(1080, 2400, 3.0f);
        assertTrue(!portrait.landscape, "tall viewport should use portrait layout");
        assertTrue(!portrait.sidePanel, "portrait should keep bottom controls");
        assertTrue(!portrait.compactStatus, "portrait status should keep full debug text");
        assertTrue(portrait.panelWidthPx == 1080, "portrait panel should span width");
    }

    private static void testStatusTextFormatterUsesCompactLandscapeText() {
        String compact = StatusTextFormatter.format(
                true,
                "Face tracked",
                "PC: waiting, set Wi-Fi",
                GateState.OPEN,
                true,
                0.74f,
                0.036f,
                0.09f,
                45.5f
        );
        assertTrue(compact.split("\\n", -1).length <= 6, "compact landscape status should fit the side panel");
        assertTrue(!compact.contains("Android Demo"), "compact landscape status should drop long title text");

        String full = StatusTextFormatter.format(
                false,
                "Face tracked",
                "PC: waiting, set Wi-Fi",
                GateState.OPEN,
                true,
                0.74f,
                0.036f,
                0.09f,
                45.5f
        );
        assertTrue(full.contains("VocaGate Android Demo"), "portrait status should keep full title text");
        assertTrue(full.contains("Gate: OPEN / 放行麦克风"), "portrait status should keep the readable gate label");
    }

    private static void testAnalysisLoadControllerThrottlesHotDevices() {
        AnalysisLoadController controller = new AnalysisLoadController();

        assertTrue(controller.shouldAnalyze(1_000L, AnalysisLoadController.THERMAL_NONE), "first frame should run");
        assertTrue(!controller.shouldAnalyze(1_030L, AnalysisLoadController.THERMAL_NONE), "cool mode should throttle above 15fps");
        assertTrue(controller.shouldAnalyze(1_070L, AnalysisLoadController.THERMAL_NONE), "cool mode should allow roughly 15fps");

        controller.recordDetectionLatency(120.0f);
        assertTrue(!controller.shouldAnalyze(1_150L, AnalysisLoadController.THERMAL_NONE), "slow detector should trigger eco interval");
        assertTrue(controller.shouldAnalyze(1_180L, AnalysisLoadController.THERMAL_NONE), "eco interval should allow roughly 10fps");

        assertTrue(!controller.shouldAnalyze(1_300L, AnalysisLoadController.THERMAL_SEVERE), "severe thermal mode should slow down more");
        assertTrue(controller.shouldAnalyze(1_350L, AnalysisLoadController.THERMAL_SEVERE), "severe thermal mode should still keep a usable cadence");
    }

    private static void testFirstWordProtectionModesAreUserFriendly() {
        assertTrue(FirstWordProtection.modeName(FirstWordProtection.OFF).equals("关闭"), "off label");
        assertTrue(FirstWordProtection.modeName(FirstWordProtection.CONSERVATIVE).equals("保守"), "conservative label");
        assertTrue(FirstWordProtection.modeName(FirstWordProtection.STANDARD).equals("标准"), "standard label");
        assertTrue(FirstWordProtection.modeName(FirstWordProtection.AGGRESSIVE).equals("激进"), "aggressive label");
        assertTrue(FirstWordProtection.holdMsForMode(FirstWordProtection.OFF) == 0L, "off should not compensate");
        assertTrue(FirstWordProtection.holdMsForMode(FirstWordProtection.STANDARD) == 180L, "standard hold");
    }

    private static void testFirstWordProtectionTriggersOnMouthRiseOnly() {
        FirstWordProtection protection = new FirstWordProtection(FirstWordProtection.STANDARD);

        MouthFeatures idle = new MouthFeatures(0.02f, 0.45f, 0.04f, 0.0f, 0.0f, 0.09f);
        assertTrue(!protection.update(idle, true, 0.95f, 0L).active, "idle mouth should not trigger");

        MouthFeatures rising = new MouthFeatures(0.07f, 0.45f, 0.16f, 0.12f, 0.0f, 0.36f);
        FirstWordProtection.Result first = protection.update(rising, true, 0.95f, 66L);
        assertTrue(first.active, "mouth opening rise should trigger first word protection");
        assertTrue(first.justTriggered, "first rise should be marked as trigger");
        assertTrue(first.sourceLabel.equals("onset"), "trigger should expose readable source");

        MouthFeatures quietAgain = new MouthFeatures(0.03f, 0.45f, 0.06f, 0.10f, 0.0f, 0.13f);
        assertTrue(protection.update(quietAgain, true, 0.95f, 180L).active, "brief compensation window should stay active");
        assertTrue(!protection.update(quietAgain, true, 0.95f, 280L).active, "compensation should expire quickly");

        FirstWordProtection disabled = new FirstWordProtection(FirstWordProtection.OFF);
        assertTrue(!disabled.update(rising, true, 0.95f, 66L).active, "disabled protection should never trigger");

        FirstWordProtection unstableFace = new FirstWordProtection(FirstWordProtection.STANDARD);
        unstableFace.update(idle, true, 0.95f, 0L);
        assertTrue(!unstableFace.update(rising, true, 0.30f, 66L).active, "unstable face should not trigger early key press");
    }

    private static void testHeadPoseCalibratorTreatsStandAngleAsBaseline() {
        HeadPoseCalibrator calibrator = new HeadPoseCalibrator();
        for (int i = 0; i < 30; i++) {
            calibrator.update(12.0f, -14.0f);
        }

        assertNear(0.0f, calibrator.relativeYaw(12.0f), 0.5f, "stand yaw should become baseline");
        assertNear(0.0f, calibrator.relativeRoll(-14.0f), 0.5f, "stand roll should become baseline");
        assertTrue(calibrator.frontalScore(12.0f, -14.0f) > 0.95f, "baseline pose should score as frontal");
        assertTrue(calibrator.frontalScore(50.0f, -14.0f) < 0.55f, "large relative yaw should still be penalized");
    }

    private static void testMouthPoseNormalizerCompensatesRoll() {
        List<Point2> baseAll = Arrays.asList(
                new Point2(-1.00f, 0.00f),
                new Point2(1.00f, 0.00f),
                new Point2(0.00f, -0.20f),
                new Point2(0.00f, 0.20f)
        );
        List<Point2> rolledAll = MouthPoseNormalizer.rotateAroundCenter(baseAll, 28.0f);
        List<Point2> normalizedAll = MouthPoseNormalizer.normalize(rolledAll, 28.0f);

        assertNear(baseAll.get(0).x, normalizedAll.get(0).x, 0.01f, "normalized left mouth x");
        assertNear(baseAll.get(0).y, normalizedAll.get(0).y, 0.01f, "normalized left mouth y");
        assertNear(baseAll.get(3).y, normalizedAll.get(3).y, 0.01f, "normalized lower lip y");
    }

    private static void testScreenShieldStateSuspendsVisualWork() {
        ScreenShieldState state = new ScreenShieldState();

        assertTrue(state.shouldRenderCameraPreview(), "screen should initially render preview");
        assertTrue(state.shouldRenderOverlay(), "screen should initially render overlay");
        assertTrue(state.shouldUpdateStatusText(), "screen should initially update status");

        state.activate(0.70f);

        assertTrue(!state.shouldRenderCameraPreview(), "shield should pause camera preview rendering");
        assertTrue(!state.shouldRenderOverlay(), "shield should pause overlay rendering");
        assertTrue(!state.shouldUpdateStatusText(), "shield should pause hidden status updates");
        assertTrue(state.shouldUpdateShieldHud(), "shield should keep the low distraction HUD updated");
        assertTrue(state.activeBrightness() >= 0.10f, "shield brightness should be visible enough for the glow HUD");
        assertTrue(state.activeBrightness() <= 0.18f, "shield brightness should stay low distraction");

        state.deactivate();

        assertTrue(state.shouldRenderCameraPreview(), "preview should resume after shield exits");
        assertTrue(state.shouldRenderOverlay(), "overlay should resume after shield exits");
        assertTrue(state.shouldUpdateStatusText(), "status should resume after shield exits");
        assertTrue(!state.shouldUpdateShieldHud(), "HUD updates are only needed while shield is active");
    }

    private static void testShieldHudStatePrioritizesActionableStatus() {
        ShieldHudState openWithPcLost = ShieldHudState.from(
                "Face tracked",
                "PC: waiting",
                GateState.OPEN,
                true,
                0.82f
        );
        assertTrue(openWithPcLost.kind == ShieldHudState.Kind.OPEN, "shield color should follow open state even when PC is missing");
        assertTrue(openWithPcLost.caption.equals("PC lost · 0.82"), "pc missing should remain visible as caption");

        ShieldHudState noFace = ShieldHudState.from(
                "No face",
                "PC: USB connected",
                GateState.SUSPEND,
                false,
                0.0f
        );
        assertTrue(noFace.kind == ShieldHudState.Kind.NO_FACE, "suspend should show no face");
        assertTrue(!noFace.flow, "no face should not use flowing segments");
        assertTrue(noFace.pulse, "no face should breathe gently");

        ShieldHudState pre = ShieldHudState.from(
                "首字保护",
                "PC: USB connected",
                GateState.PRE_OPEN,
                true,
                0.58f
        );
        assertTrue(pre.kind == ShieldHudState.Kind.PRE_OPEN, "pre-open should show pre");
        assertTrue(!pre.flow, "pre-open should not flow");
        assertTrue(pre.pulse, "pre-open should breathe");
        assertTrue(pre.caption.equals("USB · 0.58"), "pre-open caption should include channel and score");

        ShieldHudState open = ShieldHudState.from(
                "Face tracked",
                "PC: Wi-Fi connected",
                GateState.OPEN,
                true,
                0.76f
        );
        assertTrue(open.kind == ShieldHudState.Kind.OPEN, "open should show open");
        assertTrue(!open.flow, "open should not flow");
        assertTrue(open.pulse, "open should breathe");
        assertTrue(open.caption.equals("Wi-Fi · 0.76"), "open caption should include wifi and score");

        ShieldHudState blocked = ShieldHudState.from(
                "Face tracked",
                "PC: USB connected",
                GateState.CLOSED,
                false,
                0.21f
        );
        assertTrue(blocked.kind == ShieldHudState.Kind.BLOCKED, "closed should show blocked");
        assertTrue(!blocked.flow, "blocked should not flow strongly");
        assertTrue(blocked.pulse, "blocked should still have a subtle breathing glow");
    }

    private static byte[] pcmSine(int sampleRate, int samples, double frequencyHz, double amplitude) {
        byte[] buffer = new byte[samples * 2];
        double clampedAmplitude = Math.max(0.0, Math.min(1.0, amplitude));
        for (int i = 0; i < samples; i++) {
            double phase = (2.0 * Math.PI * frequencyHz * i) / sampleRate;
            short value = (short) Math.round(Math.sin(phase) * clampedAmplitude * Short.MAX_VALUE);
            buffer[i * 2] = (byte) (value & 0xFF);
            buffer[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return buffer;
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
}
