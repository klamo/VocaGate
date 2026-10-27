package com.vocagate.desktop.vision;

import java.awt.image.BufferedImage;

public final class CascadingMouthRegionProvider implements DesktopMouthRegionProvider {
    private final DesktopMouthRegionProvider primary;
    private final DesktopMouthRegionProvider fallback;

    public CascadingMouthRegionProvider(
            DesktopMouthRegionProvider primary,
            DesktopMouthRegionProvider fallback
    ) {
        this.primary = primary == null ? new CenteredMouthRegionProvider() : primary;
        this.fallback = fallback == null ? new CenteredMouthRegionProvider() : fallback;
    }

    @Override
    public DesktopMouthRegion detect(BufferedImage image) {
        DesktopMouthRegion primaryRegion = primary.detect(image);
        if (primaryRegion != null && primaryRegion.trackingValid()) {
            return primaryRegion;
        }
        return fallback.detect(image);
    }
}
