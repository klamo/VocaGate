import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.core.update.ManifestAsset;
import com.vocagate.core.update.UpdateCheckClient;
import com.vocagate.core.update.UpdateCheckResult;
import com.vocagate.core.update.UpdateClientConfig;
import com.vocagate.core.update.UpdateHttpClient;
import com.vocagate.core.update.UpdatePreferences;
import com.vocagate.core.update.UpdateSchedule;
import com.vocagate.core.update.UpdateVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class UpdateClientSmokeTest {
    public static void main(String[] args) throws Exception {
        skipsWhenDisabled();
        gatesDailyChecks();
        downloadsAndVerifiesProfile();
        rejectsHashMismatch();
        System.out.println("UpdateClientSmokeTest passed");
    }

    private static void skipsWhenDisabled() {
        UpdatePreferences preferences = new UpdatePreferences(false, true, "https://updates.example.com", 0L, "");
        assertTrue(!UpdateSchedule.shouldCheck(preferences, 86_400_000L), "disabled update check should not run");
    }

    private static void gatesDailyChecks() {
        UpdatePreferences neverChecked = new UpdatePreferences(true, true, "https://updates.example.com", 0L, "");
        UpdatePreferences recent = new UpdatePreferences(true, true, "https://updates.example.com", 10_000L, "");
        UpdatePreferences old = new UpdatePreferences(true, true, "https://updates.example.com", 10_000L, "");
        assertTrue(UpdateSchedule.shouldCheck(neverChecked, 11_000L), "never checked should run");
        assertTrue(!UpdateSchedule.shouldCheck(recent, 20_000L), "recent check should be skipped");
        assertTrue(UpdateSchedule.shouldCheck(old, 90_000_000L), "old check should run");
    }

    private static void downloadsAndVerifiesProfile() throws Exception {
        String profileJson = Files.readString(Path.of("server/update-service/public/v1/profiles/android-mlkit-v1/2026-06-29.json"));
        String manifestJson = manifestFor(profileJson);
        FakeHttpClient http = new FakeHttpClient();
        http.respond("https://updates.example.com/v1/manifest.json?platform=android&app_version=0.1.0&channel=stable", manifestJson);
        http.respond("https://updates.example.com/v1/profiles/android-mlkit-v1/2026-06-29.json", profileJson);

        UpdateCheckClient client = new UpdateCheckClient(http);
        UpdateCheckResult result = client.checkNow(new UpdateClientConfig(
                "https://updates.example.com",
                "android",
                "0.1.0",
                "stable",
                "android-mlkit-v1",
                true,
                true
        ));

        assertTrue(result.success(), "update check should succeed");
        assertEquals("android-mlkit-v1-2026-06-29", result.profile().profileId(), "profile id");
        assertEquals("2026.06.29", result.asset().version(), "asset version");
        assertTrue(result.checkedAtMs() > 0L, "checked time recorded");
    }

    private static void rejectsHashMismatch() throws Exception {
        String profileJson = Files.readString(Path.of("server/update-service/public/v1/profiles/android-mlkit-v1/2026-06-29.json"));
        String manifestJson = manifestWithSha("000000");
        FakeHttpClient http = new FakeHttpClient();
        http.respond("https://updates.example.com/v1/manifest.json?platform=android&app_version=0.1.0&channel=stable", manifestJson);
        http.respond("https://updates.example.com/v1/profiles/android-mlkit-v1/2026-06-29.json", profileJson);

        UpdateCheckClient client = new UpdateCheckClient(http);
        UpdateCheckResult result = client.checkNow(new UpdateClientConfig(
                "https://updates.example.com",
                "android",
                "0.1.0",
                "stable",
                "android-mlkit-v1",
                true,
                true
        ));

        assertTrue(!result.success(), "hash mismatch should fail safely");
        assertTrue(result.errorMessage().contains("SHA-256"), "hash error should be visible");
    }

    private static String manifestFor(String profileJson) {
        return manifestWithSha(UpdateVerifier.sha256Hex(profileJson.getBytes(StandardCharsets.UTF_8)));
    }

    private static String manifestWithSha(String sha256) {
        return "{"
                + "\"schema_version\":1,"
                + "\"channel\":\"stable\","
                + "\"generated_at\":\"2026-06-29T00:00:00Z\","
                + "\"assets\":[{"
                + "\"id\":\"android-mlkit-profile-stable\","
                + "\"kind\":\"profile\","
                + "\"engine\":\"android-mlkit-v1\","
                + "\"platform\":\"android\","
                + "\"version\":\"2026.06.29\","
                + "\"min_app_version\":\"0.1.0\","
                + "\"url\":\"/v1/profiles/android-mlkit-v1/2026-06-29.json\","
                + "\"sha256\":\"" + sha256 + "\","
                + "\"signature\":\"\","
                + "\"size_bytes\":821"
                + "}]}";
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static final class FakeHttpClient implements UpdateHttpClient {
        private final Map<String, byte[]> responses = new HashMap<>();

        private void respond(String url, String body) {
            responses.put(url, body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public byte[] get(String url) {
            byte[] response = responses.get(url);
            if (response == null) {
                throw new IllegalArgumentException("unexpected url: " + url);
            }
            return response;
        }
    }
}
