package com.vocagate.core.update;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class UpdateCheckClient {
    private final UpdateHttpClient httpClient;

    public UpdateCheckClient(UpdateHttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient is required");
        }
        this.httpClient = httpClient;
    }

    public UpdateCheckResult checkNow(UpdateClientConfig config) {
        if (config == null || !config.updateChecksEnabled()) {
            return UpdateCheckResult.skipped("检查更新已关闭");
        }
        try {
            String manifestUrl = manifestUrl(config);
            byte[] manifestBytes = httpClient.get(manifestUrl);
            verifyManifestSignatureIfConfigured(config, manifestBytes);
            UpdateManifest manifest = UpdateProtocolParser.parseManifest(new String(manifestBytes, StandardCharsets.UTF_8));
            ManifestAsset asset = selectProfileAsset(manifest, config);
            byte[] profileBytes = httpClient.get(resolveUrl(config.updateServerUrl(), asset.url()));
            if (!UpdateVerifier.matchesSha256(profileBytes, asset.sha256())) {
                return UpdateCheckResult.failure("profile SHA-256 mismatch: " + asset.id());
            }
            if (!asset.signature().isEmpty()
                    && !config.manifestPublicKeyBase64().isEmpty()
                    && !UpdateVerifier.verifyEd25519(profileBytes, asset.signature(), config.manifestPublicKeyBase64())) {
                return UpdateCheckResult.failure("profile signature verification failed: " + asset.id());
            }
            String profileJson = new String(profileBytes, StandardCharsets.UTF_8);
            AlgorithmProfile profile = UpdateProtocolParser.parseAlgorithmProfile(profileJson);
            return UpdateCheckResult.success(System.currentTimeMillis(), asset, profile, profileJson);
        } catch (Exception e) {
            return UpdateCheckResult.failure(e.getMessage());
        }
    }

    public static String manifestUrl(UpdateClientConfig config) {
        String base = config.updateServerUrl() + "/v1/manifest.json";
        if (!config.anonymousStatsEnabled()) {
            return base;
        }
        return base
                + "?platform=" + encodeQueryValue(config.platform())
                + "&app_version=" + encodeQueryValue(config.appVersion())
                + "&channel=" + encodeQueryValue(config.channel());
    }

    static String resolveUrl(String baseUrl, String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String normalizedBase = UpdatePreferences.normalizeServerUrl(baseUrl);
        return url.startsWith("/") ? normalizedBase + url : normalizedBase + "/" + url;
    }

    private void verifyManifestSignatureIfConfigured(UpdateClientConfig config, byte[] manifestBytes) throws Exception {
        if (config.manifestPublicKeyBase64().isEmpty()) {
            return;
        }
        byte[] signatureBytes = httpClient.get(config.updateServerUrl() + "/v1/manifest.json.sig");
        String signatureBase64 = new String(signatureBytes, StandardCharsets.UTF_8).trim();
        if (!UpdateVerifier.verifyEd25519(manifestBytes, signatureBase64, config.manifestPublicKeyBase64())) {
            throw new UpdateProtocolException("manifest signature verification failed");
        }
    }

    private ManifestAsset selectProfileAsset(UpdateManifest manifest, UpdateClientConfig config) {
        for (ManifestAsset asset : manifest.assets()) {
            if (!"profile".equalsIgnoreCase(asset.kind())) {
                continue;
            }
            if (!asset.engine().equals(config.engine())) {
                continue;
            }
            if (!platformMatches(asset.platform(), config.platform())) {
                continue;
            }
            if (compareVersions(asset.minAppVersion(), config.appVersion()) > 0) {
                continue;
            }
            return asset;
        }
        throw new UpdateProtocolException("no compatible profile asset for " + config.platform() + " / " + config.engine());
    }

    private boolean platformMatches(String assetPlatform, String clientPlatform) {
        if (assetPlatform.equals(clientPlatform)) {
            return true;
        }
        return "desktop".equals(assetPlatform)
                && clientPlatform.toLowerCase(Locale.US).startsWith("desktop");
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[^0-9]+");
        String[] rightParts = right.split("[^0-9]+");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            int leftValue = partValue(leftParts, i);
            int rightValue = partValue(rightParts, i);
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int partValue(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String encodeQueryValue(String value) {
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == '.') {
                encoded.append(ch);
            } else {
                encoded.append('%');
                encoded.append(String.format(Locale.US, "%02X", (int) ch));
            }
        }
        return encoded.toString();
    }
}
