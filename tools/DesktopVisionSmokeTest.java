import com.vocagate.desktop.vision.DesktopMotionScoreAnalyzer;
import com.vocagate.desktop.vision.DesktopMouthRegion;
import com.vocagate.desktop.vision.DesktopMouthRegionProvider;
import com.vocagate.desktop.vision.DesktopMouthRegionProviderFactory;
import com.vocagate.desktop.vision.DesktopRecognitionResult;

import java.awt.image.BufferedImage;

public final class DesktopVisionSmokeTest {
    public static void main(String[] args) {
        DesktopMouthRegionProvider provider = DesktopMouthRegionProviderFactory.createDefault();
        DesktopMouthRegion region = provider.detect(syntheticFrame(80));
        if (region == null || !region.trackingValid()) {
            throw new AssertionError("default mouth provider should return a usable region");
        }

        DesktopMotionScoreAnalyzer analyzer = new DesktopMotionScoreAnalyzer();
        analyzer.analyze(syntheticFrame(80), 0L);
        DesktopRecognitionResult result = analyzer.analyze(syntheticFrame(80), 100L);
        if (result.score() >= 0.08f) {
            throw new AssertionError("static frames should keep score low: " + result.score());
        }
        System.out.println("Desktop vision smoke passed: " + region.label());
    }

    private static BufferedImage syntheticFrame(int mouthBrightness) {
        BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB);
        fill(image, 0, 0, image.getWidth(), image.getHeight(), rgb(96));
        fill(image, 50, 72, 60, 24, rgb(mouthBrightness));
        return image;
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int rgb) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                image.setRGB(column, row, rgb);
            }
        }
    }

    private static int rgb(int value) {
        return (value << 16) | (value << 8) | value;
    }
}
