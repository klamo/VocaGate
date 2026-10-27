package com.vocagate.desktop.core;

public final class GateMessage {
    public enum Type {
        STATE,
        HEARTBEAT,
        HELLO,
        PAIRING
    }

    private final Type type;
    private final boolean open;
    private final String gateLabel;
    private final long androidTimeMs;
    private final float score;
    private final String pairingCode;

    private GateMessage(Type type, boolean open, String gateLabel, long androidTimeMs, float score, String pairingCode) {
        this.type = type;
        this.open = open;
        this.gateLabel = gateLabel;
        this.androidTimeMs = androidTimeMs;
        this.score = score;
        this.pairingCode = pairingCode;
    }

    public static GateMessage state(String gateLabel, boolean open, long androidTimeMs, float score) {
        return new GateMessage(Type.STATE, open, gateLabel, androidTimeMs, score, "");
    }

    public static GateMessage heartbeat(boolean open, long androidTimeMs) {
        return new GateMessage(Type.HEARTBEAT, open, open ? "OPEN" : "BLOCKED", androidTimeMs, 0.0f, "");
    }

    public static GateMessage hello() {
        return new GateMessage(Type.HELLO, false, "", 0L, 0.0f, "");
    }

    public static GateMessage pairing(String pairingCode) {
        return new GateMessage(Type.PAIRING, false, "", 0L, 0.0f, pairingCode);
    }

    public boolean isState() {
        return type == Type.STATE;
    }

    public boolean isHeartbeat() {
        return type == Type.HEARTBEAT;
    }

    public boolean isPairing() {
        return type == Type.PAIRING;
    }

    public boolean isOpen() {
        return open;
    }

    public String gateLabel() {
        return gateLabel;
    }

    public long androidTimeMs() {
        return androidTimeMs;
    }

    public float score() {
        return score;
    }

    public Type type() {
        return type;
    }

    public String pairingCode() {
        return pairingCode;
    }
}
