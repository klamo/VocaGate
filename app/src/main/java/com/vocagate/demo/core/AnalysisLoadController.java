package com.vocagate.demo.core;

public final class AnalysisLoadController {
    public static final int THERMAL_NONE = 0;
    public static final int THERMAL_LIGHT = 1;
    public static final int THERMAL_MODERATE = 2;
    public static final int THERMAL_SEVERE = 3;
    public static final int THERMAL_CRITICAL = 4;

    private static final long NORMAL_INTERVAL_MS = 66L;
    private static final long ECO_INTERVAL_MS = 100L;
    private static final long HOT_INTERVAL_MS = 166L;
    private static final long CRITICAL_INTERVAL_MS = 250L;
    private static final float SLOW_DETECTION_MS = 90.0f;
    private static final float LATENCY_ALPHA = 0.20f;

    private long lastAnalyzeAtMs = -1L;
    private float smoothedDetectionMs = 0.0f;

    public boolean shouldAnalyze(long nowMs, int thermalStatus) {
        long intervalMs = targetIntervalMs(thermalStatus);
        if (lastAnalyzeAtMs < 0L || nowMs - lastAnalyzeAtMs >= intervalMs) {
            lastAnalyzeAtMs = nowMs;
            return true;
        }
        return false;
    }

    public void recordDetectionLatency(float latencyMs) {
        float safeLatency = Math.max(0.0f, latencyMs);
        if (smoothedDetectionMs <= 0.0f) {
            smoothedDetectionMs = safeLatency;
        } else {
            smoothedDetectionMs = smoothedDetectionMs * (1.0f - LATENCY_ALPHA) + safeLatency * LATENCY_ALPHA;
        }
    }

    public long targetIntervalMs(int thermalStatus) {
        if (thermalStatus >= THERMAL_CRITICAL) {
            return CRITICAL_INTERVAL_MS;
        }
        if (thermalStatus >= THERMAL_SEVERE) {
            return HOT_INTERVAL_MS;
        }
        if (thermalStatus >= THERMAL_MODERATE || smoothedDetectionMs >= SLOW_DETECTION_MS) {
            return ECO_INTERVAL_MS;
        }
        return NORMAL_INTERVAL_MS;
    }

    public String modeLabel(int thermalStatus) {
        long intervalMs = targetIntervalMs(thermalStatus);
        if (thermalStatus >= THERMAL_CRITICAL) {
            return "thermal critical " + fpsLabel(intervalMs);
        }
        if (thermalStatus >= THERMAL_SEVERE) {
            return "thermal hot " + fpsLabel(intervalMs);
        }
        if (thermalStatus >= THERMAL_MODERATE) {
            return "thermal eco " + fpsLabel(intervalMs);
        }
        if (smoothedDetectionMs >= SLOW_DETECTION_MS) {
            return "eco " + fpsLabel(intervalMs);
        }
        return "normal " + fpsLabel(intervalMs);
    }

    public float smoothedDetectionMs() {
        return smoothedDetectionMs;
    }

    private static String fpsLabel(long intervalMs) {
        int fps = Math.max(1, Math.round(1000.0f / intervalMs));
        return fps + "fps";
    }
}
