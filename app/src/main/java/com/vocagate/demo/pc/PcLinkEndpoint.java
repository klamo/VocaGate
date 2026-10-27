package com.vocagate.demo.pc;

public final class PcLinkEndpoint {
    private final String channelName;
    private final String host;
    private final int port;
    private final String pairingCode;

    public PcLinkEndpoint(String channelName, String host, int port, String pairingCode) {
        this.channelName = channelName;
        this.host = host;
        this.port = port;
        this.pairingCode = pairingCode == null ? "" : pairingCode.trim();
    }

    public String channelName() {
        return channelName;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String pairingCode() {
        return pairingCode;
    }

    public boolean needsPairing() {
        return "Wi-Fi".equals(channelName) && !pairingCode.isEmpty();
    }
}
