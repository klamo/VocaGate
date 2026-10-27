package com.vocagate.desktop.core;

import java.util.Locale;

public final class GateProtocolParser {
    private GateProtocolParser() {
    }

    public static GateMessage parseLine(String line) {
        if (line == null) {
            throw new IllegalArgumentException("line is null");
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("line is empty");
        }

        String[] parts = trimmed.split("\\s+");
        String command = parts[0].toUpperCase(Locale.US);
        if ("VOCAGATE".equals(command)) {
            return GateMessage.hello();
        }
        if ("STATE".equals(command)) {
            return parseState(parts);
        }
        if ("PING".equals(command)) {
            return parsePing(parts);
        }
        if ("PAIR".equals(command)) {
            return parsePair(parts);
        }
        throw new IllegalArgumentException("unknown command: " + parts[0]);
    }

    private static GateMessage parseState(String[] parts) {
        if (parts.length < 4) {
            throw new IllegalArgumentException("STATE requires state, time, and score");
        }
        boolean open = parseOpen(parts[1]);
        long androidTimeMs = Long.parseLong(parts[2]);
        float score = Float.parseFloat(parts[3]);
        return GateMessage.state(parts[1].toUpperCase(Locale.US), open, androidTimeMs, score);
    }

    private static GateMessage parsePing(String[] parts) {
        if (parts.length < 3) {
            throw new IllegalArgumentException("PING requires time and state");
        }
        long androidTimeMs = Long.parseLong(parts[1]);
        boolean open = parseOpen(parts[2]);
        return GateMessage.heartbeat(open, androidTimeMs);
    }

    private static GateMessage parsePair(String[] parts) {
        if (parts.length < 2) {
            throw new IllegalArgumentException("PAIR requires a code");
        }
        return GateMessage.pairing(parts[1]);
    }

    private static boolean parseOpen(String value) {
        String normalized = value.toUpperCase(Locale.US);
        if ("OPEN".equals(normalized) || "PRE_OPEN".equals(normalized)) {
            return true;
        }
        if ("BLOCKED".equals(normalized) || "CLOSED".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("unknown gate state: " + value);
    }
}
