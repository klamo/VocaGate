import com.vocagate.core.GateConfig;
import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.demo.core.ScoreConfig;
import com.vocagate.demo.core.UserTuning;
import com.vocagate.desktop.mediapipe.MediaPipeFaceLandmarkerFrameAnalyzer;

public final class UpdateProfileApplicationSmokeTest {
    public static void main(String[] args) {
        remoteProfileBecomesDefaultGateBaseline();
        localTuningStillOffsetsRemoteGateBaseline();
        remoteScoreWeightsBecomeAndroidScoreConfig();
        remoteProfileBecomesDesktopMediaPipeGateBaseline();
        System.out.println("UpdateProfileApplicationSmokeTest passed");
    }

    private static void remoteProfileBecomesDefaultGateBaseline() {
        AlgorithmProfile profile = profileWith(
                "android-mlkit-v1",
                new GateConfig(0.58f, 0.44f, 0.31f, 2, 6, 320L, 780L, 180L)
        );

        GateConfig gate = UserTuning.defaults().toGateConfig(profile);

        assertNear(0.58f, gate.openThreshold, 0.0001f, "profile open threshold");
        assertNear(0.44f, gate.preOpenThreshold, 0.0001f, "profile pre-open threshold");
        assertNear(0.31f, gate.closeThreshold, 0.0001f, "profile close threshold");
        assertEquals(2, gate.preOpenRequiredFrames, "profile pre-open frames");
        assertEquals(6, gate.closeRequiredFrames, "profile close frames");
        assertEquals(320L, gate.minOpenMs, "profile min open");
        assertEquals(780L, gate.holdMs, "profile hold");
        assertEquals(180L, gate.cooldownMs, "profile cooldown");
    }

    private static void localTuningStillOffsetsRemoteGateBaseline() {
        AlgorithmProfile profile = profileWith(
                "android-mlkit-v1",
                new GateConfig(0.58f, 0.44f, 0.31f, 3, 7, 460L, 760L, 220L)
        );
        GateConfig defaultGate = UserTuning.defaults().toGateConfig(profile);
        GateConfig sensitiveGate = new UserTuning(80, 75, 30).toGateConfig(profile);
        GateConfig cautiousGate = new UserTuning(20, 25, 80).toGateConfig(profile);

        assertTrue(sensitiveGate.openThreshold < defaultGate.openThreshold, "higher sensitivity should lower remote open threshold");
        assertTrue(sensitiveGate.preOpenRequiredFrames < defaultGate.preOpenRequiredFrames, "faster response should reduce remote confirmation frames");
        assertTrue(sensitiveGate.holdMs < defaultGate.holdMs, "lower hold slider should shorten remote hold");
        assertTrue(cautiousGate.openThreshold > defaultGate.openThreshold, "lower sensitivity should raise remote open threshold");
        assertTrue(cautiousGate.holdMs > defaultGate.holdMs, "higher hold slider should lengthen remote hold");
    }

    private static void remoteScoreWeightsBecomeAndroidScoreConfig() {
        AlgorithmProfile profile = new AlgorithmProfile(
                1,
                "android-mlkit-v1-test",
                "android-mlkit-v1",
                "2026.06.29",
                "0.1.0",
                new GateConfig(),
                new AlgorithmProfile.ScoreWeights(0.31f, 0.29f, 0.19f, 0.11f, 0.41f, 0.27f),
                AlgorithmProfile.FirstWordConfig.defaults()
        );

        ScoreConfig config = ScoreConfig.fromProfile(profile.score());

        assertNear(0.31f, config.rhythmWeight, 0.0001f, "profile rhythm weight");
        assertNear(0.29f, config.openWeight, 0.0001f, "profile open weight");
        assertNear(0.19f, config.frontalWeight, 0.0001f, "profile frontal weight");
        assertNear(0.11f, config.stabilityWeight, 0.0001f, "profile stability weight");
        assertNear(0.41f, config.occlusionPenaltyWeight, 0.0001f, "profile occlusion penalty");
        assertNear(0.27f, config.longOpenPenaltyWeight, 0.0001f, "profile long-open penalty");
    }

    private static void remoteProfileBecomesDesktopMediaPipeGateBaseline() {
        AlgorithmProfile profile = profileWith(
                "desktop-mediapipe-v1",
                new GateConfig(0.23f, 0.13f, 0.07f, 2, 4, 260L, 420L, 120L)
        );

        GateConfig gate = MediaPipeFaceLandmarkerFrameAnalyzer.gateConfigForProfile(profile);

        assertNear(0.23f, gate.openThreshold, 0.0001f, "desktop profile open threshold");
        assertNear(0.13f, gate.preOpenThreshold, 0.0001f, "desktop profile pre-open threshold");
        assertNear(0.07f, gate.closeThreshold, 0.0001f, "desktop profile close threshold");
        assertEquals(2, gate.preOpenRequiredFrames, "desktop profile pre-open frames");
        assertEquals(4, gate.closeRequiredFrames, "desktop profile close frames");
        assertEquals(260L, gate.minOpenMs, "desktop profile min open");
        assertEquals(420L, gate.holdMs, "desktop profile hold");
        assertEquals(120L, gate.cooldownMs, "desktop profile cooldown");
    }

    private static AlgorithmProfile profileWith(String engine, GateConfig gateConfig) {
        return new AlgorithmProfile(
                1,
                engine + "-test",
                engine,
                "2026.06.29",
                "0.1.0",
                gateConfig,
                AlgorithmProfile.ScoreWeights.defaults(),
                AlgorithmProfile.FirstWordConfig.defaults()
        );
    }

    private static void assertNear(float expected, float actual, float tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }
}
