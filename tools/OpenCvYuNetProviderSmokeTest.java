import com.vocagate.desktop.vision.DesktopMouthRegion;
import com.vocagate.desktop.vision.DesktopMouthRegionProvider;
import com.vocagate.desktop.vision.OpenCvYuNetMouthRegionProvider;

import java.awt.image.BufferedImage;

public final class OpenCvYuNetProviderSmokeTest {
    public static void main(String[] args) {
        DesktopMouthRegionProvider provider = OpenCvYuNetMouthRegionProvider.createDefault();
        DesktopMouthRegion region = provider.detect(blankFrame());
        if (region == null) {
            throw new AssertionError("OpenCV YuNet provider should always return a region object");
        }
        System.out.println("OpenCV YuNet provider smoke passed: " + region.label());
    }

    private static BufferedImage blankFrame() {
        BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB);
        int rgb = (96 << 16) | (96 << 8) | 96;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
}
