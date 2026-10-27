package com.vocagate.core.update;

public final class UpdateCheckResult {
    private final boolean success;
    private final String statusMessage;
    private final String errorMessage;
    private final long checkedAtMs;
    private final ManifestAsset asset;
    private final AlgorithmProfile profile;
    private final String profileJson;

    private UpdateCheckResult(
            boolean success,
            String statusMessage,
            String errorMessage,
            long checkedAtMs,
            ManifestAsset asset,
            AlgorithmProfile profile,
            String profileJson
    ) {
        this.success = success;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
        this.checkedAtMs = checkedAtMs;
        this.asset = asset;
        this.profile = profile;
        this.profileJson = profileJson == null ? "" : profileJson;
    }

    public static UpdateCheckResult success(long checkedAtMs, ManifestAsset asset, AlgorithmProfile profile, String profileJson) {
        return new UpdateCheckResult(true, "已获取算法配置 " + profile.profileId(), "", checkedAtMs, asset, profile, profileJson);
    }

    public static UpdateCheckResult skipped(String message) {
        return new UpdateCheckResult(false, message, "", 0L, null, null, "");
    }

    public static UpdateCheckResult failure(String message) {
        return new UpdateCheckResult(false, "", message == null ? "update failed" : message, System.currentTimeMillis(), null, null, "");
    }

    public boolean success() {
        return success;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public long checkedAtMs() {
        return checkedAtMs;
    }

    public ManifestAsset asset() {
        return asset;
    }

    public AlgorithmProfile profile() {
        return profile;
    }

    public String profileJson() {
        return profileJson;
    }
}
