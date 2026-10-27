import com.vocagate.desktop.app.DesktopLocalRecognitionLifecycle;

public final class DesktopLocalRecognitionLifecycleSmokeTest {
    public static void main(String[] args) {
        DesktopLocalRecognitionLifecycle lifecycle = new DesktopLocalRecognitionLifecycle();

        if (lifecycle.onCameraPreviewStarted()) {
            throw new AssertionError("camera preview alone must not start recognition");
        }
        if (!lifecycle.onDesktopStarted()) {
            throw new AssertionError("recognition should start when desktop starts with camera ready");
        }
        if (lifecycle.onDesktopStopped()) {
            throw new AssertionError("recognition should stop when desktop stops");
        }
        if (!lifecycle.onDesktopStarted()) {
            throw new AssertionError("recognition should restart when desktop starts again");
        }
        if (lifecycle.onCameraPreviewStopped()) {
            throw new AssertionError("recognition should stop when camera preview stops");
        }

        System.out.println("Desktop local recognition lifecycle smoke passed");
    }
}
