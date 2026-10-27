package com.vocagate.core;

public final class GateStateMachine {
    private final GateConfig config;
    private GateState state = GateState.CLOSED;
    private int highScoreFrames = 0;
    private int lowScoreFrames = 0;
    private long openedAtMs = -1L;
    private long holdStartedAtMs = -1L;
    private long cooldownStartedAtMs = -1L;

    public GateStateMachine(GateConfig config) {
        this.config = config;
    }

    public GateState update(float speakScore, boolean trackingValid, long nowMs) {
        if (!trackingValid) {
            transitionTo(GateState.SUSPEND, nowMs);
            return state;
        }

        switch (state) {
            case CLOSED:
                updateClosed(speakScore, nowMs);
                break;
            case PRE_OPEN:
                updatePreOpen(speakScore, nowMs);
                break;
            case OPEN:
                updateOpen(speakScore, nowMs);
                break;
            case HOLD:
                updateHold(speakScore, nowMs);
                break;
            case COOLDOWN:
                updateCooldown(speakScore, nowMs);
                break;
            case SUSPEND:
                updateSuspend(speakScore, nowMs);
                break;
            default:
                transitionTo(GateState.CLOSED, nowMs);
                break;
        }

        return state;
    }

    public GateState getState() {
        return state;
    }

    public boolean isGateOpen() {
        return state == GateState.OPEN || state == GateState.HOLD;
    }

    private void updateClosed(float speakScore, long nowMs) {
        if (speakScore >= config.preOpenThreshold) {
            highScoreFrames = speakScore >= config.openThreshold ? 1 : 0;
            transitionTo(GateState.PRE_OPEN, nowMs);
        }
    }

    private void updatePreOpen(float speakScore, long nowMs) {
        if (speakScore >= config.openThreshold) {
            highScoreFrames++;
            if (highScoreFrames >= config.preOpenRequiredFrames) {
                transitionTo(GateState.OPEN, nowMs);
            }
        } else if (speakScore < config.preOpenThreshold) {
            transitionTo(GateState.CLOSED, nowMs);
        }
    }

    private void updateOpen(float speakScore, long nowMs) {
        if (speakScore < config.closeThreshold && nowMs - openedAtMs >= config.minOpenMs) {
            lowScoreFrames++;
            if (lowScoreFrames >= config.closeRequiredFrames) {
                transitionTo(GateState.HOLD, nowMs);
            }
        } else {
            lowScoreFrames = 0;
        }
    }

    private void updateHold(float speakScore, long nowMs) {
        if (speakScore >= config.openThreshold) {
            highScoreFrames = 1;
            transitionTo(GateState.OPEN, nowMs);
        } else if (speakScore >= config.preOpenThreshold) {
            holdStartedAtMs = nowMs;
        } else if (nowMs - holdStartedAtMs >= config.holdMs) {
            transitionTo(GateState.COOLDOWN, nowMs);
        }
    }

    private void updateCooldown(float speakScore, long nowMs) {
        if (nowMs - cooldownStartedAtMs >= config.cooldownMs) {
            transitionTo(GateState.CLOSED, nowMs);
            updateClosed(speakScore, nowMs);
        }
    }

    private void updateSuspend(float speakScore, long nowMs) {
        if (speakScore >= config.preOpenThreshold) {
            highScoreFrames = speakScore >= config.openThreshold ? 1 : 0;
            transitionTo(GateState.PRE_OPEN, nowMs);
        } else {
            transitionTo(GateState.CLOSED, nowMs);
        }
    }

    private void transitionTo(GateState nextState, long nowMs) {
        if (state == nextState) {
            return;
        }

        state = nextState;
        if (nextState != GateState.PRE_OPEN) {
            highScoreFrames = 0;
        }
        if (nextState != GateState.OPEN) {
            lowScoreFrames = 0;
        }

        if (nextState == GateState.OPEN) {
            openedAtMs = nowMs;
            lowScoreFrames = 0;
        } else if (nextState == GateState.HOLD) {
            holdStartedAtMs = nowMs;
        } else if (nextState == GateState.COOLDOWN) {
            cooldownStartedAtMs = nowMs;
        }
    }
}
