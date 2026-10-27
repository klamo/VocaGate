package com.vocagate.desktop.vision;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;

public final class OpenCvHaarMouthRegionProvider implements DesktopMouthRegionProvider {
    private static final String FACE_CASCADE_RESOURCE = "/opencv/haarcascade_frontalface_default.xml";
    private final CascadeClassifier faceCascade;

    private OpenCvHaarMouthRegionProvider(CascadeClassifier faceCascade) {
        this.faceCascade = faceCascade;
    }

    public static DesktopMouthRegionProvider createDefault() {
        String cascadePath = extractCascade(FACE_CASCADE_RESOURCE);
        CascadeClassifier classifier = new CascadeClassifier(cascadePath);
        if (classifier.empty()) {
            throw new IllegalStateException("OpenCV face cascade failed to load");
        }
        return new OpenCvHaarMouthRegionProvider(classifier);
    }

    @Override
    public DesktopMouthRegion detect(BufferedImage image) {
        if (image == null || image.getWidth() < 32 || image.getHeight() < 32) {
            return DesktopMouthRegion.missing("OpenCV Haar: 无画面");
        }

        Mat gray = toGrayMat(image);
        RectVector faces = new RectVector();
        try {
            int minFace = Math.max(24, Math.min(image.getWidth(), image.getHeight()) / 8);
            faceCascade.detectMultiScale(
                    gray,
                    faces,
                    1.1,
                    4,
                    0,
                    new Size(minFace, minFace),
                    new Size()
            );
            if (faces.size() == 0L) {
                return DesktopMouthRegion.missing("OpenCV Haar: 未检测到脸");
            }

            Rect face = largestFace(faces);
            int mouthX = face.x() + Math.round(face.width() * 0.20f);
            int mouthY = face.y() + Math.round(face.height() * 0.60f);
            int mouthWidth = Math.max(1, Math.round(face.width() * 0.60f));
            int mouthHeight = Math.max(1, Math.round(face.height() * 0.20f));
            return DesktopMouthRegion.tracked(
                    mouthX,
                    mouthY,
                    mouthWidth,
                    mouthHeight,
                    "OpenCV Haar face"
            );
        } catch (RuntimeException e) {
            return DesktopMouthRegion.missing("OpenCV Haar: " + e.getMessage());
        } finally {
            faces.close();
            gray.close();
        }
    }

    private static Rect largestFace(RectVector faces) {
        Rect best = faces.get(0);
        long bestArea = (long) best.width() * best.height();
        for (long i = 1L; i < faces.size(); i++) {
            Rect candidate = faces.get(i);
            long area = (long) candidate.width() * candidate.height();
            if (area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return best;
    }

    private static Mat toGrayMat(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = new byte[width * height];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                int luma = Math.round(red * 0.299f + green * 0.587f + blue * 0.114f);
                data[index++] = (byte) (luma & 0xff);
            }
        }
        Mat mat = new Mat(height, width, CV_8UC1);
        mat.data().put(data);
        return mat;
    }

    private static String extractCascade(String resourcePath) {
        try (InputStream input = OpenCvHaarMouthRegionProvider.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("missing resource " + resourcePath);
            }
            File file = File.createTempFile("vocagate-", "-frontalface.xml");
            file.deleteOnExit();
            try (FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException("failed to extract OpenCV cascade", e);
        }
    }
}
