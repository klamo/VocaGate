import com.vocagate.desktop.app.DesktopUsbReverseSetup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DesktopUsbReverseSetupSmokeTest {
    public static void main(String[] args) {
        testMacCandidatesPreferExplicitAdbThenSdkThenPath();
        testPrepareCreatesReverseWhenUsbDeviceIsPresent();
        testPrepareSkipsCreateWhenReverseAlreadyExists();
        testPrepareReportsNoAuthorizedDevice();
        System.out.println("Desktop USB reverse setup smoke tests passed");
    }

    private static void testMacCandidatesPreferExplicitAdbThenSdkThenPath() {
        Map<String, String> env = new HashMap<>();
        env.put("ADB", "/custom/adb");
        env.put("ANDROID_HOME", "/opt/android-sdk");

        List<String> candidates = DesktopUsbReverseSetup.adbCandidates(env, "Mac OS X");

        assertTrue(candidates.get(0).equals("/custom/adb"), "explicit ADB should be first");
        assertTrue(candidates.contains("/opt/android-sdk/platform-tools/adb"), "ANDROID_HOME platform-tools adb should be included");
        assertTrue(candidates.get(candidates.size() - 1).equals("adb"), "PATH adb should be fallback");
    }

    private static void testPrepareCreatesReverseWhenUsbDeviceIsPresent() {
        FakeRunner runner = new FakeRunner();
        runner.enqueue(0, "List of devices attached\nfc8a3d7\tdevice\n");
        runner.enqueue(0, "");
        runner.enqueue(1, "listener 'tcp:27183' not found");
        runner.enqueue(0, "");
        DesktopUsbReverseSetup setup = new DesktopUsbReverseSetup(runner, minimalEnv(), "Mac OS X");

        DesktopUsbReverseSetup.Result result = setup.prepare(27183);

        assertTrue(result.ready(), "reverse setup should be ready after create succeeds");
        assertTrue(result.message().contains("USB reverse 已就绪"), "success message should mention ready reverse");
        assertTrue(runner.commands.size() == 4, "devices, list, remove and create should run");
        assertCommand(runner.commands.get(3), "adb", "reverse", "tcp:27183", "tcp:27183");
    }

    private static void testPrepareSkipsCreateWhenReverseAlreadyExists() {
        FakeRunner runner = new FakeRunner();
        runner.enqueue(0, "List of devices attached\nfc8a3d7\tdevice\n");
        runner.enqueue(0, "UsbFfs tcp:27183 tcp:27183\n");
        DesktopUsbReverseSetup setup = new DesktopUsbReverseSetup(runner, minimalEnv(), "Mac OS X");

        DesktopUsbReverseSetup.Result result = setup.prepare(27183);

        assertTrue(result.ready(), "existing reverse should be ready");
        assertTrue(runner.commands.size() == 2, "existing reverse should not be recreated");
        assertTrue(result.message().contains("已存在"), "existing reverse message should be explicit");
    }

    private static void testPrepareReportsNoAuthorizedDevice() {
        FakeRunner runner = new FakeRunner();
        runner.enqueue(0, "List of devices attached\nfc8a3d7\tunauthorized\n");
        DesktopUsbReverseSetup setup = new DesktopUsbReverseSetup(runner, minimalEnv(), "Mac OS X");

        DesktopUsbReverseSetup.Result result = setup.prepare(27183);

        assertTrue(!result.ready(), "unauthorized device should not be ready");
        assertTrue(result.message().contains("未检测到已授权 USB 设备"), "message should explain missing authorized USB device");
        assertTrue(runner.commands.size() == 1, "reverse commands should not run without authorized device");
    }

    private static Map<String, String> minimalEnv() {
        return new HashMap<>();
    }

    private static void assertCommand(List<String> actual, String... expected) {
        assertTrue(actual.size() == expected.length, "command length should match");
        for (int i = 0; i < expected.length; i++) {
            assertTrue(actual.get(i).equals(expected[i]), "command part " + i + " should be " + expected[i] + " but got " + actual.get(i));
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeRunner implements DesktopUsbReverseSetup.CommandRunner {
        private final List<DesktopUsbReverseSetup.CommandResult> results = new ArrayList<>();
        private final List<List<String>> commands = new ArrayList<>();

        private void enqueue(int exitCode, String output) {
            results.add(new DesktopUsbReverseSetup.CommandResult(exitCode, output));
        }

        @Override
        public DesktopUsbReverseSetup.CommandResult run(List<String> command) {
            commands.add(new ArrayList<>(command));
            if (results.isEmpty()) {
                throw new AssertionError("unexpected command: " + command);
            }
            return results.remove(0);
        }
    }
}
