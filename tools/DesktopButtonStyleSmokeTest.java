import com.vocagate.desktop.app.DesktopButtonStyles;

import java.awt.Color;

import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;

public final class DesktopButtonStyleSmokeTest {
    public static void main(String[] args) {
        testPrimaryButtonKeepsTextPaintable();
        System.out.println("Desktop button style smoke tests passed");
    }

    private static void testPrimaryButtonKeepsTextPaintable() {
        JButton button = new JButton("启动");
        DesktopButtonStyles.applyPrimary(button);

        assertTrue(button.getText().equals("启动"), "button text should remain");
        assertTrue(button.getUI() instanceof BasicButtonUI, "button should use BasicButtonUI for predictable painting");
        assertTrue(button.isOpaque(), "button should paint its background");
        assertTrue(button.isContentAreaFilled(), "button content area should be filled");
        assertTrue(button.getForeground().equals(Color.WHITE), "primary text should be white");
        assertTrue(!button.getBackground().equals(Color.WHITE), "primary background should not be white");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
