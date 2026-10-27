package com.vocagate.desktop.core;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KeyStrokeSpec {
    private final String displayName;
    private final int[] keyCodes;

    private KeyStrokeSpec(String displayName, int[] keyCodes) {
        this.displayName = displayName;
        this.keyCodes = keyCodes;
    }

    public static KeyStrokeSpec parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("key spec is empty");
        }

        String[] tokens = raw.trim().split("\\+");
        List<Integer> codes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String token : tokens) {
            String normalized = token.trim().toUpperCase(Locale.US);
            if (normalized.isEmpty()) {
                continue;
            }
            codes.add(keyCodeFor(normalized));
            names.add(displayNameFor(normalized));
        }

        if (codes.isEmpty()) {
            throw new IllegalArgumentException("key spec has no keys: " + raw);
        }

        int[] codeArray = new int[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            codeArray[i] = codes.get(i);
        }
        return new KeyStrokeSpec(String.join("+", names), codeArray);
    }

    public String displayName() {
        return displayName;
    }

    public int[] keyCodes() {
        return keyCodes.clone();
    }

    private static int keyCodeFor(String token) {
        switch (token) {
            case "CTRL":
            case "CONTROL":
                return KeyEvent.VK_CONTROL;
            case "SHIFT":
                return KeyEvent.VK_SHIFT;
            case "ALT":
            case "OPTION":
                return KeyEvent.VK_ALT;
            case "CMD":
            case "COMMAND":
            case "META":
                return KeyEvent.VK_META;
            case "SPACE":
                return KeyEvent.VK_SPACE;
            case "ENTER":
                return KeyEvent.VK_ENTER;
            case "ESC":
            case "ESCAPE":
                return KeyEvent.VK_ESCAPE;
            case "TAB":
                return KeyEvent.VK_TAB;
            case "BACKSPACE":
            case "BACK_SPACE":
                return KeyEvent.VK_BACK_SPACE;
            case "DELETE":
            case "DEL":
                return KeyEvent.VK_DELETE;
            case "INSERT":
            case "INS":
                return KeyEvent.VK_INSERT;
            case "HOME":
                return KeyEvent.VK_HOME;
            case "END":
                return KeyEvent.VK_END;
            case "PAGEUP":
            case "PAGE_UP":
                return KeyEvent.VK_PAGE_UP;
            case "PAGEDOWN":
            case "PAGE_DOWN":
                return KeyEvent.VK_PAGE_DOWN;
            case "UP":
                return KeyEvent.VK_UP;
            case "DOWN":
                return KeyEvent.VK_DOWN;
            case "LEFT":
                return KeyEvent.VK_LEFT;
            case "RIGHT":
                return KeyEvent.VK_RIGHT;
            case "CAPSLOCK":
            case "CAPS_LOCK":
                return KeyEvent.VK_CAPS_LOCK;
            case "BACKQUOTE":
            case "BACK_QUOTE":
                return KeyEvent.VK_BACK_QUOTE;
            case "MINUS":
                return KeyEvent.VK_MINUS;
            case "EQUALS":
                return KeyEvent.VK_EQUALS;
            case "LEFTBRACKET":
            case "LEFT_BRACKET":
            case "OPENBRACKET":
            case "OPEN_BRACKET":
                return KeyEvent.VK_OPEN_BRACKET;
            case "RIGHTBRACKET":
            case "RIGHT_BRACKET":
            case "CLOSEBRACKET":
            case "CLOSE_BRACKET":
                return KeyEvent.VK_CLOSE_BRACKET;
            case "BACKSLASH":
            case "BACK_SLASH":
                return KeyEvent.VK_BACK_SLASH;
            case "SEMICOLON":
                return KeyEvent.VK_SEMICOLON;
            case "QUOTE":
                return KeyEvent.VK_QUOTE;
            case "COMMA":
                return KeyEvent.VK_COMMA;
            case "PERIOD":
                return KeyEvent.VK_PERIOD;
            case "SLASH":
                return KeyEvent.VK_SLASH;
            default:
                return keyCodeFromFunctionOrLetter(token);
        }
    }

    private static int keyCodeFromFunctionOrLetter(String token) {
        if (token.matches("F\\d{1,2}")) {
            int number = Integer.parseInt(token.substring(1));
            if (number >= 1 && number <= 12) {
                return KeyEvent.VK_F1 + number - 1;
            }
            if (number >= 13 && number <= 24) {
                return KeyEvent.VK_F13 + number - 13;
            }
        }
        if (token.matches("NUMPAD\\d")) {
            int number = Integer.parseInt(token.substring("NUMPAD".length()));
            return KeyEvent.VK_NUMPAD0 + number;
        }
        if (token.length() == 1) {
            char ch = token.charAt(0);
            if (ch >= 'A' && ch <= 'Z') {
                return KeyEvent.VK_A + ch - 'A';
            }
            if (ch >= '0' && ch <= '9') {
                return KeyEvent.VK_0 + ch - '0';
            }
        }
        throw new IllegalArgumentException("unsupported key: " + token);
    }

    private static String displayNameFor(String token) {
        switch (token) {
            case "CTRL":
            case "CONTROL":
                return "Ctrl";
            case "SHIFT":
                return "Shift";
            case "ALT":
            case "OPTION":
                return "Alt";
            case "CMD":
            case "COMMAND":
            case "META":
                return "Meta";
            case "SPACE":
                return "Space";
            case "ENTER":
                return "Enter";
            case "ESC":
            case "ESCAPE":
                return "Esc";
            case "TAB":
                return "Tab";
            case "BACKSPACE":
            case "BACK_SPACE":
                return "Backspace";
            case "DELETE":
            case "DEL":
                return "Delete";
            case "INSERT":
            case "INS":
                return "Insert";
            case "HOME":
                return "Home";
            case "END":
                return "End";
            case "PAGEUP":
            case "PAGE_UP":
                return "PageUp";
            case "PAGEDOWN":
            case "PAGE_DOWN":
                return "PageDown";
            case "UP":
                return "Up";
            case "DOWN":
                return "Down";
            case "LEFT":
                return "Left";
            case "RIGHT":
                return "Right";
            case "CAPSLOCK":
            case "CAPS_LOCK":
                return "CapsLock";
            case "BACKQUOTE":
            case "BACK_QUOTE":
                return "Backquote";
            case "MINUS":
                return "Minus";
            case "EQUALS":
                return "Equals";
            case "LEFTBRACKET":
            case "LEFT_BRACKET":
            case "OPENBRACKET":
            case "OPEN_BRACKET":
                return "LeftBracket";
            case "RIGHTBRACKET":
            case "RIGHT_BRACKET":
            case "CLOSEBRACKET":
            case "CLOSE_BRACKET":
                return "RightBracket";
            case "BACKSLASH":
            case "BACK_SLASH":
                return "Backslash";
            case "SEMICOLON":
                return "Semicolon";
            case "QUOTE":
                return "Quote";
            case "COMMA":
                return "Comma";
            case "PERIOD":
                return "Period";
            case "SLASH":
                return "Slash";
            default:
                if (token.matches("NUMPAD\\d")) {
                    return "Numpad" + token.substring("NUMPAD".length());
                }
                return token;
        }
    }
}
