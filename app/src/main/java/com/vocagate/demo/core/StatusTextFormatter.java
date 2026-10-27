package com.vocagate.demo.core;

import com.vocagate.core.GateState;

import java.util.Locale;

public final class StatusTextFormatter {
    private StatusTextFormatter() {
    }

    public static String format(
            boolean compact,
            String label,
            String pcLinkStatus,
            GateState state,
            boolean gateOpen,
            float score,
            float mouthRatio,
            float rhythm,
            float fps
    ) {
        String safeLabel = label == null ? "-" : label;
        String safePcStatus = pcLinkStatus == null ? "PC: off" : pcLinkStatus;
        if (compact) {
            return String.format(
                    Locale.US,
                    "VocaGate\n%s\n%s\n%s  %s\nScore %.2f  FPS %.1f\nMouth %.3f  R %.2f",
                    safeLabel,
                    safePcStatus,
                    state,
                    gateOpen ? "OPEN" : "BLOCKED",
                    score,
                    fps,
                    mouthRatio,
                    rhythm
            );
        }

        String gateLabel = gateOpen ? "OPEN / 放行麦克风" : "BLOCKED / 阻断麦克风";
        return String.format(
                Locale.US,
                "VocaGate Android Demo\n%s\n%s\nState: %s\nGate: %s\nScore: %.2f   Mouth: %.3f   Rhythm: %.2f   FPS: %.1f",
                safeLabel,
                safePcStatus,
                state,
                gateLabel,
                score,
                mouthRatio,
                rhythm,
                fps
        );
    }
}
