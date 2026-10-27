package com.vocagate.demo.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;

import com.vocagate.demo.core.AudioAssistFrame;
import com.vocagate.demo.core.AudioAssistSignalAnalyzer;

import java.io.IOException;

public final class AudioAssistMonitor {
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_BYTES = 640;

    private final AudioAssistSignalAnalyzer analyzer = new AudioAssistSignalAnalyzer(SAMPLE_RATE);
    private volatile boolean running = false;
    private volatile AudioAssistFrame latestFrame = AudioAssistFrame.SILENCE;
    private AudioRecord audioRecord;
    private Thread worker;

    public synchronized void start() throws IOException, SecurityException {
        if (running) {
            return;
        }

        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBufferSize <= 0) {
            throw new IOException("Audio assist buffer size is invalid: " + minBufferSize);
        }

        int bufferSize = Math.max(minBufferSize, FRAME_BYTES * 4);
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
            throw new IOException("Audio assist failed to initialize");
        }

        try {
            audioRecord.startRecording();
        } catch (IllegalStateException e) {
            audioRecord.release();
            audioRecord = null;
            throw new IOException("Audio assist failed to start", e);
        }

        running = true;
        worker = new Thread(this::readLoop, "VocaGateAudioAssist");
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
                // Best effort cleanup.
            }
        }
        if (worker != null) {
            try {
                worker.join(800L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        latestFrame = AudioAssistFrame.SILENCE;
    }

    public AudioAssistFrame latestFrame() {
        return latestFrame;
    }

    public boolean isRunning() {
        return running;
    }

    private void readLoop() {
        byte[] buffer = new byte[FRAME_BYTES];
        while (running && audioRecord != null) {
            int bytesRead = audioRecord.read(buffer, 0, buffer.length);
            if (bytesRead <= 0) {
                SystemClock.sleep(10L);
                continue;
            }
            latestFrame = analyzer.analyzePcm16(buffer, 0, bytesRead, SystemClock.elapsedRealtime());
        }
    }
}
