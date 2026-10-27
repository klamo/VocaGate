package com.vocagate.core.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class HttpUrlUpdateClient implements UpdateHttpClient {
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpUrlUpdateClient() {
        this(4500, 7000);
    }

    public HttpUrlUpdateClient(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = Math.max(1000, connectTimeoutMs);
        this.readTimeoutMs = Math.max(1000, readTimeoutMs);
    }

    @Override
    public byte[] get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json, application/octet-stream");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}
