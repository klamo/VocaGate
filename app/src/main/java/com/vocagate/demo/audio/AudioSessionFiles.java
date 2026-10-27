package com.vocagate.demo.audio;

import java.io.File;
import java.io.IOException;

public final class AudioSessionFiles {
    public final File sessionDir;
    public final File fullWav;
    public final File gatedWav;

    private AudioSessionFiles(File sessionDir) {
        this.sessionDir = sessionDir;
        this.fullWav = new File(sessionDir, "full.wav");
        this.gatedWav = new File(sessionDir, "gated.wav");
    }

    public static AudioSessionFiles prepareLatest(File rootDir) throws IOException {
        deleteRecursively(rootDir);
        if (!rootDir.mkdirs() && !rootDir.isDirectory()) {
            throw new IOException("Failed to create recording root: " + rootDir);
        }

        File latest = new File(rootDir, "latest");
        if (!latest.mkdirs() && !latest.isDirectory()) {
            throw new IOException("Failed to create latest recording session: " + latest);
        }
        return new AudioSessionFiles(latest);
    }

    public static AudioSessionFiles latest(File rootDir) {
        return new AudioSessionFiles(new File(rootDir, "latest"));
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }

        if (!file.delete()) {
            throw new IOException("Failed to delete: " + file);
        }
    }
}
