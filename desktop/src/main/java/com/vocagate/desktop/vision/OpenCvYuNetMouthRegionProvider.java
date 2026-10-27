package com.vocagate.desktop.vision;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;

public final class OpenCvYuNetMouthRegionProvider implements DesktopMouthRegionProvider {
    private static final String YUNET_MODEL_RESOURCE = "/opencv/face_detection_yunet_2023mar.onnx";
    private static final int FACE_X = 0;
    private static final int FACE_Y = 1;
    private static final int FACE_WIDTH = 2;
    private static final int FACE_HEIGHT = 3;
    private static final int MOUTH_RIGHT_X = 10;
    private static final int MOUTH_RIGHT_Y = 11;
    private static final int MOUTH_LEFT_X = 12;
    private static final int MOUTH_LEFT_Y = 13;
    private static final int SCORE = 14;

    private final FaceDetectorYN detector;

    private OpenCvYuNetMouthRegionProvider(FaceDetectorYN detector) {
        this.detector = detector;
    }

    public static DesktopMouthRegionProvider createDefault() {
        String modelPath = extractResource(YUNET_MODEL_RESOURCE, "-yunet.onnx");
        FaceDetectorYN detector = FaceDetectorYN.create(
                modelPath,
                "",
                new Size(320, 320),
                0.72f,
                0.30f,
                5000,
                0,
                0
        );
        return new OpenCvYuNetMouthRegionProvider(detector);
    }

    @Override
    public DesktopMouthRegion detect(BufferedImage image) {
        if (image == null || image.getWidth() < 64 || image.getHeight() < 64) {
            return DesktopMouthRegion.missing("OpenCV YuNet: 无画面");
        }

        Mat color = toBgrMat(image);
        Mat detections = new Mat();
        try {
            detector.setInputSize(new Size(image.getWidth(), image.getHeight()));
            int count = detector.detect(color, detections);
            if (count <= 0 || detections.empty() || detections.cols() <= SCORE) {
                return DesktopMouthRegion.missing("OpenCV YuNet: 未检测到脸");
            }
            return mouthRegionFromDetections(detections, image.getWidth(), image.getHeight());
        } catch (RuntimeException | LinkageError e) {
            return DesktopMouthRegion.missing("OpenCV YuNet: " + e.getMessage());
        } finally {
            detections.close();
            color.close();
        }
    }

    private static DesktopMouthRegion mouthRegionFromDetections(Mat detections, int imageWidth, int imageHeight) {
        FloatIndexer indexer = detections.createIndexer();
        try {
            int bestRow = bestDetectionRow(indexer, detections.rows());
            float faceX = indexer.get(bestRow, FACE_X);
            float faceY = indexer.get(bestRow, FACE_Y);
            float faceWidth = Math.max(1.0f, indexer.get(bestRow, FACE_WIDTH));
            float faceHeight = Math.max(1.0f, indexer.get(bestRow, FACE_HEIGHT));
            float rightMouthX = indexer.get(bestRow, MOUTH_RIGHT_X);
            float rightMouthY = indexer.get(bestRow, MOUTH_RIGHT_Y);
            float leftMouthX = indexer.get(bestRow, MOUTH_LEFT_X);
            float leftMouthY = indexer.get(bestRow, MOUTH_LEFT_Y);

            float centerX = (leftMouthX + rightMouthX) * 0.5f;
            float centerY = (leftMouthY + rightMouthY) * 0.5f + faceHeight * 0.035f;
            float cornerDistance = distance(leftMouthX, leftMouthY, rightMouthX, rightMouthY);
            float mouthWidth = Math.max(faceWidth * 0.28f, cornerDistance * 1.75f);
            float mouthHeight = Math.max(faceHeight * 0.16f, cornerDistance * 0.95f);

            int x = clamp(Math.round(centerX - mouthWidth * 0.5f), 0, imageWidth - 1);
            int y = clamp(Math.round(centerY - mouthHeight * 0.5f), 0, imageHeight - 1);
            int right = clamp(Math.round(centerX + mouthWidth * 0.5f), x + 1, imageWidth);
            int bottom = clamp(Math.round(centerY + mouthHeight * 0.5f), y + 1, imageHeight);
            List<DesktopPoint> mouthCorners = Arrays.asList(
                    new DesktopPoint(leftMouthX, leftMouthY),
                    new DesktopPoint(rightMouthX, rightMouthY)
            );
            return DesktopMouthRegion.trackedWithLandmarks(
                    x,
                    y,
                    right - x,
                    bottom - y,
                    String.format(Locale.US, "OpenCV YuNet mouth landmarks %.2f", indexer.get(bestRow, SCORE)),
                    mouthCorners
            );
        } finally {
            indexer.release();
        }
    }

    private static int bestDetectionRow(FloatIndexer indexer, int rows) {
        int best = 0;
        float bestScore = indexer.get(0, SCORE);
        float bestArea = indexer.get(0, FACE_WIDTH) * indexer.get(0, FACE_HEIGHT);
        for (int row = 1; row < rows; row++) {
            float score = indexer.get(row, SCORE);
            float area = indexer.get(row, FACE_WIDTH) * indexer.get(row, FACE_HEIGHT);
            if (score > bestScore || (score == bestScore && area > bestArea)) {
                best = row;
                bestScore = score;
                bestArea = area;
            }
        }
        return best;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Mat toBgrMat(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = new byte[width * height * 3];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                data[index++] = (byte) (rgb & 0xff);
                data[index++] = (byte) ((rgb >> 8) & 0xff);
                data[index++] = (byte) ((rgb >> 16) & 0xff);
            }
        }
        Mat mat = new Mat(height, width, CV_8UC3);
        mat.data().put(data);
        return mat;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String extractResource(String resourcePath, String suffix) {
        try (InputStream input = OpenCvYuNetMouthRegionProvider.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("missing resource " + resourcePath);
            }
            File file = File.createTempFile("vocagate-", suffix);
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
            throw new IllegalStateException("failed to extract " + resourcePath, e);
        }
    }
}
