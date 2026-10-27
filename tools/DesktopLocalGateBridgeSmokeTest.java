import com.vocagate.core.GateState;
import com.vocagate.desktop.app.DesktopLocalGateBridge;
import com.vocagate.desktop.core.GateHoldController;
import com.vocagate.desktop.core.KeyActuator;
import com.vocagate.desktop.core.KeyStrokeSpec;
import com.vocagate.desktop.vision.DesktopMouthRegion;
import com.vocagate.desktop.vision.DesktopRecognitionResult;

import java.util.ArrayList;
import java.util.List;

public final class DesktopLocalGateBridgeSmokeTest {
    public static void main(String[] args) {
        RecordingActuator actuator = new RecordingActuator();
        GateHoldController controller = new GateHoldController(
                actuator,
                KeyStrokeSpec.parse("CTRL+SHIFT+M"),
                500L
        );
        DesktopLocalGateBridge bridge = new DesktopLocalGateBridge(controller);

        bridge.onRecognitionResult(result(GateState.PRE_OPEN, 0.72f), 1000L);
        bridge.onRecognitionResult(result(GateState.OPEN, 0.82f), 1100L);
        bridge.onRecognitionResult(DesktopRecognitionResult.noFrame(), 1200L);

        assertEvents(actuator.events, "press:Ctrl+Shift+M", "release:Ctrl+Shift+M");
        System.out.println("Desktop local gate bridge smoke passed");
    }

    private static DesktopRecognitionResult result(GateState state, float score) {
        boolean open = state == GateState.PRE_OPEN || state == GateState.OPEN || state == GateState.HOLD;
        return new DesktopRecognitionResult(
                score,
                score,
                30.0f,
                open,
                state,
                true,
                DesktopMouthRegion.tracked(8, 10, 34, 18, "model-mouth"),
                "model-mouth",
                state.name()
        );
    }

    private static void assertEvents(List<String> actual, String... expected) {
        if (actual.size() != expected.length) {
            throw new AssertionError("expected " + expected.length + " events but got " + actual);
        }
        for (int i = 0; i < expected.length; i++) {
            if (!actual.get(i).equals(expected[i])) {
                throw new AssertionError("event " + i + " expected " + expected[i] + " but got " + actual.get(i));
            }
        }
    }

    private static final class RecordingActuator implements KeyActuator {
        private final List<String> events = new ArrayList<>();

        @Override
        public void press(KeyStrokeSpec spec) {
            events.add("press:" + spec.displayName());
        }

        @Override
        public void release(KeyStrokeSpec spec) {
            events.add("release:" + spec.displayName());
        }
    }
}
