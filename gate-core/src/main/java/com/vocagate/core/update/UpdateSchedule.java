package com.vocagate.core.update;

public final class UpdateSchedule {
    public static final long DAILY_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private UpdateSchedule() {
    }

    public static boolean shouldCheck(UpdatePreferences preferences, long nowMs) {
        if (preferences == null || !preferences.updateChecksEnabled()) {
            return false;
        }
        if (preferences.lastCheckedAtMs() <= 0L) {
            return true;
        }
        return nowMs - preferences.lastCheckedAtMs() >= DAILY_INTERVAL_MS;
    }
}
