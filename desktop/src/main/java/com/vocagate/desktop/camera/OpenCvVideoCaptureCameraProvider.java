package com.vocagate.desktop.camera;

import com.vocagate.desktop.app.DesktopCameraDevice;
import com.vocagate.desktop.app.DesktopCameraPreviewSession;
import com.vocagate.desktop.app.DesktopCameraProvider;
import com.vocagate.desktop.vision.DesktopMouthRegion;
import com.vocagate.desktop.vision.DesktopRecognitionResult;

import com.vocagate.core.GateState;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH;

public final class OpenCvVideoCaptureCameraProvider implements DesktopCameraProvider {
    private static final int MAX_CAMERA_INDEX = 3;
    private static final String ID_PREFIX = "opencv:";

    @Override
    public List<DesktopCameraDevice> listDevices() {
        List<DesktopCameraDevice> devices = new ArrayList<>();
        for (int index = 0; index <= MAX_CAMERA_INDEX; index++) {
            if (canOpen(index)) {
                devices.add(new DesktopCameraDevice(idFor(index), displayNameFor(index)));
            }
        }
        return devices;
    }

    @Override
    public DesktopCameraPreviewSession openPreview(DesktopCameraDevice device) {
        int index = indexFrom(device);
        VideoCapture capture = new VideoCapture(index);
        if (!capture.isOpened()) {
            capture.release();
            throw new IllegalArgumentException("OpenCV camera not available: " + device.displayName());
        }
        capture.set(CAP_PROP_FRAME_WIDTH, 640.0);
        capture.set(CAP_PROP_FRAME_HEIGHT, 480.0);
        return new OpenCvPreviewSession(capture);
    }

    private boolean canOpen(int index) {
        VideoCapture capture = null;
        try {
            capture = new VideoCapture(index);
            if (!capture.isOpened()) {
                return false;
            }
            Mat frame = new Mat();
            try {
                return capture.read(frame) && !frame.empty();
            } finally {
                frame.close();
            }
        } catch (RuntimeException | LinkageError e) {
            return false;
        } finally {
            if (capture != null) {
                capture.release();
            }
        }
    }

    private String idFor(int index) {
        return ID_PREFIX + index;
    }

    private String displayNameFor(int index) {
        return String.format(Locale.US, "摄像头 %d", index);
    }

    private int indexFrom(DesktopCameraDevice device) {
        String id = device.id();
        if (!id.startsWith(ID_PREFIX)) {
            throw new IllegalArgumentException("not an OpenCV camera id: " + id);
        }
        return Integer.parseInt(id.substring(ID_PREFIX.length()));
    }

    private static final class OpenCvPreviewSession implements DesktopCameraPreviewSession {
        private final Object captureLock = new Object();
        private final VideoCapture capture;
        private final PreviewPanel panel = new PreviewPanel();
        private final ScheduledExecutorService executor;
        private volatile BufferedImage latestFrame;
        private volatile DesktopRecognitionResult latestRecognitionResult = DesktopRecognitionResult.noFrame();
        private volatile boolean running = true;

        private OpenCvPreviewSession(VideoCapture capture) {
            this.capture = capture;
            panel.setPreferredSize(new Dimension(520, 260));
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "vocagate-opencv-camera-preview");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleAtFixedRate(this::readFrame, 0L, 33L, TimeUnit.MILLISECONDS);
        }

        @Override
        public JComponent component() {
            return panel;
        }

        @Override
        public BufferedImage snapshot() {
            return latestFrame;
        }

        @Override
        public void updateRecognitionResult(DesktopRecognitionResult result) {
            latestRecognitionResult = result == null ? DesktopRecognitionResult.noFrame() : result;
            SwingUtilities.invokeLater(panel::repaint);
        }

        @Override
        public void close() {
            running = false;
            executor.shutdownNow();
            synchronized (captureLock) {
                capture.release();
            }
        }

        private void readFrame() {
            if (!running) {
                return;
            }
            Mat frame = new Mat();
            try {
                boolean ok;
                synchronized (captureLock) {
                    ok = capture.isOpened() && capture.read(frame);
                }
                if (!ok || frame.empty()) {
                    return;
                }
                BufferedImage image = toBufferedImage(frame);
                latestFrame = image;
                SwingUtilities.invokeLater(panel::repaint);
            } catch (RuntimeException | LinkageError ignored) {
            } finally {
                frame.close();
            }
        }

        private BufferedImage toBufferedImage(Mat mat) {
            int width = mat.cols();
            int height = mat.rows();
            int channels = mat.channels();
            int stride = (int) mat.step();
            byte[] data = new byte[stride * height];
            mat.data().get(data);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                int rowOffset = y * stride;
                for (int x = 0; x < width; x++) {
                    int offset = rowOffset + x * channels;
                    int red;
                    int green;
                    int blue;
                    if (channels >= 3) {
                        blue = data[offset] & 0xff;
                        green = data[offset + 1] & 0xff;
                        red = data[offset + 2] & 0xff;
                    } else {
                        red = data[offset] & 0xff;
                        green = red;
                        blue = red;
                    }
                    image.setRGB(x, y, (red << 16) | (green << 8) | blue);
                }
            }
            return image;
        }

        private final class PreviewPanel extends JPanel {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                try {
                    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics2D.setColor(Color.BLACK);
                    graphics2D.fillRect(0, 0, getWidth(), getHeight());
                    drawFrameAndOverlay(graphics2D);
                } finally {
                    graphics2D.dispose();
                }
            }

            private void drawFrameAndOverlay(Graphics2D graphics) {
                BufferedImage image = latestFrame;
                if (image == null) {
                    graphics.setColor(Color.WHITE);
                    graphics.drawString("Waiting for camera frame", 16, 24);
                    return;
                }
                double scale = Math.min(
                        getWidth() / (double) image.getWidth(),
                        getHeight() / (double) image.getHeight()
                );
                int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
                int x = (getWidth() - drawWidth) / 2;
                int y = (getHeight() - drawHeight) / 2;
                graphics.drawImage(image, x, y, drawWidth, drawHeight, null);
                drawRecognitionOverlay(graphics, x, y, scale);
            }

            private void drawRecognitionOverlay(Graphics2D graphics, int imageX, int imageY, double scale) {
                DesktopRecognitionResult result = latestRecognitionResult;
                Color stateColor = stateColor(result);

                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
                drawStatusBadge(graphics, result, stateColor);
                drawScoreBar(graphics, result, stateColor);

                DesktopMouthRegion mouthRegion = result.mouthRegion();
                if (!result.trackingValid() || mouthRegion == null || !mouthRegion.trackingValid()) {
                    return;
                }

                int mouthX = imageX + (int) Math.round(mouthRegion.x() * scale);
                int mouthY = imageY + (int) Math.round(mouthRegion.y() * scale);
                int mouthWidth = Math.max(1, (int) Math.round(mouthRegion.width() * scale));
                int mouthHeight = Math.max(1, (int) Math.round(mouthRegion.height() * scale));

                graphics.setStroke(new BasicStroke(3f));
                graphics.setColor(stateColor);
                graphics.drawRoundRect(mouthX, mouthY, mouthWidth, mouthHeight, 14, 14);

                drawLandmarks(graphics, mouthRegion, imageX, imageY, scale, stateColor);

                int centerY = mouthY + mouthHeight / 2;
                graphics.setStroke(new BasicStroke(2f));
                graphics.drawLine(mouthX + 6, centerY, mouthX + mouthWidth - 6, centerY);

                String label = "嘴部 ROI";
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                int labelY = Math.max(18, mouthY - 8);
                drawTextPill(graphics, label, mouthX, labelY - 18, stateColor);
            }

            private void drawLandmarks(
                    Graphics2D graphics,
                    DesktopMouthRegion mouthRegion,
                    int imageX,
                    int imageY,
                    double scale,
                    Color stateColor
            ) {
                if (mouthRegion.landmarks().isEmpty()) {
                    return;
                }
                graphics.setColor(stateColor);
                graphics.setStroke(new BasicStroke(2f));
                for (int i = 0; i < mouthRegion.landmarks().size(); i++) {
                    int next = i == mouthRegion.landmarks().size() - 1 ? 0 : i + 1;
                    int x1 = imageX + Math.round(mouthRegion.landmarks().get(i).x() * (float) scale);
                    int y1 = imageY + Math.round(mouthRegion.landmarks().get(i).y() * (float) scale);
                    int x2 = imageX + Math.round(mouthRegion.landmarks().get(next).x() * (float) scale);
                    int y2 = imageY + Math.round(mouthRegion.landmarks().get(next).y() * (float) scale);
                    graphics.drawLine(x1, y1, x2, y2);
                    graphics.fillOval(x1 - 3, y1 - 3, 6, 6);
                }
            }

            private void drawStatusBadge(Graphics2D graphics, DesktopRecognitionResult result, Color stateColor) {
                String text = result.statusLabel() + "  score=" + String.format(Locale.US, "%.2f", result.score());
                drawTextPill(graphics, text, 16, 16, stateColor);
            }

            private void drawScoreBar(Graphics2D graphics, DesktopRecognitionResult result, Color stateColor) {
                int margin = 16;
                int barWidth = Math.min(320, Math.max(120, getWidth() - margin * 2));
                int barHeight = 12;
                int x = margin;
                int y = getHeight() - margin - barHeight;

                graphics.setComposite(AlphaComposite.SrcOver.derive(0.68f));
                graphics.setColor(Color.DARK_GRAY);
                graphics.fillRoundRect(x, y, barWidth, barHeight, barHeight, barHeight);
                graphics.setColor(stateColor);
                graphics.fillRoundRect(x, y, Math.round(barWidth * result.score()), barHeight, barHeight, barHeight);
                graphics.setComposite(AlphaComposite.SrcOver);
            }

            private void drawTextPill(Graphics2D graphics, String text, int x, int y, Color color) {
                int paddingX = 10;
                int paddingY = 5;
                int width = graphics.getFontMetrics().stringWidth(text) + paddingX * 2;
                int height = graphics.getFontMetrics().getHeight() + paddingY;

                graphics.setComposite(AlphaComposite.SrcOver.derive(0.78f));
                graphics.setColor(new Color(0, 0, 0));
                graphics.fillRoundRect(x, y, width, height, 12, 12);
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.setColor(color);
                graphics.drawRoundRect(x, y, width, height, 12, 12);
                graphics.setColor(Color.WHITE);
                graphics.drawString(text, x + paddingX, y + height - paddingY - 2);
            }

            private Color stateColor(DesktopRecognitionResult result) {
                GateState state = result.state();
                if (result.isLikelySpeaking() || state == GateState.OPEN || state == GateState.PRE_OPEN || state == GateState.HOLD) {
                    return new Color(30, 200, 95);
                }
                if (!result.trackingValid()) {
                    return new Color(235, 176, 64);
                }
                return new Color(72, 154, 255);
            }
        }
    }
}
