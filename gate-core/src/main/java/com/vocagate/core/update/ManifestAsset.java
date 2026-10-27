package com.vocagate.core.update;

public final class ManifestAsset {
    private final String id;
    private final String kind;
    private final String engine;
    private final String platform;
    private final String version;
    private final String minAppVersion;
    private final String url;
    private final String sha256;
    private final String signature;
    private final long sizeBytes;

    public ManifestAsset(
            String id,
            String kind,
            String engine,
            String platform,
            String version,
            String minAppVersion,
            String url,
            String sha256,
            String signature,
            long sizeBytes
    ) {
        this.id = requireText(id, "asset id");
        this.kind = requireText(kind, "asset kind");
        this.engine = requireText(engine, "asset engine");
        this.platform = requireText(platform, "asset platform");
        this.version = requireText(version, "asset version");
        this.minAppVersion = requireText(minAppVersion, "asset min_app_version");
        this.url = requireText(url, "asset url");
        this.sha256 = requireText(sha256, "asset sha256");
        this.signature = signature == null ? "" : signature.trim();
        this.sizeBytes = Math.max(0L, sizeBytes);
    }

    public String id() {
        return id;
    }

    public String kind() {
        return kind;
    }

    public String engine() {
        return engine;
    }

    public String platform() {
        return platform;
    }

    public String version() {
        return version;
    }

    public String minAppVersion() {
        return minAppVersion;
    }

    public String url() {
        return url;
    }

    public String sha256() {
        return sha256;
    }

    public String signature() {
        return signature;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new UpdateProtocolException(label + " is required");
        }
        return value.trim();
    }
}
