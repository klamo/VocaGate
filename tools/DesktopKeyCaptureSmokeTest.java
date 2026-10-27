import com.vocagate.desktop.app.DesktopKeyCapture;
import com.vocagate.desktop.core.KeyStrokeSpec;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Optional;

public final class DesktopKeyCaptureSmokeTest {
    public static void main(String[] args) {
        testCapturesModifierCombo();
        testIgnoresPureModifierKeys();
        testCapturesCommonSingleKeys();
        testExtendedFunctionKeysUseJavaConstants();
        testCapturedDisplayNamesRemainParseable();
        System.out.println("Desktop key capture smoke tests passed");
    }

    private static void testCapturesModifierCombo() {
        Optional<KeyStrokeSpec> captured = DesktopKeyCapture.capture(keyPressed(
                KeyEvent.VK_M,
                'M',
                KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK
        ));

        assertTrue(captured.isPresent(), "Ctrl+Shift+M should be captured");
        assertTrue(captured.get().displayName().equals("Ctrl+Shift+M"), "combo display name");
        assertTrue(
                Arrays.equals(captured.get().keyCodes(), new int[]{
                        KeyEvent.VK_CONTROL,
                        KeyEvent.VK_SHIFT,
                        KeyEvent.VK_M
                }),
                "combo key code order"
        );
    }

    private static void testIgnoresPureModifierKeys() {
        assertTrue(
                !DesktopKeyCapture.capture(keyPressed(KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED, KeyEvent.CTRL_DOWN_MASK)).isPresent(),
                "pure Ctrl should wait for a non-modifier key"
        );
        assertTrue(
                !DesktopKeyCapture.capture(keyPressed(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED, KeyEvent.SHIFT_DOWN_MASK)).isPresent(),
                "pure Shift should wait for a non-modifier key"
        );
    }

    private static void testCapturesCommonSingleKeys() {
        assertDisplayName("Space", KeyEvent.VK_SPACE, ' ', 0);
        assertDisplayName("Alt+Tab", KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED, KeyEvent.ALT_DOWN_MASK);
        assertDisplayName("Enter", KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED, 0);
        assertDisplayName("Esc", KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED, 0);
        assertDisplayName("Delete", KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED, 0);
        assertDisplayName("Left", KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED, 0);
        assertDisplayName("PageDown", KeyEvent.VK_PAGE_DOWN, KeyEvent.CHAR_UNDEFINED, 0);
    }

    private static void testCapturedDisplayNamesRemainParseable() {
        KeyStrokeSpec captured = DesktopKeyCapture
                .capture(keyPressed(KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED, KeyEvent.CTRL_DOWN_MASK))
                .orElseThrow(() -> new AssertionError("Ctrl+Tab should be captured"));

        KeyStrokeSpec parsedAgain = KeyStrokeSpec.parse(captured.displayName());
        assertTrue(parsedAgain.displayName().equals("Ctrl+Tab"), "captured display name should parse again");
        assertTrue(
                Arrays.equals(parsedAgain.keyCodes(), new int[]{KeyEvent.VK_CONTROL, KeyEvent.VK_TAB}),
                "captured display codes should parse again"
        );
    }

    private static void testExtendedFunctionKeysUseJavaConstants() {
        KeyStrokeSpec f13 = KeyStrokeSpec.parse("F13");
        assertTrue(f13.keyCodes()[0] == KeyEvent.VK_F13, "F13 should use Java extended key code");

        Optional<KeyStrokeSpec> captured = DesktopKeyCapture.capture(keyPressed(
                KeyEvent.VK_F13,
                KeyEvent.CHAR_UNDEFINED,
                KeyEvent.SHIFT_DOWN_MASK
        ));
        assertTrue(captured.isPresent(), "Shift+F13 should be captured");
        assertTrue(captured.get().displayName().equals("Shift+F13"), "Shift+F13 display name");
    }

    private static void assertDisplayName(String expected, int keyCode, char keyChar, int modifiersEx) {
        Optional<KeyStrokeSpec> captured = DesktopKeyCapture.capture(keyPressed(keyCode, keyChar, modifiersEx));
        assertTrue(captured.isPresent(), expected + " should be captured");
        assertTrue(captured.get().displayName().equals(expected), expected + " display name");
    }

    private static KeyEvent keyPressed(int keyCode, char keyChar, int modifiersEx) {
        return new KeyEvent(
                new Canvas(),
                KeyEvent.KEY_PRESSED,
                1000L,
                modifiersEx,
                keyCode,
                keyChar
        );
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
