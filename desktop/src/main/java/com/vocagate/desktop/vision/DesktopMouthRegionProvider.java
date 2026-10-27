package com.vocagate.desktop.vision;

import java.awt.image.BufferedImage;

public interface DesktopMouthRegionProvider {
    DesktopMouthRegion detect(BufferedImage image);
}
