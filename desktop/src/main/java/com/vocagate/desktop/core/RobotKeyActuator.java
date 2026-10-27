package com.vocagate.desktop.core;

import java.awt.AWTException;
import java.awt.Robot;

public final class RobotKeyActuator implements KeyActuator {
    private final Robot robot;

    public RobotKeyActuator() {
        try {
            robot = new Robot();
            robot.setAutoDelay(0);
        } catch (AWTException e) {
            throw new IllegalStateException("Failed to initialize keyboard actuator", e);
        }
    }

    @Override
    public synchronized void press(KeyStrokeSpec spec) {
        int[] keyCodes = spec.keyCodes();
        for (int keyCode : keyCodes) {
            robot.keyPress(keyCode);
        }
    }

    @Override
    public synchronized void release(KeyStrokeSpec spec) {
        int[] keyCodes = spec.keyCodes();
        for (int i = keyCodes.length - 1; i >= 0; i--) {
            robot.keyRelease(keyCodes[i]);
        }
    }
}
