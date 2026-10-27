package com.vocagate.demo.core;

import com.vocagate.core.GateState;

import java.util.Locale;

public final class ShieldHudState {
    public enum Kind {
        BLOCKED,
        PRE_OPEN,
        OPEN,
        NO_FACE,
        PC_LOST
    }

    public static final int COLOR_BLOCKED = 0xFF334052;
    public static final int COLOR_PRE_OPEN = 0xFF1D8BA8;
    public static final int COLOR_OPEN = 0xFF2E8F4D;
    public static final int COLOR_NO_FACE = 0xFFB47A24;
    public static final int COLOR_PC_LOST = 0xFF9B3438;

    public final Kind kind;
    public final String label;
    public final String caption;
    public final int colorArgb;
    public final boolean flow;
    public final boolean pulse;

    private ShieldHudState(
            Kind kind,
            String label,
            String caption,
            int colorArgb,
            boolean flow,
            boolean pulse
    ) {
        this.kind = kind;
        this.label = label;
        this.caption = caption;
        this.colorArgb = colorArgb;
        this.flow = flow;
        this.pulse = pulse;
    }

    public static ShieldHudState from(
            String trackingLabel,
            String pcLinkStatus,
            GateState state,
            boolean gateOpen,
            float score
    ) {
        String safePcStatus = pcLinkStatus == null ? "" : pcLinkStatus;
        if (state == GateState.SUSPEND || isNoFaceLabel(trackingLabel)) {
            return new ShieldHudState(Kind.NO_FACE, "NO FACE", "Face lost", COLOR_NO_FACE, false, true);
        }

        String caption = channelLabel(safePcStatus) + " · " + scoreLabel(score);
        if (state == GateState.PRE_OPEN) {
            return new ShieldHudState(Kind.PRE_OPEN, "PRE", caption, COLOR_PRE_OPEN, false, true);
        }
        if (gateOpen || state == GateState.OPEN || state == GateState.HOLD) {
            return new ShieldHudState(Kind.OPEN, "OPEN", caption, COLOR_OPEN, false, true);
        }
        return new ShieldHudState(Kind.BLOCKED, "BLOCKED", caption, COLOR_BLOCKED, false, true);
    }

    private static boolean isPcExpectedButMissing(String pcLinkStatus) {
        String value = pcLinkStatus.toLowerCase(Locale.US);
        if (!value.startsWith("pc:")) {
            return false;
        }
        if (value.contains("off")) {
            return false;
        }
        return !value.contains("connected");
    }

    private static boolean isNoFaceLabel(String label) {
        if (label == null) {
            return false;
        }
        String value = label.toLowerCase(Locale.US);
        return value.contains("no face") || value.contains("lost") || value.contains("no mouth");
    }

    private static String channelLabel(String pcLinkStatus) {
        if (isPcExpectedButMissing(pcLinkStatus)) {
            return "PC lost";
        }
        String value = pcLinkStatus.toLowerCase(Locale.US);
        if (value.contains("wi-fi") || value.contains("wifi")) {
            return "Wi-Fi";
        }
        if (value.contains("usb")) {
            return "USB";
        }
        return "PC";
    }

    private static String scoreLabel(float score) {
        return String.format(Locale.US, "%.2f", Math.max(0.0f, Math.min(1.0f, score)));
    }
}
