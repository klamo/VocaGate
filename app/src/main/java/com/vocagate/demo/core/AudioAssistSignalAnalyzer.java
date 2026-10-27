package com.vocagate.demo.core;

public final class AudioAssistSignalAnalyzer {
    private static final float MIN_RMS = 0.000001f;

    private final int sampleRate;
    private float noiseFloorDb = -62.0f;

    public AudioAssistSignalAnalyzer(int sampleRate) {
        this.sampleRate = Math.max(8000, sampleRate);
    }

    public synchronized AudioAssistFrame analyzePcm16(byte[] buffer, int offset, int byteCount, long timestampMs) {
        if (buffer == null || byteCount < 2 || offset < 0 || offset >= buffer.length) {
            return new AudioAssistFrame(timestampMs, -120.0f, noiseFloorDb, 0.0f, 0.0f);
        }

        int safeByteCount = Math.min(byteCount, buffer.length - offset);
        int sampleCount = safeByteCount / 2;
        if (sampleCount <= 0) {
            return new AudioAssistFrame(timestampMs, -120.0f, noiseFloorDb, 0.0f, 0.0f);
        }

        double sumSquares = 0.0;
        int zeroCrossings = 0;
        int previousSign = 0;
        for (int i = 0; i < sampleCount; i++) {
            int byteIndex = offset + (i * 2);
            short sample = (short) ((buffer[byteIndex] & 0xFF) | (buffer[byteIndex + 1] << 8));
            float normalized = sample / 32768.0f;
            sumSquares += normalized * normalized;

            int sign = sample > 0 ? 1 : (sample < 0 ? -1 : 0);
            if (sign != 0 && previousSign != 0 && sign != previousSign) {
                zeroCrossings++;
            }
            if (sign != 0) {
                previousSign = sign;
            }
        }

        float rms = (float) Math.sqrt(sumSquares / sampleCount);
        float rmsDb = 20.0f * (float) Math.log10(Math.max(MIN_RMS, rms));
        float currentNoiseFloor = noiseFloorDb;
        float aboveNoiseDb = rmsDb - currentNoiseFloor;
        float energyScore = clamp01((aboveNoiseDb - 8.0f) / 24.0f);
        float riseScore = clamp01((aboveNoiseDb - 3.0f) / 18.0f);
        float zeroCrossingRate = zeroCrossings / (float) Math.max(1, sampleCount);
        float zcrScore = voiceLikeZeroCrossingScore(zeroCrossingRate);
        float voiceConfidence = clamp01((energyScore * 0.85f) + (zcrScore * 0.15f));

        adaptNoiseFloor(rmsDb);
        return new AudioAssistFrame(timestampMs, rmsDb, currentNoiseFloor, voiceConfidence, riseScore);
    }

    public int sampleRate() {
        return sampleRate;
    }

    public synchronized float noiseFloorDb() {
        return noiseFloorDb;
    }

    private void adaptNoiseFloor(float rmsDb) {
        if (rmsDb < noiseFloorDb + 3.0f) {
            noiseFloorDb = (noiseFloorDb * 0.90f) + (rmsDb * 0.10f);
        } else {
            noiseFloorDb = (noiseFloorDb * 0.995f) + (rmsDb * 0.005f);
        }
        noiseFloorDb = Math.max(-90.0f, Math.min(-20.0f, noiseFloorDb));
    }

    private float voiceLikeZeroCrossingScore(float zeroCrossingRate) {
        float min = 0.010f;
        float max = 0.200f;
        if (zeroCrossingRate <= 0.0f) {
            return 0.0f;
        }
        if (zeroCrossingRate < min) {
            return clamp01(zeroCrossingRate / min);
        }
        if (zeroCrossingRate > max) {
            return clamp01(1.0f - ((zeroCrossingRate - max) / 0.20f));
        }
        return 1.0f;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
