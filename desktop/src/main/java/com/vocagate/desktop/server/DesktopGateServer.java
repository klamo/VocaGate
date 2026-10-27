package com.vocagate.desktop.server;

import com.vocagate.desktop.core.GateHoldController;
import com.vocagate.desktop.core.GateMessage;
import com.vocagate.desktop.core.GateProtocolParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class DesktopGateServer {
    public interface Listener {
        void onStatus(String status);

        void onMessage(GateMessage message);

        default void onChannel(ConnectionChannel channel) {
        }
    }

    private final int requestedPort;
    private final GateHoldController controller;
    private final Listener listener;
    private volatile ConnectionMode mode;
    private volatile String pairingCode;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Socket currentSocket;
    private ConnectionChannel activeChannel;
    private Thread acceptThread;
    private Thread watchdogThread;
    private int boundPort = -1;

    public DesktopGateServer(int port, GateHoldController controller, Listener listener) {
        this(port, controller, listener, ConnectionMode.AUTO, "");
    }

    public DesktopGateServer(
            int port,
            GateHoldController controller,
            Listener listener,
            ConnectionMode mode,
            String pairingCode
    ) {
        this.requestedPort = port;
        this.controller = controller;
        this.listener = listener;
        this.mode = mode;
        this.pairingCode = pairingCode == null ? "" : pairingCode.trim();
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("0.0.0.0", requestedPort));
            boundPort = serverSocket.getLocalPort();
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start desktop server", e);
        }

        acceptThread = new Thread(this::acceptLoop, "VocaGateDesktopAccept");
        acceptThread.start();
        watchdogThread = new Thread(this::watchdogLoop, "VocaGateDesktopWatchdog");
        watchdogThread.start();
        listener.onStatus("Listening on 0.0.0.0:" + boundPort);
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        closeCurrentSocket();
        closeServerSocket();
        controller.onDisconnect();
        listener.onChannel(null);
        listener.onStatus("Stopped");
    }

    public int port() {
        if (boundPort <= 0) {
            throw new IllegalStateException("server is not started");
        }
        return boundPort;
    }

    public synchronized void setMode(ConnectionMode nextMode) {
        mode = nextMode;
        if (activeChannel != null && !ConnectionPolicy.shouldAccept(mode, null, activeChannel)) {
            closeCurrentSocket();
            controller.onDisconnect();
            listener.onChannel(null);
            listener.onStatus("Disconnected because mode changed to " + mode);
        }
    }

    public void setPairingCode(String pairingCode) {
        this.pairingCode = pairingCode == null ? "" : pairingCode.trim();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ConnectionChannel channel = classify(socket);
                if (!activate(socket, channel)) {
                    closeSocket(socket);
                    listener.onStatus("Rejected " + channel + " client in " + mode + " mode");
                    continue;
                }
                Thread clientThread = new Thread(
                        () -> handleClient(socket, channel),
                        "VocaGateDesktopClient-" + channel
                );
                clientThread.start();
            } catch (IOException e) {
                if (running) {
                    listener.onStatus("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket, ConnectionChannel channel) {
        boolean paired = channel == ConnectionChannel.USB || pairingCode.isEmpty();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && isCurrent(socket) && (line = reader.readLine()) != null) {
                GateMessage message;
                try {
                    message = GateProtocolParser.parseLine(line);
                } catch (IllegalArgumentException e) {
                    listener.onStatus("Ignored invalid line: " + e.getMessage());
                    continue;
                }

                if (message.isPairing()) {
                    paired = pairingCode.equals(message.pairingCode());
                    listener.onStatus(paired ? "Wi-Fi paired" : "Wi-Fi pairing failed");
                    if (!paired) {
                        break;
                    }
                    continue;
                }
                if (!paired && message.isState()) {
                    listener.onStatus("Wi-Fi client missing pairing code");
                    break;
                }

                try {
                    controller.onMessage(message, System.currentTimeMillis());
                } catch (RuntimeException e) {
                    controller.onDisconnect();
                    listener.onStatus("Key action failed: " + e.getMessage());
                    continue;
                }
                listener.onMessage(message);
            }
        } catch (IOException e) {
            if (running) {
                listener.onStatus("Client read failed: " + e.getMessage());
            }
        } finally {
            deactivate(socket, channel);
        }
    }

    private synchronized boolean activate(Socket socket, ConnectionChannel channel) {
        if (!ConnectionPolicy.shouldAccept(mode, activeChannel, channel)) {
            return false;
        }

        if (ConnectionPolicy.shouldReplaceActive(mode, activeChannel, channel) || activeChannel == channel) {
            closeCurrentSocket();
            controller.onDisconnect();
        }

        currentSocket = socket;
        activeChannel = channel;
        listener.onChannel(channel);
        listener.onStatus(channel + " client connected");
        return true;
    }

    private synchronized void deactivate(Socket socket, ConnectionChannel channel) {
        if (socket != currentSocket) {
            closeSocket(socket);
            return;
        }

        controller.onDisconnect();
        closeCurrentSocket();
        listener.onChannel(null);
        if (running) {
            listener.onStatus(channel + " client disconnected");
        }
    }

    private synchronized boolean isCurrent(Socket socket) {
        return socket == currentSocket;
    }

    private ConnectionChannel classify(Socket socket) {
        InetAddress address = socket.getInetAddress();
        return address.isLoopbackAddress() ? ConnectionChannel.USB : ConnectionChannel.WIFI;
    }

    private void watchdogLoop() {
        while (running) {
            controller.checkTimeout(System.currentTimeMillis());
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void closeCurrentSocket() {
        Socket socket = currentSocket;
        currentSocket = null;
        activeChannel = null;
        closeSocket(socket);
    }

    private void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
