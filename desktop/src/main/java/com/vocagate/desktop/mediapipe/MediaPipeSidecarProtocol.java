package com.vocagate.desktop.mediapipe;

import com.vocagate.desktop.vision.DesktopMouthRegion;
import com.vocagate.desktop.vision.DesktopPoint;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaPipeSidecarProtocol {
    private static final Pattern JSON_PAIR = Pattern.compile(
            "\\[\\s*(" + numberPattern() + ")\\s*,\\s*(" + numberPattern() + ")\\s*\\]"
    );

    private MediaPipeSidecarProtocol() {
    }

    public static String frameRequestLine(BufferedImage image, long timestampMs, int maxWidth) {
        if (image == null) {
            throw new IllegalArgumentException("image is required");
        }
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        int targetWidth = Math.max(1, Math.min(sourceWidth, maxWidth <= 0 ? sourceWidth : maxWidth));
        int targetHeight = Math.max(1, Math.round(sourceHeight * (targetWidth / (float) sourceWidth)));
        BufferedImage scaled = scale(image, targetWidth, targetHeight);
        String encodedRgb = Base64.getEncoder().encodeToString(rgbBytes(scaled));

        return "{"
                + "\"type\":\"frame\","
                + "\"timestamp_ms\":" + timestampMs + ","
                + "\"source_width\":" + sourceWidth + ","
                + "\"source_height\":" + sourceHeight + ","
                + "\"width\":" + targetWidth + ","
                + "\"height\":" + targetHeight + ","
                + "\"rgb\":\"" + encodedRgb + "\""
                + "}";
    }

    public static Optional<MediaPipeSidecarFrameResult> parseRecognitionLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return Optional.empty();
        }

        String type = stringField(line, "type", "");
        if (!"recognition".equals(type)) {
            return Optional.empty();
        }

        String status = stringField(line, "status", "MediaPipe");
        boolean trackingValid = booleanField(line, "tracking_valid", false);
        float score = floatField(line, "score", 0.0f);
        float motion = floatField(line, "motion", 0.0f);
        float fps = floatField(line, "fps", 0.0f);
        DesktopMouthRegion region = parseMouthRegion(line, trackingValid, status);

        return Optional.of(new MediaPipeSidecarFrameResult(
                score,
                motion,
                fps,
                trackingValid,
                region,
                status
        ));
    }

    private static BufferedImage scale(BufferedImage image, int width, int height) {
        if (image.getWidth() == width && image.getHeight() == height && image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static byte[] rgbBytes(BufferedImage image) {
        byte[] bytes = new byte[image.getWidth() * image.getHeight() * 3];
        int index = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                bytes[index++] = (byte) ((rgb >> 16) & 0xff);
                bytes[index++] = (byte) ((rgb >> 8) & 0xff);
                bytes[index++] = (byte) (rgb & 0xff);
            }
        }
        return bytes;
    }

    private static DesktopMouthRegion parseMouthRegion(String line, boolean trackingValid, String status) {
        String roi = nestedJsonValue(line, "roi", '{', '}');
        if (!trackingValid || roi == null) {
            return DesktopMouthRegion.missing(status);
        }

        int x = Math.round(floatField(roi, "x", 0.0f));
        int y = Math.round(floatField(roi, "y", 0.0f));
        int width = Math.round(floatField(roi, "width", 0.0f));
        int height = Math.round(floatField(roi, "height", 0.0f));
        String label = stringField(roi, "label", "MediaPipe lips");
        return DesktopMouthRegion.trackedWithLandmarks(
                x,
                y,
                width,
                height,
                label,
                parseLandmarks(line)
        );
    }

    private static List<DesktopPoint> parseLandmarks(String line) {
        String landmarksJson = nestedJsonValue(line, "landmarks", '[', ']');
        List<DesktopPoint> points = new ArrayList<>();
        if (landmarksJson == null) {
            return points;
        }

        Matcher matcher = JSON_PAIR.matcher(landmarksJson);
        while (matcher.find()) {
            points.add(new DesktopPoint(parseFloat(matcher.group(1), 0.0f), parseFloat(matcher.group(2), 0.0f)));
        }
        return points;
    }

    private static String nestedJsonValue(String json, String key, char open, char close) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != open) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = inString;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String stringField(String json, String key, String fallback) {
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        return unescapeJsonString(matcher.group(1));
    }

    private static boolean booleanField(String json, String key, boolean fallback) {
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)")
                .matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static float floatField(String json, String key, float fallback) {
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(" + numberPattern() + ")")
                .matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        return parseFloat(matcher.group(1), fallback);
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String numberPattern() {
        return "-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?";
    }

    private static String unescapeJsonString(String raw) {
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch != '\\' || i + 1 >= raw.length()) {
                builder.append(ch);
                continue;
            }
            char escaped = raw.charAt(++i);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    builder.append(escaped);
                    break;
                case 'b':
                    builder.append('\b');
                    break;
                case 'f':
                    builder.append('\f');
                    break;
                case 'n':
                    builder.append('\n');
                    break;
                case 'r':
                    builder.append('\r');
                    break;
                case 't':
                    builder.append('\t');
                    break;
                case 'u':
                    if (i + 4 < raw.length()) {
                        builder.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    break;
                default:
                    builder.append(escaped);
                    break;
            }
        }
        return builder.toString();
    }
}
