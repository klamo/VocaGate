import java.nio.file.Files;
import java.nio.file.Path;

public final class AndroidUpdateSettingsPageSmokeTest {
    public static void main(String[] args) throws Exception {
        manifestDeclaresUpdateSettingsActivity();
        mainActivityUsesSecondaryUpdateSettingsPage();
        updateSettingsActivityContainsExpectedControls();
        System.out.println("AndroidUpdateSettingsPageSmokeTest passed");
    }

    private static void manifestDeclaresUpdateSettingsActivity() throws Exception {
        String manifest = Files.readString(Path.of("app/src/main/AndroidManifest.xml"));

        assertTrue(manifest.contains(".UpdateSettingsActivity"), "manifest should declare UpdateSettingsActivity");
        assertTrue(manifest.contains("android:exported=\"false\""), "secondary settings page should not be exported");
    }

    private static void mainActivityUsesSecondaryUpdateSettingsPage() throws Exception {
        String mainActivity = Files.readString(Path.of("app/src/main/java/com/vocagate/demo/MainActivity.java"));

        assertTrue(mainActivity.contains("openUpdateSettings"), "main activity should have an update settings launcher");
        assertTrue(mainActivity.contains("UpdateSettingsActivity.class"), "launcher should open UpdateSettingsActivity");
        assertFalse(mainActivity.contains("addUpdateSettings(settingsPanel)"), "main settings panel should not embed update settings");
    }

    private static void updateSettingsActivityContainsExpectedControls() throws Exception {
        String activity = Files.readString(Path.of("app/src/main/java/com/vocagate/demo/UpdateSettingsActivity.java"));

        assertTrue(activity.contains("更新服务器 URL"), "secondary page should expose update server URL");
        assertTrue(activity.contains("立即检查算法更新"), "secondary page should expose manual check button");
        assertTrue(activity.contains("匿名使用统计"), "secondary page should expose anonymous stats toggle");
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
