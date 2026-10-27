import com.vocagate.desktop.app.KeyBindingDisplayField;

import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.text.JTextComponent;

public final class KeyBindingDisplayFieldSmokeTest {
    public static void main(String[] args) {
        testDisplayFieldIsNotTextEditableOrSelectable();
        testClickRequestsRebindOnlyWhenEnabled();
        System.out.println("Key binding display field smoke tests passed");
    }

    private static void testDisplayFieldIsNotTextEditableOrSelectable() {
        KeyBindingDisplayField field = new KeyBindingDisplayField("Alt+V");

        assertTrue(
                !JTextComponent.class.isAssignableFrom(field.getClass()),
                "display field should not be a text component"
        );
        assertTrue(!field.isFocusable(), "display field should not show a text caret");
        assertTrue(field.getText().equals("Alt+V"), "display field text");

        field.setText("Ctrl+Shift+M");
        assertTrue(field.getText().equals("Ctrl+Shift+M"), "display field text should update programmatically");
    }

    private static void testClickRequestsRebindOnlyWhenEnabled() {
        KeyBindingDisplayField field = new KeyBindingDisplayField("Alt+V");
        AtomicInteger requests = new AtomicInteger();
        field.setOnRequestRebind(requests::incrementAndGet);

        field.dispatchEvent(mouseClicked(field));
        assertTrue(requests.get() == 1, "enabled click should request rebind");

        field.setEnabled(false);
        field.dispatchEvent(mouseClicked(field));
        assertTrue(requests.get() == 1, "disabled click should not request rebind");
    }

    private static MouseEvent mouseClicked(KeyBindingDisplayField field) {
        return new MouseEvent(
                field,
                MouseEvent.MOUSE_CLICKED,
                1000L,
                0,
                12,
                8,
                1,
                false
        );
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
