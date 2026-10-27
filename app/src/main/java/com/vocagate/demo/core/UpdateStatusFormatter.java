package com.vocagate.demo.core;

import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.core.update.UpdatePreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class UpdateStatusFormatter {
    private UpdateStatusFormatter() {
    }

    public static String statusText(UpdatePreferences preferences) {
        if (preferences == null || !preferences.updateChecksEnabled()) {
            return "检查更新已关闭";
        }
        if (!preferences.lastProfileId().isEmpty()) {
            return "已缓存 " + preferences.lastProfileId();
        }
        return "未检查";
    }

    public static String profileText(AlgorithmProfile profile, long checkedAtMs) {
        String version = profile == null ? "内置默认" : profile.version();
        String checkedAt = checkedAtMs <= 0L ? "-" : formatTime(checkedAtMs);
        return "算法版本 " + version + " · 更新时间 " + checkedAt;
    }

    public static String shortStatus(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "-";
        }
        String trimmed = message.trim();
        return trimmed.length() > 44 ? trimmed.substring(0, 41) + "..." : trimmed;
    }

    private static String formatTime(long timeMs) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(timeMs));
    }
}
