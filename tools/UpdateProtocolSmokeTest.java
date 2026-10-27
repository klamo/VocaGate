import com.vocagate.core.GateConfig;
import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.core.update.ManifestAsset;
import com.vocagate.core.update.UpdateManifest;
import com.vocagate.core.update.UpdateProtocolParser;
import com.vocagate.core.update.UpdateVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

public final class UpdateProtocolSmokeTest {
    public static void main(String[] args) throws Exception {
        parsesManifestAssets();
        parsesAlgorithmProfileIntoGateConfig();
        verifiesHashAndSignature();
        rejectsTamperedSignature();
        parsesStaticServerExamples();
        System.out.println("UpdateProtocolSmokeTest passed");
    }

    private static void parsesManifestAssets() {
        UpdateManifest manifest = UpdateProtocolParser.parseManifest(sampleManifestJson());
        assertEquals(1, manifest.schemaVersion(), "manifest schema version");
        assertEquals("stable", manifest.channel(), "manifest channel");
        assertEquals(2, manifest.assets().size(), "asset count");

        ManifestAsset android = manifest.asset("android-profile-stable");
        assertEquals("android-mlkit-v1", android.engine(), "android engine");
        assertEquals("android", android.platform(), "android platform");
        assertEquals("2026.06.29", android.version(), "android version");
        assertEquals("/v1/profiles/android-mlkit-v1/2026-06-29.json", android.url(), "android url");
        assertEquals("sha256-android-placeholder", android.sha256(), "android sha");

        ManifestAsset desktop = manifest.asset("desktop-mediapipe-model");
        assertEquals("desktop-mediapipe-v1", desktop.engine(), "desktop engine");
        assertEquals("model", desktop.kind(), "desktop kind");
    }

    private static void parsesAlgorithmProfileIntoGateConfig() {
        AlgorithmProfile profile = UpdateProtocolParser.parseAlgorithmProfile(sampleProfileJson());
        assertEquals(1, profile.schemaVersion(), "profile schema version");
        assertEquals("android-mlkit-v1", profile.engine(), "profile engine");
        assertEquals("2026.06.29", profile.version(), "profile version");

        GateConfig gate = profile.toGateConfig();
        assertClose(0.56f, gate.openThreshold, "open threshold");
        assertClose(0.46f, gate.preOpenThreshold, "pre-open threshold");
        assertClose(0.32f, gate.closeThreshold, "close threshold");
        assertEquals(2, gate.preOpenRequiredFrames, "pre-open frames");
        assertEquals(6, gate.closeRequiredFrames, "close frames");
        assertEquals(320L, gate.minOpenMs, "min open ms");
        assertEquals(760L, gate.holdMs, "hold ms");
        assertEquals(180L, gate.cooldownMs, "cooldown ms");

        assertClose(0.44f, profile.score().rhythmWeight(), "rhythm weight");
        assertClose(0.28f, profile.score().openWeight(), "open weight");
        assertClose(0.055f, profile.firstWord().riseThreshold(), "first-word rise threshold");
        assertEquals(180L, profile.firstWord().holdMs(), "first-word hold ms");
    }

    private static void verifiesHashAndSignature() throws Exception {
        byte[] payload = "vocagate profile payload".getBytes(StandardCharsets.UTF_8);
        assertEquals(
                "a1f5e4d4514839a72b88d63b9ce613ff66531c76f4f3a384c380a13d6af8f113",
                UpdateVerifier.sha256Hex(payload),
                "sha256"
        );

        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String signatureBase64 = sign(payload, keyPair);
        assertTrue(UpdateVerifier.verifyEd25519(payload, signatureBase64, publicKeyBase64), "signature verifies");
    }

    private static void rejectsTamperedSignature() throws Exception {
        byte[] payload = "vocagate profile payload".getBytes(StandardCharsets.UTF_8);
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String signatureBase64 = sign(payload, keyPair);
        byte[] tampered = "vocagate profile payload!".getBytes(StandardCharsets.UTF_8);
        assertFalse(UpdateVerifier.verifyEd25519(tampered, signatureBase64, publicKeyBase64), "tampered signature rejected");
    }

    private static void parsesStaticServerExamples() throws Exception {
        Path manifestPath = Path.of("server/update-service/public/v1/manifest.json");
        Path androidProfilePath = Path.of("server/update-service/public/v1/profiles/android-mlkit-v1/2026-06-29.json");
        Path desktopProfilePath = Path.of("server/update-service/public/v1/profiles/desktop-mediapipe-v1/2026-06-29.json");
        if (!Files.isRegularFile(manifestPath)) {
            return;
        }

        UpdateManifest manifest = UpdateProtocolParser.parseManifest(Files.readString(manifestPath));
        ManifestAsset android = manifest.asset("android-mlkit-profile-stable");
        ManifestAsset desktop = manifest.asset("desktop-mediapipe-profile-stable");
        byte[] androidProfile = Files.readAllBytes(androidProfilePath);
        byte[] desktopProfile = Files.readAllBytes(desktopProfilePath);

        assertTrue(UpdateVerifier.matchesSha256(androidProfile, android.sha256()), "android profile sha matches manifest");
        assertTrue(UpdateVerifier.matchesSha256(desktopProfile, desktop.sha256()), "desktop profile sha matches manifest");
        assertEquals("android-mlkit-v1", UpdateProtocolParser.parseAlgorithmProfile(new String(androidProfile, StandardCharsets.UTF_8)).engine(), "static android profile engine");
        assertEquals("desktop-mediapipe-v1", UpdateProtocolParser.parseAlgorithmProfile(new String(desktopProfile, StandardCharsets.UTF_8)).engine(), "static desktop profile engine");
    }

    private static String sign(byte[] payload, KeyPair keyPair) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload);
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private static String sampleManifestJson() {
        return "{"
                + "\"schema_version\":1,"
                + "\"channel\":\"stable\","
                + "\"generated_at\":\"2026-06-29T00:00:00Z\","
                + "\"assets\":["
                + "{"
                + "\"id\":\"android-profile-stable\","
                + "\"kind\":\"profile\","
                + "\"engine\":\"android-mlkit-v1\","
                + "\"platform\":\"android\","
                + "\"version\":\"2026.06.29\","
                + "\"min_app_version\":\"0.1.0\","
                + "\"url\":\"/v1/profiles/android-mlkit-v1/2026-06-29.json\","
                + "\"sha256\":\"sha256-android-placeholder\""
                + "},"
                + "{"
                + "\"id\":\"desktop-mediapipe-model\","
                + "\"kind\":\"model\","
                + "\"engine\":\"desktop-mediapipe-v1\","
                + "\"platform\":\"desktop-macos\","
                + "\"version\":\"2026.06.29\","
                + "\"min_app_version\":\"0.1.0\","
                + "\"url\":\"/v1/models/desktop-mediapipe/face_landmarker.task\","
                + "\"sha256\":\"sha256-model-placeholder\""
                + "}"
                + "]"
                + "}";
    }

    private static String sampleProfileJson() {
        return "{"
                + "\"schema_version\":1,"
                + "\"profile_id\":\"android-mlkit-v1-2026-06-29\","
                + "\"engine\":\"android-mlkit-v1\","
                + "\"version\":\"2026.06.29\","
                + "\"min_app_version\":\"0.1.0\","
                + "\"gate\":{"
                + "\"open_threshold\":0.56,"
                + "\"pre_open_threshold\":0.46,"
                + "\"close_threshold\":0.32,"
                + "\"pre_open_required_frames\":2,"
                + "\"close_required_frames\":6,"
                + "\"min_open_ms\":320,"
                + "\"hold_ms\":760,"
                + "\"cooldown_ms\":180"
                + "},"
                + "\"score\":{"
                + "\"rhythm_weight\":0.44,"
                + "\"open_weight\":0.28,"
                + "\"frontal_weight\":0.18,"
                + "\"stability_weight\":0.10,"
                + "\"occlusion_penalty_weight\":0.34,"
                + "\"long_open_penalty_weight\":0.24"
                + "},"
                + "\"first_word\":{"
                + "\"enabled\":true,"
                + "\"rise_threshold\":0.055,"
                + "\"hold_ms\":180"
                + "}"
                + "}";
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertClose(float expected, float actual, String label) {
        if (Math.abs(expected - actual) > 0.0001f) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertFalse(boolean value, String label) {
        if (value) {
            throw new AssertionError(label);
        }
    }
}
