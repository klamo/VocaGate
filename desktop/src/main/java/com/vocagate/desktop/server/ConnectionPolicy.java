package com.vocagate.desktop.server;

public final class ConnectionPolicy {
    private ConnectionPolicy() {
    }

    public static boolean shouldAccept(ConnectionMode mode, ConnectionChannel active, ConnectionChannel incoming) {
        if (!modeAllows(mode, incoming)) {
            return false;
        }
        return active == null || active == incoming || shouldReplaceActive(mode, active, incoming);
    }

    public static boolean shouldReplaceActive(ConnectionMode mode, ConnectionChannel active, ConnectionChannel incoming) {
        if (active == null || active == incoming || !modeAllows(mode, incoming)) {
            return false;
        }
        return mode == ConnectionMode.AUTO && active == ConnectionChannel.WIFI && incoming == ConnectionChannel.USB;
    }

    private static boolean modeAllows(ConnectionMode mode, ConnectionChannel channel) {
        if (mode == ConnectionMode.AUTO) {
            return true;
        }
        if (mode == ConnectionMode.USB) {
            return channel == ConnectionChannel.USB;
        }
        return channel == ConnectionChannel.WIFI;
    }
}
