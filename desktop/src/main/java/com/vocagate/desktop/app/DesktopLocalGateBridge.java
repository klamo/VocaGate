package com.vocagate.desktop.app;

import com.vocagate.core.GateState;
import com.vocagate.desktop.core.GateHoldController;
import com.vocagate.desktop.core.GateMessage;
import com.vocagate.desktop.vision.DesktopRecognitionResult;

public final class DesktopLocalGateBridge {
    private final GateHoldController controller;

    public DesktopLocalGateBridge(GateHoldController controller) {
        this.controller = controller;
    }

    public void onRecognitionResult(DesktopRecognitionResult result, long nowMs) {
        if (controller == null || result == null) {
            return;
        }
        GateState state = result.state();
        boolean open = isOpenState(state);
        String label = state == null ? "BLOCKED" : (open ? state.name() : "BLOCKED");
        controller.onMessage(GateMessage.state(label, open, nowMs, result.score()), nowMs);
    }

    private boolean isOpenState(GateState state) {
        return state == GateState.PRE_OPEN || state == GateState.OPEN || state == GateState.HOLD;
    }
}
