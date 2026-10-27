package com.vocagate.core.update;

public final class UpdateClientConfig {
    private final String updateServerUrl;
    private final String platform;
    private final String appVersion;
    private final String channel;
    private final String engine;
    private final boolean updateChecksEnabled;
    private final boolean anonymousStatsEnabled;
    private final String manifestPublicKeyBase64;

    public UpdateClientConfig(
            String updateServerUrl,
            String platform,
            String appVersion,
            String channel,
            String engine,
            boolean updateChecksEnabled,
            boolean anonymousStatsEnabled
    ) {
        this(updateServerUrl, platform, appVersion, channel, engine, updateChecksEnabled, anonymousStatsEnabled, "");
    }

    public UpdateClientConfig(
            String updateServerUrl,
            String platform,
            String appVersion,
            String channel,
            String engine,
            boolean updateChecksEnabled,
            boolean anonymousStatsEnabled,
            String manifestPublicKeyBase64
    ) {
        this.updateServerUrl = UpdatePreferences.normalizeServerUrl(updateServerUrl);
        this.platform = require(platform, "platform");
        this.appVersion = require(appVersion, "app version");
        this.channel = require(channel, "channel");
        this.engine = require(engine, "engine");
        this.updateChecksEnabled = updateChecksEnabled;
        this.anonymousStatsEnabled = anonymousStatsEnabled;
        this.manifestPublicKeyBase64 = manifestPublicKeyBase64 == null ? "" : manifestPublicKeyBase64.trim();
    }

    public String updateServerUrl() {
        return updateServerUrl;
    }

    public String platform() {
        return platform;
    }

    public String appVersion() {
        return appVersion;
    }

    public String channel() {
        return channel;
    }

    public String engine() {
        return engine;
    }

    public boolean updateChecksEnabled() {
        return updateChecksEnabled;
    }

    public boolean anonymousStatsEnabled() {
        return anonymousStatsEnabled;
    }

    public String manifestPublicKeyBase64() {
        return manifestPublicKeyBase64;
    }

    private static String require(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new UpdateProtocolException(label + " is required");
        }
        return value.trim();
    }
}
