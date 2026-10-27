package com.vocagate.demo;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class RecordingFilesActivity extends ComponentActivity {
    public static final String EXTRA_FULL_WAV = "full_wav";
    public static final String EXTRA_GATED_WAV = "gated_wav";
    public static final String EXTRA_SCORE = "score";
    public static final String EXTRA_SUPPRESSION = "suppression";
    public static final String EXTRA_OPEN_PERCENT = "open_percent";
    public static final String EXTRA_TRANSITIONS = "transitions";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PlayerPanel fullPanel;
    private PlayerPanel gatedPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String fullPath = getIntent().getStringExtra(EXTRA_FULL_WAV);
        String gatedPath = getIntent().getStringExtra(EXTRA_GATED_WAV);
        float score = getIntent().getFloatExtra(EXTRA_SCORE, 0.0f);
        float suppression = getIntent().getFloatExtra(EXTRA_SUPPRESSION, 0.0f);
        float openPercent = getIntent().getFloatExtra(EXTRA_OPEN_PERCENT, 0.0f);
        float transitions = getIntent().getFloatExtra(EXTRA_TRANSITIONS, 0.0f);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollView.addView(root, matchWrap());

        TextView title = new TextView(this);
        title.setTextSize(22.0f);
        title.setText("录音文件");
        root.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setTextSize(14.0f);
        summary.setText(String.format(
                Locale.US,
                "麦哨值 %.1f\n抑制 %.1f%%   开麦 %.1f%%   抖动 %.1f/min",
                score,
                suppression,
                openPercent,
                transitions
        ));
        summary.setPadding(0, dp(10), 0, dp(14));
        root.addView(summary, matchWrap());

        fullPanel = new PlayerPanel("全程录音 full.wav", fullPath);
        gatedPanel = new PlayerPanel("麦哨录音 gated.wav", gatedPath);
        root.addView(fullPanel.container, matchWrap());
        root.addView(gatedPanel.container, matchWrap());

        Button backButton = new Button(this);
        backButton.setText("返回");
        backButton.setAllCaps(false);
        backButton.setOnClickListener(view -> finish());
        root.addView(backButton, matchWrap());

        setContentView(scrollView);
        handler.post(updateProgressRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateProgressRunnable);
        if (fullPanel != null) {
            fullPanel.release();
        }
        if (gatedPanel != null) {
            gatedPanel.release();
        }
    }

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (fullPanel != null) {
                fullPanel.updateProgress();
            }
            if (gatedPanel != null) {
                gatedPanel.updateProgress();
            }
            handler.postDelayed(this, 250L);
        }
    };

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void pauseOther(PlayerPanel current) {
        if (fullPanel != null && fullPanel != current) {
            fullPanel.pause();
        }
        if (gatedPanel != null && gatedPanel != current) {
            gatedPanel.pause();
        }
    }

    private final class PlayerPanel {
        private final LinearLayout container;
        private final TextView status;
        private final Button playButton;
        private final SeekBar seekBar;
        private MediaPlayer player;
        private boolean prepared = false;

        private PlayerPanel(String title, String path) {
            container = new LinearLayout(RecordingFilesActivity.this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(0, dp(8), 0, dp(18));

            TextView titleView = new TextView(RecordingFilesActivity.this);
            titleView.setTextSize(16.0f);
            titleView.setText(title);
            container.addView(titleView, matchWrap());

            status = new TextView(RecordingFilesActivity.this);
            status.setTextSize(12.0f);
            status.setText("未找到文件");
            container.addView(status, matchWrap());

            seekBar = new SeekBar(RecordingFilesActivity.this);
            seekBar.setEnabled(false);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && prepared && player != null) {
                        player.seekTo(progress);
                        status.setText(formatTime(progress) + " / " + formatTime(player.getDuration()));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // No-op.
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // No-op.
                }
            });
            container.addView(seekBar, matchWrap());

            playButton = new Button(RecordingFilesActivity.this);
            playButton.setText("播放");
            playButton.setAllCaps(false);
            playButton.setEnabled(false);
            playButton.setOnClickListener(view -> toggle());
            container.addView(playButton, matchWrap());

            prepare(path);
        }

        private void prepare(String path) {
            if (path == null) {
                return;
            }

            File file = new File(path);
            if (!file.exists()) {
                status.setText("文件不存在：" + path);
                return;
            }

            player = new MediaPlayer();
            try {
                player.setDataSource(file.getAbsolutePath());
                player.prepare();
                player.setOnCompletionListener(mediaPlayer -> {
                    playButton.setText("播放");
                    seekBar.setProgress(0);
                    player.seekTo(0);
                });
                prepared = true;
                seekBar.setEnabled(true);
                seekBar.setMax(player.getDuration());
                playButton.setEnabled(true);
                status.setText(formatTime(0) + " / " + formatTime(player.getDuration()));
            } catch (IOException e) {
                status.setText("无法播放：" + e.getMessage());
                release();
            }
        }

        private void toggle() {
            if (!prepared || player == null) {
                return;
            }

            if (player.isPlaying()) {
                pause();
            } else {
                pauseOther(this);
                player.start();
                playButton.setText("暂停");
            }
        }

        private void pause() {
            if (prepared && player != null && player.isPlaying()) {
                player.pause();
            }
            playButton.setText("播放");
        }

        private void updateProgress() {
            if (!prepared || player == null) {
                return;
            }

            int current = player.getCurrentPosition();
            int duration = Math.max(1, player.getDuration());
            seekBar.setProgress(current);
            status.setText(formatTime(current) + " / " + formatTime(duration));
        }

        private void release() {
            if (player != null) {
                player.release();
                player = null;
            }
            prepared = false;
        }

        private String formatTime(int ms) {
            int totalSeconds = Math.max(0, ms / 1000);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
    }
}
