package com.vocagate.demo.pc;

import java.util.Locale;

public final class PcLinkProtocol {
    private PcLinkProtocol() {
    }

    public static String helloLine() {
        return "VOCAGATE 1";
    }

    public static String stateLine(boolean open, long androidTimeMs, float score) {
        return String.format(
                Locale.US,
                "STATE %s %d %.2f",
                open ? "OPEN" : "BLOCKED",
                androidTimeMs,
                score
        );
    }

    public static String preOpenLine(long androidTimeMs, float score) {
        return String.format(Locale.US, "STATE PRE_OPEN %d %.2f", androidTimeMs, score);
    }

    public static String pairLine(String pairingCode) {
        return "PAIR " + pairingCode.trim();
    }

    public static String pingLine(long androidTimeMs, boolean open) {
        return String.format(
                Locale.US,
                "PING %d %s",
                androidTimeMs,
                open ? "OPEN" : "BLOCKED"
        );
    }
}
