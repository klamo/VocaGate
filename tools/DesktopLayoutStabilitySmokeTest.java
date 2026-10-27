import com.vocagate.desktop.app.DesktopFixedTextLabel;
import com.vocagate.desktop.app.DesktopStatusPill;

import java.awt.Dimension;

public final class DesktopLayoutStabilitySmokeTest {
    public static void main(String[] args) {
        testStatusPillKeepsFixedSizeWhenTextChanges();
        testFixedTextLabelKeepsFixedSizeWhenTextChanges();
        System.out.println("Desktop layout stability smoke tests passed");
    }

    private static void testStatusPillKeepsFixedSizeWhenTextChanges() {
        DesktopStatusPill pill = new DesktopStatusPill("Stopped");
        Dimension preferred = pill.getPreferredSize();
        Dimension minimum = pill.getMinimumSize();
        Dimension maximum = pill.getMaximumSize();

        pill.setText("Listening on 0.0.0.0 27183 with a very long status message");

        assertEqual(preferred, pill.getPreferredSize(), "pill preferred size should stay fixed");
        assertEqual(minimum, pill.getMinimumSize(), "pill minimum size should stay fixed");
        assertEqual(maximum, pill.getMaximumSize(), "pill maximum size should stay fixed");
    }

    private static void testFixedTextLabelKeepsFixedSizeWhenTextChanges() {
        DesktopFixedTextLabel label = new DesktopFixedTextLabel("-", new Dimension(180, 32));
        Dimension preferred = label.getPreferredSize();
        Dimension minimum = label.getMinimumSize();
        Dimension maximum = label.getMaximumSize();

        label.setText("LOCAL BLOCKED score=0.00 motion=0.00 fps=29.9 roi=OpenCV fallback");

        assertEqual(preferred, label.getPreferredSize(), "value preferred size should stay fixed");
        assertEqual(minimum, label.getMinimumSize(), "value minimum size should stay fixed");
        assertEqual(maximum, label.getMaximumSize(), "value maximum size should stay fixed");
        assertTrue(label.getToolTipText().contains("LOCAL BLOCKED"), "full text should remain available as tooltip");
    }

    private static void assertEqual(Dimension expected, Dimension actual, String message) {
        assertTrue(expected.equals(actual), message + ": expected " + expected + " but got " + actual);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
