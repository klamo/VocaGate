package com.vocagate.demo.pc;

import android.os.SystemClock;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class PcLinkClient {
    public interface Listener {
        void onStatus(String status);
    }

    private static final long HEARTBEAT_MS = 250L;
    private static final long RECONNECT_MS = 1000L;
    private final Listener listener;
    private final Object lock = new Object();
    private volatile boolean running = false;
    private volatile PcLinkSettings settings = new PcLinkSettings(PcLinkMode.AUTO, "", "", PcLinkSettings.DEFAULT_PORT);
    private Thread worker;
    private Socket socket;
    private boolean latestOpen = false;
    private String latestGateLabel = "BLOCKED";
    private long latestAndroidTimeMs = 0L;
    private float latestScore = 0.0f;
    private boolean dirty = true;

    public PcLinkClient(Listener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        worker = new Thread(this::runLoop, "VocaGatePcLink");
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        closeSocket();
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    public void updateGate(boolean open, long androidTimeMs, float score) {
        updateGateState(open ? "OPEN" : "BLOCKED", open, androidTimeMs, score);
    }

    public void updateGateState(String gateLabel, boolean open, long androidTimeMs, float score) {
        synchronized (lock) {
            String normalizedLabel = gateLabel == null || gateLabel.trim().isEmpty() ? (open ? "OPEN" : "BLOCKED") : gateLabel.trim();
            if (latestOpen != open || !latestGateLabel.equals(normalizedLabel)) {
                dirty = true;
            }
            latestOpen = open;
            latestGateLabel = normalizedLabel;
            latestAndroidTimeMs = androidTimeMs;
            latestScore = score;
        }
    }

    public void updateSettings(PcLinkSettings settings) {
        this.settings = settings;
        closeSocket();
    }

    private void runLoop() {
        publishStatus("PC: waiting");
        while (running) {
            PcLinkSettings currentSettings = settings;
            if (currentSettings.endpoints().isEmpty()) {
                publishStatus(currentSettings.needsWifiPairing() ? "PC: set Wi-Fi in settings" : "PC: waiting");
                sleep(RECONNECT_MS);
                continue;
            }

            boolean connected = false;
            for (PcLinkEndpoint endpoint : currentSettings.endpoints()) {
                if (!running) {
                    break;
                }
                try {
                    connectAndSend(endpoint);
                    connected = true;
                } catch (IOException e) {
                    publishStatus("PC: waiting");
                    closeSocket();
                }
            }
            if (!connected) {
                if (currentSettings.needsWifiPairing()) {
                    publishStatus("PC: waiting, set Wi-Fi");
                }
                sleep(RECONNECT_MS);
            }
        }
        publishStatus("PC: off");
    }

    private void connectAndSend(PcLinkEndpoint endpoint) throws IOException {
        Socket nextSocket = new Socket();
        nextSocket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 1000);
        socket = nextSocket;
        publishStatus("PC: " + endpoint.channelName() + " connected");

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(nextSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            writer.println(PcLinkProtocol.helloLine());
            if (endpoint.needsPairing()) {
                writer.println(PcLinkProtocol.pairLine(endpoint.pairingCode()));
            }
            Boolean lastSentOpen = null;
            String lastSentGateLabel = "";
            long lastPingAtMs = 0L;

            while (running && !nextSocket.isClosed()) {
                Snapshot snapshot = snapshot();
                if (lastSentOpen == null || snapshot.dirty || lastSentOpen != snapshot.open || !lastSentGateLabel.equals(snapshot.gateLabel)) {
                    if ("PRE_OPEN".equals(snapshot.gateLabel)) {
                        writer.println(PcLinkProtocol.preOpenLine(snapshot.androidTimeMs, snapshot.score));
                    } else {
                        writer.println(PcLinkProtocol.stateLine(snapshot.open, snapshot.androidTimeMs, snapshot.score));
                    }
                    lastSentOpen = snapshot.open;
                    lastSentGateLabel = snapshot.gateLabel;
                    markClean();
                }

                long nowMs = SystemClock.elapsedRealtime();
                if (nowMs - lastPingAtMs >= HEARTBEAT_MS) {
                    writer.println(PcLinkProtocol.pingLine(nowMs, snapshot.open));
                    lastPingAtMs = nowMs;
                }

                if (writer.checkError()) {
                    throw new IOException("PC link writer failed");
                }
                sleep(40L);
            }
        } finally {
            closeSocket();
        }
    }

    private Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(latestOpen, latestGateLabel, latestAndroidTimeMs, latestScore, dirty);
        }
    }

    private void markClean() {
        synchronized (lock) {
            dirty = false;
        }
    }

    private void publishStatus(String status) {
        listener.onStatus(status);
    }

    private void closeSocket() {
        Socket activeSocket = socket;
        socket = null;
        if (activeSocket != null) {
            try {
                activeSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Snapshot {
        private final boolean open;
        private final String gateLabel;
        private final long androidTimeMs;
        private final float score;
        private final boolean dirty;

        private Snapshot(boolean open, String gateLabel, long androidTimeMs, float score, boolean dirty) {
            this.open = open;
            this.gateLabel = gateLabel;
            this.androidTimeMs = androidTimeMs;
            this.score = score;
            this.dirty = dirty;
        }
    }
}
