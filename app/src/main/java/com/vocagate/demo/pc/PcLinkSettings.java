package com.vocagate.demo.pc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PcLinkSettings {
    public static final int DEFAULT_PORT = 27183;
    private static final String USB_HOST = "127.0.0.1";

    private final PcLinkMode mode;
    private final String wifiHost;
    private final String pairingCode;
    private final int port;

    public PcLinkSettings(PcLinkMode mode, String wifiHost, String pairingCode, int port) {
        this.mode = mode == null ? PcLinkMode.AUTO : mode;
        this.wifiHost = wifiHost == null ? "" : wifiHost.trim();
        this.pairingCode = pairingCode == null ? "" : pairingCode.trim();
        this.port = port <= 0 ? DEFAULT_PORT : port;
    }

    public PcLinkMode mode() {
        return mode;
    }

    public String wifiHost() {
        return wifiHost;
    }

    public String pairingCode() {
        return pairingCode;
    }

    public int port() {
        return port;
    }

    public List<PcLinkEndpoint> endpoints() {
        List<PcLinkEndpoint> endpoints = new ArrayList<>();
        if (mode == PcLinkMode.AUTO || mode == PcLinkMode.USB) {
            endpoints.add(new PcLinkEndpoint("USB", USB_HOST, port, ""));
        }
        if ((mode == PcLinkMode.AUTO || mode == PcLinkMode.WIFI) && !wifiHost.isEmpty()) {
            endpoints.add(new PcLinkEndpoint("Wi-Fi", wifiHost, port, pairingCode));
        }
        return Collections.unmodifiableList(endpoints);
    }

    public boolean needsWifiPairing() {
        return (mode == PcLinkMode.AUTO || mode == PcLinkMode.WIFI) && wifiHost.isEmpty();
    }
}
