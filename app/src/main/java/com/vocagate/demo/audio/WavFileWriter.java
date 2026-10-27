package com.vocagate.demo.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class WavFileWriter implements AutoCloseable {
    private final RandomAccessFile file;
    private final int sampleRate;
    private final int channels;
    private final int bitsPerSample;
    private long dataBytes = 0L;

    public WavFileWriter(File outputFile, int sampleRate, int channels, int bitsPerSample) throws IOException {
        this.file = new RandomAccessFile(outputFile, "rw");
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.file.setLength(0L);
        writeHeader(0L);
    }

    public synchronized void writePcm(byte[] data, int offset, int byteCount) throws IOException {
        file.write(data, offset, byteCount);
        dataBytes += byteCount;
    }

    public synchronized long getDataBytes() {
        return dataBytes;
    }

    @Override
    public synchronized void close() throws IOException {
        file.seek(0L);
        writeHeader(dataBytes);
        file.close();
    }

    private void writeHeader(long pcmBytes) throws IOException {
        long byteRate = sampleRate * channels * bitsPerSample / 8L;
        int blockAlign = channels * bitsPerSample / 8;

        file.writeBytes("RIFF");
        writeLittleEndianInt(36L + pcmBytes);
        file.writeBytes("WAVE");
        file.writeBytes("fmt ");
        writeLittleEndianInt(16L);
        writeLittleEndianShort(1);
        writeLittleEndianShort(channels);
        writeLittleEndianInt(sampleRate);
        writeLittleEndianInt(byteRate);
        writeLittleEndianShort(blockAlign);
        writeLittleEndianShort(bitsPerSample);
        file.writeBytes("data");
        writeLittleEndianInt(pcmBytes);
    }

    private void writeLittleEndianShort(int value) throws IOException {
        file.write(value & 0xff);
        file.write((value >> 8) & 0xff);
    }

    private void writeLittleEndianInt(long value) throws IOException {
        file.write((int) (value & 0xff));
        file.write((int) ((value >> 8) & 0xff));
        file.write((int) ((value >> 16) & 0xff));
        file.write((int) ((value >> 24) & 0xff));
    }
}
