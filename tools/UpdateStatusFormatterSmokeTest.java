import com.vocagate.core.GateConfig;
import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.core.update.UpdatePreferences;
import com.vocagate.demo.core.UpdateStatusFormatter;

public final class UpdateStatusFormatterSmokeTest {
    public static void main(String[] args) {
        disabledStatusIsExplicit();
        cachedProfileShowsVersionAndUpdateTime();
        missingProfileShowsBuiltInDefault();
        System.out.println("UpdateStatusFormatterSmokeTest passed");
    }

    private static void disabledStatusIsExplicit() {
        UpdatePreferences preferences = new UpdatePreferences(
                false,
                true,
                UpdatePreferences.DEFAULT_UPDATE_SERVER_URL,
                0L,
                ""
        );

        assertEquals("检查更新已关闭", UpdateStatusFormatter.statusText(preferences), "disabled status");
    }

    private static void cachedProfileShowsVersionAndUpdateTime() {
        AlgorithmProfile profile = new AlgorithmProfile(
                1,
                "android-mlkit-v1-2026-06-29",
                "android-mlkit-v1",
                "2026.06.29",
                "0.1.0",
                new GateConfig(),
                AlgorithmProfile.ScoreWeights.defaults(),
                AlgorithmProfile.FirstWordConfig.defaults()
        );

        String text = UpdateStatusFormatter.profileText(profile, 1782705600000L);

        assertTrue(text.contains("算法版本 2026.06.29"), "profile version should be shown");
        assertTrue(text.contains("更新时间 2026-06-29"), "update date should be shown");
    }

    private static void missingProfileShowsBuiltInDefault() {
        String text = UpdateStatusFormatter.profileText(null, 0L);

        assertEquals("算法版本 内置默认 · 更新时间 -", text, "default profile status");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }
}
