package com.vocagate.core.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleJsonParser {
    private final String text;
    private int index = 0;

    private SimpleJsonParser(String text) {
        this.text = text == null ? "" : text;
    }

    static Map<String, Object> parseObject(String json) {
        Object value = new SimpleJsonParser(json).parseRoot();
        if (value instanceof Map) {
            return castObject(value);
        }
        throw new UpdateProtocolException("JSON root must be an object");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castObject(Object value) {
        return (Map<String, Object>) value;
    }

    private Object parseRoot() {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        if (!isEnd()) {
            throw error("unexpected trailing data");
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (isEnd()) {
            throw error("unexpected end of JSON");
        }
        char ch = peek();
        if (ch == '{') {
            return parseObjectValue();
        }
        if (ch == '[') {
            return parseArray();
        }
        if (ch == '"') {
            return parseString();
        }
        if (ch == 't') {
            expectLiteral("true");
            return Boolean.TRUE;
        }
        if (ch == 'f') {
            expectLiteral("false");
            return Boolean.FALSE;
        }
        if (ch == 'n') {
            expectLiteral("null");
            return null;
        }
        if (ch == '-' || Character.isDigit(ch)) {
            return parseNumber();
        }
        throw error("unexpected character '" + ch + "'");
    }

    private Map<String, Object> parseObjectValue() {
        expect('{');
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (tryConsume('}')) {
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            object.put(key, parseValue());
            skipWhitespace();
            if (tryConsume('}')) {
                return object;
            }
            expect(',');
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> values = new ArrayList<>();
        skipWhitespace();
        if (tryConsume(']')) {
            return values;
        }
        while (true) {
            values.add(parseValue());
            skipWhitespace();
            if (tryConsume(']')) {
                return values;
            }
            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (!isEnd()) {
            char ch = next();
            if (ch == '"') {
                return builder.toString();
            }
            if (ch == '\\') {
                builder.append(parseEscapedChar());
            } else {
                builder.append(ch);
            }
        }
        throw error("unterminated string");
    }

    private char parseEscapedChar() {
        if (isEnd()) {
            throw error("unterminated escape");
        }
        char ch = next();
        switch (ch) {
            case '"':
            case '\\':
            case '/':
                return ch;
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'u':
                return parseUnicodeEscape();
            default:
                throw error("invalid escape: \\" + ch);
        }
    }

    private char parseUnicodeEscape() {
        if (index + 4 > text.length()) {
            throw error("short unicode escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char ch = next();
            int digit = Character.digit(ch, 16);
            if (digit < 0) {
                throw error("invalid unicode escape");
            }
            value = (value << 4) + digit;
        }
        return (char) value;
    }

    private Number parseNumber() {
        int start = index;
        tryConsume('-');
        consumeDigits();
        if (tryConsume('.')) {
            consumeDigits();
        }
        if (!isEnd() && (peek() == 'e' || peek() == 'E')) {
            next();
            if (!isEnd() && (peek() == '+' || peek() == '-')) {
                next();
            }
            consumeDigits();
        }
        String raw = text.substring(start, index);
        try {
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw error("invalid number: " + raw);
        }
    }

    private void consumeDigits() {
        int start = index;
        while (!isEnd() && Character.isDigit(peek())) {
            next();
        }
        if (start == index) {
            throw error("expected digit");
        }
    }

    private void expectLiteral(String literal) {
        if (!text.startsWith(literal, index)) {
            throw error("expected " + literal);
        }
        index += literal.length();
    }

    private void expect(char expected) {
        if (isEnd() || next() != expected) {
            throw error("expected '" + expected + "'");
        }
    }

    private boolean tryConsume(char expected) {
        if (!isEnd() && peek() == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (!isEnd() && Character.isWhitespace(peek())) {
            index++;
        }
    }

    private char peek() {
        return text.charAt(index);
    }

    private char next() {
        return text.charAt(index++);
    }

    private boolean isEnd() {
        return index >= text.length();
    }

    private UpdateProtocolException error(String message) {
        return new UpdateProtocolException(message + " at byte " + index);
    }
}
