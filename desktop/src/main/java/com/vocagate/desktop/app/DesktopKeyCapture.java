package com.vocagate.desktop.app;

import com.vocagate.desktop.core.KeyStrokeSpec;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DesktopKeyCapture {
    private DesktopKeyCapture() {
    }

    public static Optional<KeyStrokeSpec> capture(KeyEvent event) {
        if (event == null || event.getID() != KeyEvent.KEY_PRESSED || isModifierKey(event.getKeyCode())) {
            return Optional.empty();
        }
        return buildSpec(event.getKeyCode(), event.getModifiersEx());
    }

    public static Optional<KeyStrokeSpec> captureReleasedModifier(KeyEvent event) {
        if (event == null || event.getID() != KeyEvent.KEY_RELEASED || !isModifierKey(event.getKeyCode())) {
            return Optional.empty();
        }
        return buildSpec(event.getKeyCode(), 0);
    }

    public static boolean isModifierKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_SHIFT:
            case KeyEvent.VK_ALT:
            case KeyEvent.VK_META:
                return true;
            default:
                return false;
        }
    }

    private static Optional<KeyStrokeSpec> buildSpec(int keyCode, int modifiersEx) {
        String keyToken = tokenForKeyCode(keyCode);
        if (keyToken.isEmpty()) {
            return Optional.empty();
        }

        List<String> tokens = new ArrayList<>();
        if (keyCode != KeyEvent.VK_CONTROL && (modifiersEx & KeyEvent.CTRL_DOWN_MASK) != 0) {
            tokens.add("CTRL");
        }
        if (keyCode != KeyEvent.VK_SHIFT && (modifiersEx & KeyEvent.SHIFT_DOWN_MASK) != 0) {
            tokens.add("SHIFT");
        }
        if (keyCode != KeyEvent.VK_ALT && (modifiersEx & KeyEvent.ALT_DOWN_MASK) != 0) {
            tokens.add("ALT");
        }
        if (keyCode != KeyEvent.VK_META && (modifiersEx & KeyEvent.META_DOWN_MASK) != 0) {
            tokens.add("META");
        }
        tokens.add(keyToken);
        return Optional.of(KeyStrokeSpec.parse(String.join("+", tokens)));
    }

    private static String tokenForKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
            return String.valueOf((char) ('A' + keyCode - KeyEvent.VK_A));
        }
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
            return String.valueOf((char) ('0' + keyCode - KeyEvent.VK_0));
        }
        if (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12) {
            return "F" + (keyCode - KeyEvent.VK_F1 + 1);
        }
        if (keyCode >= KeyEvent.VK_F13 && keyCode <= KeyEvent.VK_F24) {
            return "F" + (keyCode - KeyEvent.VK_F13 + 13);
        }
        if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9) {
            return "NUMPAD" + (keyCode - KeyEvent.VK_NUMPAD0);
        }

        switch (keyCode) {
            case KeyEvent.VK_CONTROL:
                return "CTRL";
            case KeyEvent.VK_SHIFT:
                return "SHIFT";
            case KeyEvent.VK_ALT:
                return "ALT";
            case KeyEvent.VK_META:
                return "META";
            case KeyEvent.VK_SPACE:
                return "SPACE";
            case KeyEvent.VK_ENTER:
                return "ENTER";
            case KeyEvent.VK_ESCAPE:
                return "ESC";
            case KeyEvent.VK_TAB:
                return "TAB";
            case KeyEvent.VK_BACK_SPACE:
                return "BACKSPACE";
            case KeyEvent.VK_DELETE:
                return "DELETE";
            case KeyEvent.VK_INSERT:
                return "INSERT";
            case KeyEvent.VK_HOME:
                return "HOME";
            case KeyEvent.VK_END:
                return "END";
            case KeyEvent.VK_PAGE_UP:
                return "PAGEUP";
            case KeyEvent.VK_PAGE_DOWN:
                return "PAGEDOWN";
            case KeyEvent.VK_UP:
                return "UP";
            case KeyEvent.VK_DOWN:
                return "DOWN";
            case KeyEvent.VK_LEFT:
                return "LEFT";
            case KeyEvent.VK_RIGHT:
                return "RIGHT";
            case KeyEvent.VK_CAPS_LOCK:
                return "CAPSLOCK";
            case KeyEvent.VK_BACK_QUOTE:
                return "BACKQUOTE";
            case KeyEvent.VK_MINUS:
                return "MINUS";
            case KeyEvent.VK_EQUALS:
                return "EQUALS";
            case KeyEvent.VK_OPEN_BRACKET:
                return "LEFTBRACKET";
            case KeyEvent.VK_CLOSE_BRACKET:
                return "RIGHTBRACKET";
            case KeyEvent.VK_BACK_SLASH:
                return "BACKSLASH";
            case KeyEvent.VK_SEMICOLON:
                return "SEMICOLON";
            case KeyEvent.VK_QUOTE:
                return "QUOTE";
            case KeyEvent.VK_COMMA:
                return "COMMA";
            case KeyEvent.VK_PERIOD:
                return "PERIOD";
            case KeyEvent.VK_SLASH:
                return "SLASH";
            default:
                return "";
        }
    }
}
