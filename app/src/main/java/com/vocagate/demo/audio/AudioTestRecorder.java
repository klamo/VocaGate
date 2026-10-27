package com.vocagate.demo.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;

import com.vocagate.demo.core.MaiShaoScore;
import com.vocagate.demo.core.MaiShaoScoreCalculator;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class AudioTestRecorder {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private final File rootDir;
    private volatile boolean recording = false;
    private volatile boolean gateOpen = false;
    private boolean lastGateOpen = false;
    private int gateTransitions = 0;
    private AudioRecord audioRecord;
    private Thread worker;
    private AudioSessionFiles sessionFiles;
    private double fullEnergy = 0.0;
    private double gatedEnergy = 0.0;
    private long totalSamples = 0L;
    private long openSamples = 0L;

    public AudioTestRecorder(File rootDir) {
        this.rootDir = rootDir;
    }

    public synchronized void start() throws IOException {
        if (recording) {
            return;
        }

        resetStats();
        sessionFiles = AudioSessionFiles.prepareLatest(rootDir);

        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBufferSize <= 0) {
            throw new IOException("AudioRecord buffer size is invalid: " + minBufferSize);
        }

        int bufferSize = Math.max(minBufferSize, 4096);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            audioRecord = null;
            throw new IOException("AudioRecord failed to initialize");
        }

        recording = true;
        try {
            audioRecord.startRecording();
        } catch (IllegalStateException e) {
            audioRecord.release();
            audioRecord = null;
            recording = false;
            throw new IOException("AudioRecord failed to start", e);
        }
        worker = new Thread(() -> recordLoop(bufferSize), "VocaGateAudioRecorder");
        worker.start();
    }

    public synchronized RecordingResult stop() throws IOException {
        if (!recording) {
            return null;
        }

        recording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
                // Stop may race with failed initialization on some devices.
            }
        }

        if (worker != null) {
            try {
                worker.join(1200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }

        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        MaiShaoScore score = MaiShaoScoreCalculator.calculate(
                SAMPLE_RATE,
                totalSamples,
                openSamples,
                fullEnergy,
                gatedEnergy,
                gateTransitions
        );
        return new RecordingResult(sessionFiles.sessionDir, sessionFiles.fullWav, sessionFiles.gatedWav, score);
    }

    public boolean isRecording() {
        return recording;
    }

    public void setGateOpen(boolean open) {
        if (recording && open != lastGateOpen) {
            gateTransitions++;
            lastGateOpen = open;
        }
        gateOpen = open;
    }

    public String getLiveLabel() {
        if (!recording || totalSamples <= 0L) {
            return "Recording: off";
        }
        float seconds = totalSamples / (float) SAMPLE_RATE;
        float openPercent = 100.0f * openSamples / Math.max(1L, totalSamples);
        return String.format(Locale.US, "Recording %.1fs  Open %.1f%%", seconds, openPercent);
    }

    private void recordLoop(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        byte[] gatedBuffer = new byte[bufferSize];

        try (WavFileWriter fullWriter = new WavFileWriter(sessionFiles.fullWav, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE);
             WavFileWriter gatedWriter = new WavFileWriter(sessionFiles.gatedWav, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE)) {
            while (recording && audioRecord != null) {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                if (bytesRead <= 0) {
                    SystemClock.sleep(10L);
                    continue;
                }

                boolean open = gateOpen;
                fullWriter.writePcm(buffer, 0, bytesRead);
                if (open) {
                    gatedWriter.writePcm(buffer, 0, bytesRead);
                } else {
                    zero(gatedBuffer, bytesRead);
                    gatedWriter.writePcm(gatedBuffer, 0, bytesRead);
                }
                collectStats(buffer, bytesRead, open);
            }
        } catch (IOException ignored) {
            recording = false;
        }
    }

    private void collectStats(byte[] buffer, int byteCount, boolean open) {
        int sampleCount = byteCount / 2;
        totalSamples += sampleCount;
        if (open) {
            openSamples += sampleCount;
        }

        for (int i = 0; i + 1 < byteCount; i += 2) {
            int low = buffer[i] & 0xff;
            int high = buffer[i + 1];
            int sample = (short) ((high << 8) | low);
            double energy = sample * (double) sample;
            fullEnergy += energy;
            if (open) {
                gatedEnergy += energy;
            }
        }
    }

    private void resetStats() {
        gateOpen = false;
        lastGateOpen = false;
        gateTransitions = 0;
        fullEnergy = 0.0;
        gatedEnergy = 0.0;
        totalSamples = 0L;
        openSamples = 0L;
    }

    private void zero(byte[] data, int byteCount) {
        for (int i = 0; i < byteCount; i++) {
            data[i] = 0;
        }
    }
}
