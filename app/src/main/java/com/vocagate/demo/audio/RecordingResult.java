package com.vocagate.demo.audio;

import com.vocagate.demo.core.MaiShaoScore;

import java.io.File;

public final class RecordingResult {
    public final File sessionDir;
    public final File fullWav;
    public final File gatedWav;
    public final MaiShaoScore score;

    public RecordingResult(File sessionDir, File fullWav, File gatedWav, MaiShaoScore score) {
        this.sessionDir = sessionDir;
        this.fullWav = fullWav;
        this.gatedWav = gatedWav;
        this.score = score;
    }
}
