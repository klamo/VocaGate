package com.vocagate.demo.core;

import com.vocagate.core.GateConfig;
import com.vocagate.core.GateState;
import com.vocagate.core.GateStateMachine;
import com.vocagate.demo.audio.AudioSessionFiles;
import com.vocagate.demo.pc.PcLinkEndpoint;
import com.vocagate.demo.pc.PcLinkMode;
import com.vocagate.demo.pc.PcLinkProtocol;
import com.vocagate.demo.pc.PcLinkSettings;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CoreAlgorithmTest {
    @Test
    public void mouthOpenRatioIsNormalizedByMouthWidth() {
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

        assertEquals(0.25f, features.mouthOpenRatio, 0.001f);
        assertTrue(features.deltaMouth > 0.0f);
    }

    @Test
    public void penaltiesReduceSpeakScoreAndClampResult() {
        SpeakScoreEstimator estimator = new SpeakScoreEstimator(new ScoreConfig());

        MouthFeatures features = new MouthFeatures(0.10f, 0.40f, 0.25f, 0.20f, 0.85f, 0.25f);
        float speakingScore = estimator.estimate(features, 0.95f, 0.95f, 0.0f, 0.0f);
        float blockedScore = estimator.estimate(features, 0.95f, 0.95f, 1.0f, 1.0f);

        assertTrue(speakingScore > 0.60f);
        assertTrue(blockedScore < speakingScore);
        assertTrue(blockedScore >= 0.0f && blockedScore <= 1.0f);
    }

    @Test
    public void stateMachineRequiresConsecutiveHighFramesAndSuspendsWhenTrackingInvalid() {
        GateStateMachine machine = new GateStateMachine(new GateConfig());

        machine.update(0.70f, true, 0L);
        machine.update(0.72f, true, 70L);
        machine.update(0.74f, true, 140L);

        assertEquals(GateState.OPEN, machine.getState());
        assertTrue(machine.isGateOpen());

        machine.update(0.80f, false, 210L);

        assertEquals(GateState.SUSPEND, machine.getState());
        assertFalse(machine.isGateOpen());
    }

    @Test
    public void userTuningMapsBasicSlidersToGateConfig() {
        UserTuning defaultTuning = new UserTuning(50, 50, 50);
        GateConfig defaultConfig = defaultTuning.toGateConfig();

        UserTuning fasterAndMoreSensitive = new UserTuning(90, 80, 20);
        GateConfig fasterConfig = fasterAndMoreSensitive.toGateConfig();

        assertEquals(0.62f, defaultConfig.openThreshold, 0.01f);
        assertEquals(3, defaultConfig.preOpenRequiredFrames);
        assertTrue(defaultConfig.holdMs >= 700L && defaultConfig.holdMs <= 800L);
        assertTrue(fasterConfig.openThreshold < defaultConfig.openThreshold);
        assertTrue(fasterConfig.preOpenRequiredFrames < defaultConfig.preOpenRequiredFrames);
        assertTrue(fasterConfig.holdMs < defaultConfig.holdMs);
    }

    @Test
    public void maiShaoScoreReportsSuppressionAndOpenPercent() {
        MaiShaoScore score = MaiShaoScoreCalculator.calculate(
                16000,
                160000,
                48000,
                1000000.0,
                250000.0,
                6
        );

        assertEquals(75.0f, score.suppressionPercent, 0.01f);
        assertEquals(30.0f, score.openPercent, 0.01f);
        assertTrue(score.transitionsPerMinute > 30.0f);
        assertTrue(score.value >= 0.0f && score.value <= 100.0f);
    }

    @Test
    public void latestRecordingSessionClearsOldFiles() throws IOException {
        File root = new File(System.getProperty("java.io.tmpdir"), "vocagate-session-test-" + System.nanoTime());
        File oldSession = new File(root, "session_old");
        File oldFile = new File(oldSession, "full.wav");
        assertTrue(oldSession.mkdirs());
        try (FileOutputStream output = new FileOutputStream(oldFile)) {
            output.write(new byte[]{1, 2, 3});
        }

        AudioSessionFiles files = AudioSessionFiles.prepareLatest(root);

        assertTrue(files.sessionDir.exists());
        assertEquals("latest", files.sessionDir.getName());
        assertFalse(oldFile.exists());
        assertEquals("full.wav", files.fullWav.getName());
        assertEquals("gated.wav", files.gatedWav.getName());
    }

    @Test
    public void pcLinkProtocolFormatsDesktopLines() {
        assertEquals("VOCAGATE 1", PcLinkProtocol.helloLine());
        assertEquals("STATE OPEN 1200 0.82", PcLinkProtocol.stateLine(true, 1200L, 0.82f));
        assertEquals("STATE PRE_OPEN 1250 0.66", PcLinkProtocol.preOpenLine(1250L, 0.66f));
        assertEquals("STATE BLOCKED 1300 0.21", PcLinkProtocol.stateLine(false, 1300L, 0.21f));
        assertEquals("PAIR 493812", PcLinkProtocol.pairLine("493812"));
        assertEquals("PING 1400 OPEN", PcLinkProtocol.pingLine(1400L, true));
    }

    @Test
    public void pcLinkSettingsSelectsUsbBeforeWifiInAutoMode() {
        assertEquals(27183, PcLinkSettings.DEFAULT_PORT);

        PcLinkSettings settings = new PcLinkSettings(PcLinkMode.AUTO, "192.168.1.20", "493812", 0);
        List<PcLinkEndpoint> endpoints = settings.endpoints();

        assertEquals(27183, settings.port());
        assertEquals(2, endpoints.size());
        assertEquals("USB", endpoints.get(0).channelName());
        assertEquals("127.0.0.1", endpoints.get(0).host());
        assertEquals(27183, endpoints.get(0).port());
        assertEquals("Wi-Fi", endpoints.get(1).channelName());
        assertEquals("192.168.1.20", endpoints.get(1).host());
        assertEquals(27183, endpoints.get(1).port());
    }

    @Test
    public void screenShieldSavesAndRestoresBrightness() {
        ScreenShieldState state = new ScreenShieldState();

        assertFalse(state.isActive());
        assertEquals(0.01f, ScreenShieldState.DIM_BRIGHTNESS, 0.001f);

        state.activate(0.70f);

        assertTrue(state.isActive());
        assertEquals(0.70f, state.previousBrightness(), 0.001f);
        assertEquals(0.01f, state.activeBrightness(), 0.001f);

        float restored = state.deactivate();

        assertFalse(state.isActive());
        assertEquals(0.70f, restored, 0.001f);
    }
}
