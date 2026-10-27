package com.vocagate.desktop.core;

public interface KeyActuator {
    void press(KeyStrokeSpec spec);

    void release(KeyStrokeSpec spec);
}
