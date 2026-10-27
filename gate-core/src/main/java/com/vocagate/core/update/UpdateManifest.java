package com.vocagate.core.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UpdateManifest {
    private final int schemaVersion;
    private final String channel;
    private final String generatedAt;
    private final List<ManifestAsset> assets;

    public UpdateManifest(int schemaVersion, String channel, String generatedAt, List<ManifestAsset> assets) {
        if (schemaVersion <= 0) {
            throw new UpdateProtocolException("manifest schema_version must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.channel = ManifestAsset.requireText(channel, "manifest channel");
        this.generatedAt = ManifestAsset.requireText(generatedAt, "manifest generated_at");
        if (assets == null || assets.isEmpty()) {
            throw new UpdateProtocolException("manifest assets are required");
        }
        this.assets = Collections.unmodifiableList(new ArrayList<>(assets));
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String channel() {
        return channel;
    }

    public String generatedAt() {
        return generatedAt;
    }

    public List<ManifestAsset> assets() {
        return assets;
    }

    public ManifestAsset asset(String id) {
        for (ManifestAsset asset : assets) {
            if (asset.id().equals(id)) {
                return asset;
            }
        }
        throw new UpdateProtocolException("manifest asset not found: " + id);
    }
}
