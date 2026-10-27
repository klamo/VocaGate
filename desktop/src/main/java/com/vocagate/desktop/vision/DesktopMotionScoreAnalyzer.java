package com.vocagate.desktop.vision;

import com.vocagate.core.GateConfig;
import com.vocagate.core.GateState;
import com.vocagate.core.GateStateMachine;

import java.awt.image.BufferedImage;

public final class DesktopMotionScoreAnalyzer {
    private static final int MAX_SAMPLE_COLUMNS = 64;
    private static final int MAX_SAMPLE_ROWS = 48;
    private static final GateConfig DESKTOP_PROTOTYPE_CONFIG = new GateConfig(
            0.16f,
            0.08f,
            3,
            4,
            250L,
            300L,
            150L
    );
    private float[] previousLuma;
    private int previousColumns;
    private int previousRows;
    private String previousRegionLabel = "";
    private float previousMouthOpenRatio = 0.0f;
    private long previousTimeMs = -1L;
    private final DesktopMouthRegionProvider mouthRegionProvider;
    private GateStateMachine gateStateMachine = new GateStateMachine(DESKTOP_PROTOTYPE_CONFIG);

    public DesktopMotionScoreAnalyzer() {
        this(DesktopMouthRegionProviderFactory.createDefault());
    }

    public DesktopMotionScoreAnalyzer(DesktopMouthRegionProvider mouthRegionProvider) {
        this.mouthRegionProvider = mouthRegionProvider == null
                ? new CenteredMouthRegionProvider()
                : mouthRegionProvider;
    }

    public synchronized DesktopRecognitionResult analyze(BufferedImage image, long nowMs) {
        if (image == null || image.getWidth() < 16 || image.getHeight() < 16) {
            return DesktopRecognitionResult.noFrame();
        }

        DesktopMouthRegion region = mouthRegionProvider.detect(image);
        if (region == null || !region.trackingValid()) {
            previousLuma = null;
            GateState state = gateStateMachine.update(0.0f, false, nowMs);
            DesktopMouthRegion missingRegion = region == null ? DesktopMouthRegion.missing("无嘴部区域") : region;
            return new DesktopRecognitionResult(
                    0.0f,
                    0.0f,
                    0.0f,
                    false,
                    state,
                    false,
                    missingRegion,
                    missingRegion.label(),
                    "未检测到嘴部区域"
            );
        }

        Sample sample = sampleRegion(image, region);
        float mouthOpenRatio = mouthOpenRatio(region);
        if (previousLuma == null
                || previousColumns != sample.columns
                || previousRows != sample.rows
                || !previousRegionLabel.equals(region.label())) {
            remember(sample, mouthOpenRatio, nowMs);
            GateState state = gateStateMachine.update(0.0f, true, nowMs);
            return new DesktopRecognitionResult(0.0f, 0.0f, 0.0f, false, state, true, region, region.label(), "校准中");
        }

        float totalDiff = 0.0f;
        for (int i = 0; i < sample.luma.length; i++) {
            totalDiff += Math.abs(sample.luma[i] - previousLuma[i]);
        }
        float averageDiff = totalDiff / sample.luma.length / 255.0f;
        float motionScore = Math.min(1.0f, averageDiff * 2.2f);
        float landmarkScore = landmarkScore(region, mouthOpenRatio, previousMouthOpenRatio);
        float score = region.landmarks().isEmpty()
                ? motionScore
                : Math.min(1.0f, motionScore * 0.25f + landmarkScore * 0.75f);
        float fps = previousTimeMs >= 0L && nowMs > previousTimeMs
                ? 1000.0f / (nowMs - previousTimeMs)
                : 0.0f;

        remember(sample, mouthOpenRatio, nowMs);
        GateState state = gateStateMachine.update(score, true, nowMs);
        boolean likelySpeaking = state == GateState.PRE_OPEN || gateStateMachine.isGateOpen();
        return new DesktopRecognitionResult(
                score,
                motionScore,
                fps,
                likelySpeaking,
                state,
                true,
                region,
                region.label(),
                stateLabel(state)
        );
    }

    public synchronized void reset() {
        previousLuma = null;
        previousColumns = 0;
        previousRows = 0;
        previousRegionLabel = "";
        previousMouthOpenRatio = 0.0f;
        previousTimeMs = -1L;
        gateStateMachine = new GateStateMachine(DESKTOP_PROTOTYPE_CONFIG);
    }

    private String stateLabel(GateState state) {
        switch (state) {
            case PRE_OPEN:
                return "PRE_OPEN";
            case OPEN:
                return "OPEN";
            case HOLD:
                return "HOLD";
            case SUSPEND:
                return "SUSPEND";
            default:
                return "安静/未开麦";
        }
    }

    private void remember(Sample sample, float mouthOpenRatio, long nowMs) {
        previousLuma = sample.luma;
        previousColumns = sample.columns;
        previousRows = sample.rows;
        previousRegionLabel = sample.regionLabel;
        previousMouthOpenRatio = mouthOpenRatio;
        previousTimeMs = nowMs;
    }

    private float landmarkScore(DesktopMouthRegion region, float mouthOpenRatio, float previousRatio) {
        if (region.landmarks().size() < 20) {
            return 0.0f;
        }
        float openScore = clamp01(mouthOpenRatio / 0.12f);
        float rhythmScore = clamp01(Math.abs(mouthOpenRatio - previousRatio) / 0.035f);
        return clamp01(openScore * 0.42f + rhythmScore * 0.58f);
    }

    private float mouthOpenRatio(DesktopMouthRegion region) {
        if (region.landmarks().size() < 20) {
            return 0.0f;
        }
        ListAccessor points = new ListAccessor(region);
        float mouthWidth = Math.max(1.0f, Math.abs(points.x(6) - points.x(0)));
        float upperY = (points.y(13) + points.y(14) + points.y(15)) / 3.0f;
        float lowerY = (points.y(17) + points.y(18) + points.y(19)) / 3.0f;
        return clamp01(Math.abs(lowerY - upperY) / mouthWidth);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private Sample sampleRegion(BufferedImage image, DesktopMouthRegion region) {
        int width = image.getWidth();
        int height = image.getHeight();
        int cropX = Math.max(0, Math.min(width - 1, region.x()));
        int cropY = Math.max(0, Math.min(height - 1, region.y()));
        int cropWidth = Math.max(1, Math.min(region.width(), width - cropX));
        int cropHeight = Math.max(1, Math.min(region.height(), height - cropY));
        int columns = Math.min(MAX_SAMPLE_COLUMNS, cropWidth);
        int rows = Math.min(MAX_SAMPLE_ROWS, cropHeight);
        float[] luma = new float[columns * rows];

        int index = 0;
        for (int row = 0; row < rows; row++) {
            int y = cropY + Math.min(cropHeight - 1, Math.round((row + 0.5f) * cropHeight / rows));
            for (int column = 0; column < columns; column++) {
                int x = cropX + Math.min(cropWidth - 1, Math.round((column + 0.5f) * cropWidth / columns));
                luma[index++] = luminance(image.getRGB(x, y));
            }
        }
        return new Sample(luma, columns, rows, region.label());
    }

    private float luminance(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return red * 0.299f + green * 0.587f + blue * 0.114f;
    }

    private static final class Sample {
        private final float[] luma;
        private final int columns;
        private final int rows;
        private final String regionLabel;

        private Sample(float[] luma, int columns, int rows, String regionLabel) {
            this.luma = luma;
            this.columns = columns;
            this.rows = rows;
            this.regionLabel = regionLabel;
        }
    }

    private static final class ListAccessor {
        private final DesktopMouthRegion region;

        private ListAccessor(DesktopMouthRegion region) {
            this.region = region;
        }

        private float x(int index) {
            return region.landmarks().get(index).x();
        }

        private float y(int index) {
            return region.landmarks().get(index).y();
        }
    }
}
