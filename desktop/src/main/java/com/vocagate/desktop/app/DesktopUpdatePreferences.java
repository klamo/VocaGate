package com.vocagate.desktop.app;

import com.vocagate.core.update.UpdatePreferences;

import java.util.prefs.Preferences;

public final class DesktopUpdatePreferences {
    private static final String KEY_UPDATE_CHECKS_ENABLED = "updateChecksEnabled";
    private static final String KEY_ANONYMOUS_STATS_ENABLED = "anonymousStatsEnabled";
    private static final String KEY_UPDATE_SERVER_URL = "updateServerUrl";
    private static final String KEY_LAST_CHECKED_AT_MS = "lastUpdateCheckedAtMs";
    private static final String KEY_LAST_PROFILE_ID = "lastUpdateProfileId";
    private static final String KEY_LAST_PROFILE_JSON = "lastUpdateProfileJson";

    private final Preferences preferences;

    public DesktopUpdatePreferences() {
        this(Preferences.userNodeForPackage(DesktopUpdatePreferences.class));
    }

    public DesktopUpdatePreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    public UpdatePreferences load() {
        return new UpdatePreferences(
                preferences.getBoolean(KEY_UPDATE_CHECKS_ENABLED, true),
                preferences.getBoolean(KEY_ANONYMOUS_STATS_ENABLED, true),
                preferences.get(KEY_UPDATE_SERVER_URL, UpdatePreferences.DEFAULT_UPDATE_SERVER_URL),
                preferences.getLong(KEY_LAST_CHECKED_AT_MS, 0L),
                preferences.get(KEY_LAST_PROFILE_ID, "")
        );
    }

    public void saveSettings(boolean updateChecksEnabled, boolean anonymousStatsEnabled, String updateServerUrl) {
        preferences.putBoolean(KEY_UPDATE_CHECKS_ENABLED, updateChecksEnabled);
        preferences.putBoolean(KEY_ANONYMOUS_STATS_ENABLED, anonymousStatsEnabled);
        preferences.put(KEY_UPDATE_SERVER_URL, UpdatePreferences.normalizeServerUrl(updateServerUrl));
    }

    public void saveLastProfile(long checkedAtMs, String profileId, String profileJson) {
        preferences.putLong(KEY_LAST_CHECKED_AT_MS, Math.max(0L, checkedAtMs));
        preferences.put(KEY_LAST_PROFILE_ID, profileId == null ? "" : profileId.trim());
        preferences.put(KEY_LAST_PROFILE_JSON, profileJson == null ? "" : profileJson);
    }

    public void saveLastCheckedAt(long checkedAtMs) {
        preferences.putLong(KEY_LAST_CHECKED_AT_MS, Math.max(0L, checkedAtMs));
    }

    public String loadLastProfileJson() {
        return preferences.get(KEY_LAST_PROFILE_JSON, "");
    }
}
