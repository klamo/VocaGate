package com.vocagate.core.update;

public final class UpdatePreferences {
    public static final String DEFAULT_UPDATE_SERVER_URL = "https://updates.vocagate.app";

    private final boolean updateChecksEnabled;
    private final boolean anonymousStatsEnabled;
    private final String updateServerUrl;
    private final long lastCheckedAtMs;
    private final String lastProfileId;

    public UpdatePreferences(
            boolean updateChecksEnabled,
            boolean anonymousStatsEnabled,
            String updateServerUrl,
            long lastCheckedAtMs,
            String lastProfileId
    ) {
        this.updateChecksEnabled = updateChecksEnabled;
        this.anonymousStatsEnabled = anonymousStatsEnabled;
        this.updateServerUrl = normalizeServerUrl(updateServerUrl);
        this.lastCheckedAtMs = Math.max(0L, lastCheckedAtMs);
        this.lastProfileId = lastProfileId == null ? "" : lastProfileId.trim();
    }

    public static UpdatePreferences defaults() {
        return new UpdatePreferences(true, true, DEFAULT_UPDATE_SERVER_URL, 0L, "");
    }

    public boolean updateChecksEnabled() {
        return updateChecksEnabled;
    }

    public boolean anonymousStatsEnabled() {
        return anonymousStatsEnabled;
    }

    public String updateServerUrl() {
        return updateServerUrl;
    }

    public long lastCheckedAtMs() {
        return lastCheckedAtMs;
    }

    public String lastProfileId() {
        return lastProfileId;
    }

    public UpdatePreferences withLastCheck(long checkedAtMs, String profileId) {
        return new UpdatePreferences(
                updateChecksEnabled,
                anonymousStatsEnabled,
                updateServerUrl,
                checkedAtMs,
                profileId
        );
    }

    public static String normalizeServerUrl(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) {
            raw = DEFAULT_UPDATE_SERVER_URL;
        }
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }
}
