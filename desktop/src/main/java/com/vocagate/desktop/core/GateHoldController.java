package com.vocagate.desktop.core;

public final class GateHoldController {
    private final KeyActuator actuator;
    private final long timeoutMs;
    private KeyStrokeSpec keySpec;
    private boolean keyDown = false;
    private long lastMessageAtMs = -1L;

    public GateHoldController(KeyActuator actuator, KeyStrokeSpec keySpec, long timeoutMs) {
        this.actuator = actuator;
        this.keySpec = keySpec;
        this.timeoutMs = timeoutMs;
    }

    public synchronized void onMessage(GateMessage message, long nowMs) {
        lastMessageAtMs = nowMs;
        if (message.isState() || message.isHeartbeat()) {
            applyGate(message.isOpen());
        }
    }

    public synchronized void checkTimeout(long nowMs) {
        if (lastMessageAtMs >= 0L && nowMs - lastMessageAtMs > timeoutMs) {
            releaseIfNeeded();
        }
    }

    public synchronized void onDisconnect() {
        releaseIfNeeded();
        lastMessageAtMs = -1L;
    }

    public synchronized void setKeySpec(KeyStrokeSpec nextSpec) {
        if (keyDown) {
            actuator.release(keySpec);
            keyDown = false;
        }
        keySpec = nextSpec;
    }

    public synchronized boolean isKeyDown() {
        return keyDown;
    }

    private void applyGate(boolean open) {
        if (open) {
            if (!keyDown) {
                actuator.press(keySpec);
                keyDown = true;
            }
        } else {
            releaseIfNeeded();
        }
    }

    private void releaseIfNeeded() {
        if (keyDown) {
            actuator.release(keySpec);
            keyDown = false;
        }
    }
}
