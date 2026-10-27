package com.vocagate.demo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.vocagate.demo.audio.AudioAssistMonitor;
import com.vocagate.demo.audio.AudioTestRecorder;
import com.vocagate.demo.audio.RecordingResult;
import com.vocagate.core.GateConfig;
import com.vocagate.core.GateState;
import com.vocagate.core.GateStateMachine;
import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.core.update.HttpUrlUpdateClient;
import com.vocagate.core.update.UpdateCheckClient;
import com.vocagate.core.update.UpdateCheckResult;
import com.vocagate.core.update.UpdateClientConfig;
import com.vocagate.core.update.UpdatePreferences;
import com.vocagate.core.update.UpdateProtocolParser;
import com.vocagate.core.update.UpdateSchedule;
import com.vocagate.demo.core.AnalysisLoadController;
import com.vocagate.demo.core.AudioAssistConfig;
import com.vocagate.demo.core.AudioAssistFrame;
import com.vocagate.demo.core.AudioVisualFusion;
import com.vocagate.demo.core.FirstWordProtection;
import com.vocagate.demo.core.HeadPoseCalibrator;
import com.vocagate.demo.core.MaiShaoScore;
import com.vocagate.demo.core.MouthFeatureExtractor;
import com.vocagate.demo.core.MouthFeatures;
import com.vocagate.demo.core.MouthPoseNormalizer;
import com.vocagate.demo.core.OverlayCoordinateMapper;
import com.vocagate.demo.core.Point2;
import com.vocagate.demo.core.ResponsiveControlLayout;
import com.vocagate.demo.core.ScoreConfig;
import com.vocagate.demo.core.ScreenShieldState;
import com.vocagate.demo.core.ShieldHudState;
import com.vocagate.demo.core.SpeakScoreEstimator;
import com.vocagate.demo.core.StatusTextFormatter;
import com.vocagate.demo.core.UserTuning;
import com.vocagate.demo.pc.PcLinkClient;
import com.vocagate.demo.pc.PcLinkMode;
import com.vocagate.demo.pc.PcLinkSettings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 42;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };
    private static final int ANALYSIS_WIDTH = 640;
    private static final int ANALYSIS_HEIGHT = 360;
    private static final String PREFS_PC_LINK = "pc_link";
    private static final String PREFS_UPDATE = "update";
    private static final String PREF_MODE = "mode";
    private static final String PREF_WIFI_HOST = "wifi_host";
    private static final String PREF_PAIRING_CODE = "pairing_code";
    private static final String PREF_PORT = "port";
    private static final String PREF_UPDATE_CHECKS_ENABLED = "update_checks_enabled";
    private static final String PREF_ANONYMOUS_STATS_ENABLED = "anonymous_stats_enabled";
    private static final String PREF_UPDATE_SERVER_URL = "update_server_url";
    private static final String PREF_UPDATE_LAST_CHECKED_AT_MS = "update_last_checked_at_ms";
    private static final String PREF_UPDATE_LAST_PROFILE_ID = "update_last_profile_id";
    private static final String PREF_UPDATE_LAST_PROFILE_JSON = "update_last_profile_json";
    private static final String PREF_UPDATE_LAST_PROFILE_UPDATED_AT_MS = "update_last_profile_updated_at_ms";
    private static final String ANDROID_APP_VERSION = "0.1.0";

    private PreviewView previewView;
    private MouthOverlayView overlayView;
    private FrameLayout rootView;
    private LinearLayout controlsPanel;
    private TextView statusView;
    private TextView recordingView;
    private TextView sensitivityLabel;
    private TextView earlyPressLabel;
    private TextView responseLabel;
    private TextView holdLabel;
    private TextView firstWordProtectionLabel;
    private TextView audioAssistLabel;
    private Button recordButton;
    private Button filesButton;
    private Button settingsButton;
    private Button screenShieldButton;
    private ShieldHudView screenShieldView;
    private LinearLayout settingsPanel;
    private SeekBar sensitivitySeekBar;
    private SeekBar earlyPressSeekBar;
    private SeekBar responseSeekBar;
    private SeekBar holdSeekBar;
    private Spinner firstWordProtectionSpinner;
    private Spinner audioAssistSpinner;
    private Spinner pcModeSpinner;
    private EditText wifiHostEditText;
    private EditText pairingCodeEditText;
    private EditText pcPortEditText;
    private TextView pcConfigLabel;
    private ExecutorService cameraExecutor;
    private ExecutorService updateExecutor;
    private FaceDetector detector;
    private AudioTestRecorder audioRecorder;
    private AudioAssistMonitor audioAssistMonitor;
    private PcLinkClient pcLinkClient;
    private SharedPreferences pcLinkPreferences;
    private SharedPreferences updatePreferences;
    private VocaGateAnalyzer analyzer;
    private ProcessCameraProvider boundCameraProvider;
    private CameraSelector boundCameraSelector;
    private Preview previewUseCase;
    private ImageAnalysis analysisUseCase;
    private UserTuning currentTuning = UserTuning.defaults();
    private volatile AlgorithmProfile cachedAlgorithmProfile;
    private final ScreenShieldState screenShieldState = new ScreenShieldState();
    private RecordingResult latestRecordingResult;
    private String pcLinkStatus = "PC: off";
    private String lastShieldLabel = "Waiting";
    private GateState lastShieldState = GateState.CLOSED;
    private boolean lastShieldGateOpen = false;
    private float lastShieldScore = 0.0f;
    private boolean compactStatusLayout = false;
    private boolean responsiveLayoutApplied = false;
    private boolean lastResponsiveSidePanel = false;
    private int lastResponsivePanelWidthPx = -1;
    private volatile int thermalStatus = AnalysisLoadController.THERMAL_NONE;
    private PowerManager.OnThermalStatusChangedListener thermalStatusListener;
    private final Executor mainExecutor = command -> new Handler(Looper.getMainLooper()).post(command);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        startThermalMonitoring();
        pcLinkPreferences = getSharedPreferences(PREFS_PC_LINK, MODE_PRIVATE);
        updatePreferences = getSharedPreferences(PREFS_UPDATE, MODE_PRIVATE);
        cachedAlgorithmProfile = loadCachedAlgorithmProfile();
        cameraExecutor = Executors.newSingleThreadExecutor();
        updateExecutor = Executors.newSingleThreadExecutor();
        buildUi();
        maybeAutoCheckAlgorithmUpdates();
        File externalMusicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File recordingBaseDir = externalMusicDir == null ? getFilesDir() : externalMusicDir;
        File recordingRoot = new File(recordingBaseDir, "vocagate-tests");
        audioRecorder = new AudioTestRecorder(recordingRoot);
        audioAssistMonitor = new AudioAssistMonitor();
        pcLinkClient = new PcLinkClient(status -> runOnUiThread(() -> {
            pcLinkStatus = status;
            refreshShieldHudFromLastStatus();
        }));
        pcLinkClient.updateSettings(currentPcLinkSettings());
        pcLinkClient.start();

        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .enableTracking()
                .build());

        if (hasRequiredPermissions()) {
            startCamera();
            startAudioAssistMonitor();
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && hasRequiredPermissions()) {
            startCamera();
            startAudioAssistMonitor();
        } else {
            showStatus("Camera or mic permission denied", GateState.SUSPEND, false, 0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    private void startAudioAssistMonitor() {
        if (audioAssistMonitor == null || audioAssistMonitor.isRunning()) {
            return;
        }
        try {
            audioAssistMonitor.start();
        } catch (IOException | SecurityException ignored) {
            // Audio assist is optional; visual recognition must keep working if mic capture is unavailable.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updatePreferences != null) {
            applyAlgorithmProfile(loadCachedAlgorithmProfile());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deactivateScreenShield();
        stopThermalMonitoring();
        if (detector != null) {
            detector.close();
        }
        if (audioRecorder != null && audioRecorder.isRecording()) {
            try {
                audioRecorder.stop();
            } catch (IOException ignored) {
                // App is closing; best effort cleanup.
            }
        }
        if (audioAssistMonitor != null) {
            audioAssistMonitor.stop();
        }
        if (pcLinkClient != null) {
            pcLinkClient.stop();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (updateExecutor != null) {
            updateExecutor.shutdown();
        }
    }

    private void startThermalMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager == null) {
            return;
        }
        thermalStatus = powerManager.getCurrentThermalStatus();
        thermalStatusListener = status -> thermalStatus = status;
        powerManager.addThermalStatusListener(mainExecutor, thermalStatusListener);
    }

    private void stopThermalMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || thermalStatusListener == null) {
            return;
        }
        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager != null) {
            powerManager.removeThermalStatusListener(thermalStatusListener);
        }
        thermalStatusListener = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyResponsiveLayout();
        if (previewView != null) {
            previewView.post(this::updateCameraTargetRotation);
        } else {
            updateCameraTargetRotation();
        }
    }

    private void applyResponsiveLayout() {
        if (rootView == null || statusView == null || controlsPanel == null) {
            return;
        }

        int width = rootView.getWidth() > 0 ? rootView.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int height = rootView.getHeight() > 0 ? rootView.getHeight() : getResources().getDisplayMetrics().heightPixels;
        ResponsiveControlLayout.Spec spec = ResponsiveControlLayout.forViewport(
                width,
                height,
                getResources().getDisplayMetrics().density
        );
        if (responsiveLayoutApplied
                && compactStatusLayout == spec.compactStatus
                && lastResponsiveSidePanel == spec.sidePanel
                && lastResponsivePanelWidthPx == spec.panelWidthPx) {
            return;
        }
        compactStatusLayout = spec.compactStatus;
        lastResponsiveSidePanel = spec.sidePanel;
        lastResponsivePanelWidthPx = spec.panelWidthPx;
        responsiveLayoutApplied = true;
        applyStatusLayout(spec);
        applyControlsLayout(spec);
    }

    private void applyStatusLayout(ResponsiveControlLayout.Spec spec) {
        int margin = dp(spec.sidePanel ? 8 : 12);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                spec.sidePanel ? spec.panelWidthPx : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = spec.sidePanel ? (Gravity.TOP | Gravity.END) : Gravity.TOP;
        params.setMargins(margin, margin, margin, 0);
        statusView.setLayoutParams(params);
        statusView.setTextSize(spec.compactStatus ? 12.0f : 14.0f);
        statusView.setMaxLines(spec.compactStatus ? 6 : Integer.MAX_VALUE);
        int padding = dp(spec.compactStatus ? 10 : 14);
        statusView.setPadding(padding, padding, padding, padding);
    }

    private void applyControlsLayout(ResponsiveControlLayout.Spec spec) {
        int margin = dp(spec.sidePanel ? 8 : 12);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                spec.sidePanel ? spec.panelWidthPx : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = spec.sidePanel ? (Gravity.BOTTOM | Gravity.END) : Gravity.BOTTOM;
        params.setMargins(margin, 0, margin, margin);
        controlsPanel.setLayoutParams(params);
        controlsPanel.setPadding(
                dp(spec.sidePanel ? 10 : 14),
                dp(spec.sidePanel ? 8 : 12),
                dp(spec.sidePanel ? 10 : 14),
                dp(spec.sidePanel ? 8 : 12)
        );
        float buttonTextSize = spec.sidePanel ? 12.0f : 14.0f;
        recordButton.setTextSize(buttonTextSize);
        filesButton.setTextSize(buttonTextSize);
        settingsButton.setTextSize(buttonTextSize);
        screenShieldButton.setTextSize(buttonTextSize);
        recordingView.setTextSize(spec.sidePanel ? 11.0f : 12.0f);
    }

    private void configureWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
    }

    private void buildUi() {
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(Color.rgb(16, 20, 24));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        rootView.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        overlayView = new MouthOverlayView(this);
        rootView.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(14.0f);
        statusView.setGravity(Gravity.START);
        int padding = dp(14);
        statusView.setPadding(padding, padding, padding, padding);
        statusView.setText("VocaGate Demo\nWaiting for camera...");
        setStatusBackground(Color.argb(190, 20, 24, 28));

        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.gravity = Gravity.TOP;
        statusParams.setMargins(dp(12), dp(12), dp(12), 0);
        rootView.addView(statusView, statusParams);

        controlsPanel = new LinearLayout(this);
        controlsPanel.setOrientation(LinearLayout.VERTICAL);
        controlsPanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable controlsBackground = new GradientDrawable();
        controlsBackground.setColor(Color.argb(210, 18, 23, 29));
        controlsBackground.setCornerRadius(dp(8));
        controlsPanel.setBackground(controlsBackground);

        recordButton = new Button(this);
        recordButton.setText("开始录音测试");
        recordButton.setAllCaps(false);
        recordButton.setOnClickListener(view -> toggleRecording());
        controlsPanel.addView(recordButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        filesButton = new Button(this);
        filesButton.setText("查看录音文件");
        filesButton.setAllCaps(false);
        filesButton.setEnabled(false);
        filesButton.setOnClickListener(view -> openRecordingFiles());
        controlsPanel.addView(filesButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        settingsButton = new Button(this);
        settingsButton.setText("显示设置");
        settingsButton.setAllCaps(false);
        settingsButton.setOnClickListener(view -> toggleSettingsPanel());
        controlsPanel.addView(settingsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        screenShieldButton = new Button(this);
        screenShieldButton.setText("关闭屏幕");
        screenShieldButton.setAllCaps(false);
        screenShieldButton.setOnClickListener(view -> activateScreenShield());
        controlsPanel.addView(screenShieldButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        recordingView = new TextView(this);
        recordingView.setTextColor(Color.WHITE);
        recordingView.setTextSize(12.0f);
        recordingView.setText("Recording: off");
        controlsPanel.addView(recordingView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setVisibility(LinearLayout.GONE);
        controlsPanel.addView(settingsPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addTuningHelpButton(settingsPanel);
        sensitivityLabel = addSlider(settingsPanel, "灵敏度", 50);
        earlyPressLabel = addSlider(settingsPanel, "提前按键", UserTuning.DEFAULT_EARLY_PRESS);
        responseLabel = addSlider(settingsPanel, "响应速度", 50);
        holdLabel = addSlider(settingsPanel, "闭麦保持", 50);
        firstWordProtectionLabel = addFirstWordProtectionSetting(settingsPanel);
        audioAssistLabel = addAudioAssistSetting(settingsPanel);
        addPcLinkSettings(settingsPanel);
        addUpdateSettingsEntry(settingsPanel);
        updateTuningFromSliders();

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        controlsParams.gravity = Gravity.BOTTOM;
        controlsParams.setMargins(dp(12), 0, dp(12), dp(12));
        rootView.addView(controlsPanel, controlsParams);

        screenShieldView = new ShieldHudView(this);
        screenShieldView.setVisibility(View.GONE);
        screenShieldView.setClickable(true);
        screenShieldView.setFocusable(true);
        screenShieldView.setOnClickListener(view -> deactivateScreenShield());
        rootView.addView(screenShieldView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        rootView.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                applyResponsiveLayout()
        );
        setContentView(rootView);
        rootView.post(this::applyResponsiveLayout);
    }

    @Override
    protected void onPause() {
        deactivateScreenShield();
        super.onPause();
    }

    private void addTuningHelpButton(LinearLayout parent) {
        Button helpButton = new Button(this);
        helpButton.setAllCaps(false);
        helpButton.setText("参数说明");
        helpButton.setOnClickListener(view -> showTuningHelp());
        parent.addView(helpButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private TextView addSlider(LinearLayout parent, String label, int defaultValue) {
        TextView textView = new TextView(this);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(12.0f);
        parent.addView(textView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(defaultValue);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTuningFromSliders();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if ("灵敏度".equals(label)) {
            sensitivitySeekBar = seekBar;
        } else if ("提前按键".equals(label)) {
            earlyPressSeekBar = seekBar;
        } else if ("响应速度".equals(label)) {
            responseSeekBar = seekBar;
        } else {
            holdSeekBar = seekBar;
        }
        textView.setTag(label);
        return textView;
    }

    private TextView addFirstWordProtectionSetting(LinearLayout parent) {
        TextView textView = new TextView(this);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(12.0f);
        parent.addView(textView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        firstWordProtectionSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        FirstWordProtection.modeName(FirstWordProtection.OFF),
                        FirstWordProtection.modeName(FirstWordProtection.CONSERVATIVE),
                        FirstWordProtection.modeName(FirstWordProtection.STANDARD),
                        FirstWordProtection.modeName(FirstWordProtection.AGGRESSIVE)
                }
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        firstWordProtectionSpinner.setAdapter(adapter);
        firstWordProtectionSpinner.setSelection(UserTuning.DEFAULT_FIRST_WORD_PROTECTION_MODE);
        firstWordProtectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedView, int position, long id) {
                updateTuningFromSliders();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
        parent.addView(firstWordProtectionSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return textView;
    }

    private TextView addAudioAssistSetting(LinearLayout parent) {
        TextView textView = new TextView(this);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(12.0f);
        parent.addView(textView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        audioAssistSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        AudioAssistConfig.modeName(AudioAssistConfig.OFF),
                        AudioAssistConfig.modeName(AudioAssistConfig.CONSERVATIVE),
                        AudioAssistConfig.modeName(AudioAssistConfig.STANDARD),
                        AudioAssistConfig.modeName(AudioAssistConfig.AGGRESSIVE)
                }
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioAssistSpinner.setAdapter(adapter);
        audioAssistSpinner.setSelection(UserTuning.DEFAULT_AUDIO_ASSIST_MODE);
        audioAssistSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedView, int position, long id) {
                updateTuningFromSliders();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
        parent.addView(audioAssistSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return textView;
    }

    private void updateTuningFromSliders() {
        if (sensitivitySeekBar == null
                || earlyPressSeekBar == null
                || responseSeekBar == null
                || holdSeekBar == null) {
            return;
        }
        int firstWordProtectionMode = firstWordProtectionSpinner == null
                ? UserTuning.DEFAULT_FIRST_WORD_PROTECTION_MODE
                : firstWordProtectionSpinner.getSelectedItemPosition();
        int audioAssistMode = audioAssistSpinner == null
                ? UserTuning.DEFAULT_AUDIO_ASSIST_MODE
                : audioAssistSpinner.getSelectedItemPosition();

        currentTuning = new UserTuning(
                sensitivitySeekBar.getProgress(),
                responseSeekBar.getProgress(),
                holdSeekBar.getProgress(),
                earlyPressSeekBar.getProgress(),
                firstWordProtectionMode,
                audioAssistMode
        );
        GateConfig config = currentTuning.toGateConfig();
        sensitivityLabel.setText(String.format(Locale.US, "灵敏度 %d  开麦 %.2f",
                currentTuning.sensitivity,
                config.openThreshold));
        earlyPressLabel.setText(String.format(Locale.US, "提前按键 %d  预按 %.2f",
                currentTuning.earlyPress,
                config.preOpenThreshold));
        responseLabel.setText(String.format(Locale.US, "响应速度 %d  确认 %d 帧",
                currentTuning.responseSpeed,
                config.preOpenRequiredFrames));
        holdLabel.setText(String.format(Locale.US, "闭麦保持 %d  HOLD %dms",
                currentTuning.hold,
                config.holdMs));
        if (firstWordProtectionLabel != null) {
            firstWordProtectionLabel.setText(String.format(
                    Locale.US,
                    "首字保护 %s  补偿 %dms",
                    FirstWordProtection.modeName(currentTuning.firstWordProtectionMode),
                    FirstWordProtection.holdMsForMode(currentTuning.firstWordProtectionMode)
            ));
        }
        if (audioAssistLabel != null) {
            AudioAssistConfig audioConfig = AudioAssistConfig.forMode(currentTuning.audioAssistMode);
            audioAssistLabel.setText(String.format(
                    Locale.US,
                    "音频辅助 %s  加分 %.2f",
                    AudioAssistConfig.modeName(currentTuning.audioAssistMode),
                    audioConfig.maxBoost
            ));
        }

        if (analyzer != null) {
            analyzer.applyTuning(currentTuning);
        }
    }

    private void showTuningHelp() {
        new AlertDialog.Builder(this)
                .setTitle("参数说明")
                .setMessage(
                        "灵敏度：越高越容易开麦，但更容易误触发。\n\n"
                                + "提前按键：越高越早按住按键，用来保护第一个字。"
                                + "如果环境很吵或容易误触，可以先降低它。\n\n"
                                + "响应速度：越高确认帧越少，开麦更快，但稳定性会下降。\n\n"
                                + "闭麦保持：说完后继续按住多久，数值越高越不容易吞尾音。\n\n"
                                + "首字保护：只看嘴部刚开始张开的视觉趋势，提前短暂按住按键。"
                                + "环境音不会触发它，但如果画面误触发且环境音很大，游戏自己的 VAD 可能会送出一点环境音。\n\n"
                                + "首字保护档位：关闭不补偿；保守误触少；标准适合多数情况；激进更少吞字但可能多按一点空键。\n\n"
                                + "音频辅助：手机麦克风只给临界嘴部识别加分。只有声音、没有嘴部趋势时不会开麦；环境吵或音箱声音大时可调到保守或关闭。"
                )
                .setPositiveButton("知道了", null)
                .show();
    }

    private void addPcLinkSettings(LinearLayout parent) {
        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(13.0f);
        title.setText("电脑连接");
        title.setPadding(0, dp(8), 0, 0);
        parent.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        pcConfigLabel = new TextView(this);
        pcConfigLabel.setTextColor(Color.WHITE);
        pcConfigLabel.setTextSize(12.0f);
        parent.addView(pcConfigLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        pcModeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Auto", "USB", "Wi-Fi"}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pcModeSpinner.setAdapter(adapter);
        pcModeSpinner.setSelection(indexForMode(loadPcLinkMode()));
        parent.addView(pcModeSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        pcPortEditText = new EditText(this);
        pcPortEditText.setSingleLine(true);
        pcPortEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        pcPortEditText.setHint("电脑端口，例如 27183");
        pcPortEditText.setText(String.valueOf(loadPcLinkPort()));
        parent.addView(pcPortEditText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        wifiHostEditText = new EditText(this);
        wifiHostEditText.setSingleLine(true);
        wifiHostEditText.setHint("电脑 IP，例如 192.168.1.20");
        wifiHostEditText.setText(pcLinkPreferences.getString(PREF_WIFI_HOST, ""));
        parent.addView(wifiHostEditText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        pairingCodeEditText = new EditText(this);
        pairingCodeEditText.setSingleLine(true);
        pairingCodeEditText.setHint("Wi-Fi 配对码");
        pairingCodeEditText.setText(pcLinkPreferences.getString(PREF_PAIRING_CODE, ""));
        parent.addView(pairingCodeEditText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button saveButton = new Button(this);
        saveButton.setAllCaps(false);
        saveButton.setText("保存电脑连接设置");
        saveButton.setOnClickListener(view -> savePcLinkSettings());
        parent.addView(saveButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        updatePcConfigLabel(currentPcLinkSettings());
    }

    private void addUpdateSettingsEntry(LinearLayout parent) {
        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(13.0f);
        title.setText("算法更新");
        title.setPadding(0, dp(8), 0, 0);
        parent.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button updateSettingsButton = new Button(this);
        updateSettingsButton.setAllCaps(false);
        updateSettingsButton.setText("打开算法更新设置");
        updateSettingsButton.setOnClickListener(view -> openUpdateSettings());
        parent.addView(updateSettingsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void openUpdateSettings() {
        startActivity(new Intent(this, UpdateSettingsActivity.class));
    }

    private void maybeAutoCheckAlgorithmUpdates() {
        UpdatePreferences saved = loadUpdatePreferences();
        if (UpdateSchedule.shouldCheck(saved, System.currentTimeMillis())) {
            runBackgroundAlgorithmUpdateCheck(saved);
        }
    }

    private void runBackgroundAlgorithmUpdateCheck(UpdatePreferences saved) {
        if (!saved.updateChecksEnabled()) {
            return;
        }
        updateExecutor.execute(() -> {
            UpdateCheckResult result = new UpdateCheckClient(new HttpUrlUpdateClient()).checkNow(new UpdateClientConfig(
                    saved.updateServerUrl(),
                    "android",
                    ANDROID_APP_VERSION,
                    "stable",
                    "android-mlkit-v1",
                    saved.updateChecksEnabled(),
                    saved.anonymousStatsEnabled()
            ));
            runOnUiThread(() -> {
                if (result.success()) {
                    updatePreferences.edit()
                            .putLong(PREF_UPDATE_LAST_CHECKED_AT_MS, result.checkedAtMs())
                            .putString(PREF_UPDATE_LAST_PROFILE_ID, result.profile().profileId())
                            .putString(PREF_UPDATE_LAST_PROFILE_JSON, result.profileJson())
                            .putLong(PREF_UPDATE_LAST_PROFILE_UPDATED_AT_MS, result.checkedAtMs())
                            .apply();
                    applyAlgorithmProfile(result.profile());
                } else {
                    if (result.checkedAtMs() > 0L) {
                        updatePreferences.edit()
                                .putLong(PREF_UPDATE_LAST_CHECKED_AT_MS, result.checkedAtMs())
                                .apply();
                    }
                }
            });
        });
    }

    private UpdatePreferences loadUpdatePreferences() {
        return new UpdatePreferences(
                updatePreferences.getBoolean(PREF_UPDATE_CHECKS_ENABLED, true),
                updatePreferences.getBoolean(PREF_ANONYMOUS_STATS_ENABLED, true),
                updatePreferences.getString(PREF_UPDATE_SERVER_URL, UpdatePreferences.DEFAULT_UPDATE_SERVER_URL),
                updatePreferences.getLong(PREF_UPDATE_LAST_CHECKED_AT_MS, 0L),
                updatePreferences.getString(PREF_UPDATE_LAST_PROFILE_ID, "")
        );
    }

    private AlgorithmProfile loadCachedAlgorithmProfile() {
        String profileJson = updatePreferences.getString(PREF_UPDATE_LAST_PROFILE_JSON, "");
        if (profileJson == null || profileJson.trim().isEmpty()) {
            return null;
        }
        try {
            return UpdateProtocolParser.parseAlgorithmProfile(profileJson);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void applyAlgorithmProfile(AlgorithmProfile profile) {
        cachedAlgorithmProfile = profile;
        VocaGateAnalyzer activeAnalyzer = analyzer;
        if (activeAnalyzer != null) {
            activeAnalyzer.applyAlgorithmProfile(profile);
        }
    }

    private void savePcLinkSettings() {
        PcLinkMode mode = modeForIndex(pcModeSpinner.getSelectedItemPosition());
        String wifiHost = wifiHostEditText.getText().toString().trim();
        String pairingCode = pairingCodeEditText.getText().toString().trim();
        int port = parsePcLinkPortInput();
        pcPortEditText.setText(String.valueOf(port));
        pcLinkPreferences.edit()
                .putString(PREF_MODE, mode.name())
                .putString(PREF_WIFI_HOST, wifiHost)
                .putString(PREF_PAIRING_CODE, pairingCode)
                .putInt(PREF_PORT, port)
                .apply();

        PcLinkSettings settings = new PcLinkSettings(mode, wifiHost, pairingCode, port);
        if (pcLinkClient != null) {
            pcLinkClient.updateSettings(settings);
        }
        updatePcConfigLabel(settings);
    }

    private PcLinkSettings currentPcLinkSettings() {
        return new PcLinkSettings(
                loadPcLinkMode(),
                pcLinkPreferences.getString(PREF_WIFI_HOST, ""),
                pcLinkPreferences.getString(PREF_PAIRING_CODE, ""),
                loadPcLinkPort()
        );
    }

    private PcLinkMode loadPcLinkMode() {
        String raw = pcLinkPreferences.getString(PREF_MODE, PcLinkMode.AUTO.name());
        try {
            return PcLinkMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return PcLinkMode.AUTO;
        }
    }

    private void updatePcConfigLabel(PcLinkSettings settings) {
        if (pcConfigLabel == null) {
            return;
        }
        String wifi = settings.wifiHost().isEmpty() ? "Wi-Fi 未配对" : "Wi-Fi " + settings.wifiHost();
        pcConfigLabel.setText("模式 " + settings.mode() + " / 端口 " + settings.port() + " / " + wifi);
    }

    private int parsePcLinkPortInput() {
        if (pcPortEditText == null) {
            return loadPcLinkPort();
        }
        return sanitizePcLinkPort(parseIntOrDefault(
                pcPortEditText.getText().toString().trim(),
                PcLinkSettings.DEFAULT_PORT
        ));
    }

    private int loadPcLinkPort() {
        Object stored = pcLinkPreferences.getAll().get(PREF_PORT);
        if (stored instanceof Number) {
            return sanitizePcLinkPort(((Number) stored).intValue());
        }
        if (stored instanceof String) {
            return sanitizePcLinkPort(parseIntOrDefault((String) stored, PcLinkSettings.DEFAULT_PORT));
        }
        return PcLinkSettings.DEFAULT_PORT;
    }

    private int sanitizePcLinkPort(int port) {
        if (port <= 0 || port > 65535) {
            return PcLinkSettings.DEFAULT_PORT;
        }
        return port;
    }

    private int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int indexForMode(PcLinkMode mode) {
        if (mode == PcLinkMode.USB) {
            return 1;
        }
        if (mode == PcLinkMode.WIFI) {
            return 2;
        }
        return 0;
    }

    private PcLinkMode modeForIndex(int index) {
        if (index == 1) {
            return PcLinkMode.USB;
        }
        if (index == 2) {
            return PcLinkMode.WIFI;
        }
        return PcLinkMode.AUTO;
    }

    private boolean hasRequiredPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCamera(cameraProvider, CameraSelector.DEFAULT_FRONT_CAMERA, true);
            } catch (Exception frontError) {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindCamera(cameraProvider, CameraSelector.DEFAULT_BACK_CAMERA, false);
                } catch (Exception backError) {
                    showStatus("Camera start failed: " + backError.getMessage(), GateState.SUSPEND, false, 0.0f, 0.0f, 0.0f, 0.0f);
                }
            }
        }, mainExecutor);
    }

    private void bindCamera(ProcessCameraProvider cameraProvider, CameraSelector selector, boolean mirrorOverlay) {
        int targetRotation = currentDisplayRotation();
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analyzer = new VocaGateAnalyzer(mirrorOverlay);
        analyzer.applyTuning(currentTuning);
        analyzer.applyAlgorithmProfile(cachedAlgorithmProfile);
        analysis.setAnalyzer(cameraExecutor, analyzer);

        cameraProvider.unbindAll();
        boundCameraProvider = cameraProvider;
        boundCameraSelector = selector;
        previewUseCase = screenShieldState.shouldRenderCameraPreview() ? createPreviewUseCase(targetRotation) : null;
        analysisUseCase = analysis;
        if (previewUseCase == null) {
            cameraProvider.bindToLifecycle(this, selector, analysisUseCase);
        } else {
            cameraProvider.bindToLifecycle(this, selector, previewUseCase, analysisUseCase);
        }
        updateCameraTargetRotation();
    }

    private Preview createPreviewUseCase(int targetRotation) {
        Preview preview = new Preview.Builder()
                .setTargetRotation(targetRotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        return preview;
    }

    private int currentDisplayRotation() {
        if (previewView != null && previewView.getDisplay() != null) {
            return previewView.getDisplay().getRotation();
        }
        return getWindowManager().getDefaultDisplay().getRotation();
    }

    private void updateCameraTargetRotation() {
        int rotation = currentDisplayRotation();
        if (previewUseCase != null) {
            previewUseCase.setTargetRotation(rotation);
        }
        if (analysisUseCase != null) {
            analysisUseCase.setTargetRotation(rotation);
        }
        if (overlayView != null) {
            overlayView.invalidate();
        }
    }

    private void showStatus(
            String label,
            GateState state,
            boolean gateOpen,
            float score,
            float mouthRatio,
            float rhythm,
            float fps
    ) {
        runOnUiThread(() -> {
            rememberShieldStatus(label, state, gateOpen, score);
            refreshShieldHudFromLastStatus();
            statusView.setText(StatusTextFormatter.format(
                    compactStatusLayout,
                    label,
                    pcLinkStatus,
                    state,
                    gateOpen,
                    score,
                    mouthRatio,
                    rhythm,
                    fps
            ));

            if (state == GateState.SUSPEND) {
                setStatusBackground(Color.argb(205, 127, 79, 13));
            } else if (gateOpen) {
                setStatusBackground(Color.argb(205, 12, 105, 73));
            } else {
                setStatusBackground(Color.argb(205, 96, 32, 45));
            }
            if (audioRecorder != null && audioRecorder.isRecording()) {
                recordingView.setText(audioRecorder.getLiveLabel());
            }
        });
    }

    private void showShieldHudStatus(String label, GateState state, boolean gateOpen, float score) {
        runOnUiThread(() -> {
            rememberShieldStatus(label, state, gateOpen, score);
            refreshShieldHudFromLastStatus();
        });
    }

    private void rememberShieldStatus(String label, GateState state, boolean gateOpen, float score) {
        lastShieldLabel = label == null ? "-" : label;
        lastShieldState = state == null ? GateState.CLOSED : state;
        lastShieldGateOpen = gateOpen;
        lastShieldScore = score;
    }

    private void refreshShieldHudFromLastStatus() {
        if (screenShieldView == null) {
            return;
        }
        screenShieldView.setHudState(ShieldHudState.from(
                lastShieldLabel,
                pcLinkStatus,
                lastShieldState,
                lastShieldGateOpen,
                lastShieldScore
        ));
    }

    private void toggleRecording() {
        if (!hasRequiredPermissions()) {
            requestPermissions(REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST);
            return;
        }

        if (audioRecorder == null) {
            return;
        }

        if (audioRecorder.isRecording()) {
            stopRecordingTest();
        } else {
            startRecordingTest();
        }
    }

    private void startRecordingTest() {
        try {
            latestRecordingResult = null;
            filesButton.setEnabled(false);
            startAudioRecorderWithAssistFallback();
            recordButton.setText("停止录音测试");
            recordingView.setText("Recording: starting...");
        } catch (IOException | SecurityException e) {
            recordingView.setText("Recording failed: " + e.getMessage());
        }
    }

    private void startAudioRecorderWithAssistFallback() throws IOException {
        try {
            audioRecorder.start();
        } catch (IOException | SecurityException firstError) {
            if (audioAssistMonitor == null || !audioAssistMonitor.isRunning()) {
                throw firstError;
            }
            audioAssistMonitor.stop();
            audioRecorder.start();
        }
    }

    private void stopRecordingTest() {
        try {
            RecordingResult result = audioRecorder.stop();
            latestRecordingResult = result;
            recordButton.setText("开始录音测试");
            if (result == null) {
                recordingView.setText("Recording: off");
                filesButton.setEnabled(false);
                startAudioAssistMonitor();
                return;
            }
            filesButton.setEnabled(true);
            MaiShaoScore score = result.score;
            recordingView.setText(String.format(
                    Locale.US,
                    "麦哨值 %.1f  抑制 %.1f%%  开麦 %.1f%%  抖动 %.1f/min",
                    score.value,
                    score.suppressionPercent,
                    score.openPercent,
                    score.transitionsPerMinute
            ));
            startAudioAssistMonitor();
        } catch (IOException e) {
            recordButton.setText("开始录音测试");
            recordingView.setText("Stop failed: " + e.getMessage());
            startAudioAssistMonitor();
        }
    }

    private void openRecordingFiles() {
        if (latestRecordingResult == null) {
            return;
        }

        Intent intent = new Intent(this, RecordingFilesActivity.class);
        intent.putExtra(RecordingFilesActivity.EXTRA_FULL_WAV, latestRecordingResult.fullWav.getAbsolutePath());
        intent.putExtra(RecordingFilesActivity.EXTRA_GATED_WAV, latestRecordingResult.gatedWav.getAbsolutePath());
        intent.putExtra(RecordingFilesActivity.EXTRA_SCORE, latestRecordingResult.score.value);
        intent.putExtra(RecordingFilesActivity.EXTRA_SUPPRESSION, latestRecordingResult.score.suppressionPercent);
        intent.putExtra(RecordingFilesActivity.EXTRA_OPEN_PERCENT, latestRecordingResult.score.openPercent);
        intent.putExtra(RecordingFilesActivity.EXTRA_TRANSITIONS, latestRecordingResult.score.transitionsPerMinute);
        startActivity(intent);
    }

    private void toggleSettingsPanel() {
        boolean show = settingsPanel.getVisibility() != LinearLayout.VISIBLE;
        settingsPanel.setVisibility(show ? LinearLayout.VISIBLE : LinearLayout.GONE);
        settingsButton.setText(show ? "隐藏设置" : "显示设置");
    }

    private void activateScreenShield() {
        if (screenShieldState.isActive()) {
            return;
        }
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        screenShieldState.activate(attributes.screenBrightness);
        attributes.screenBrightness = screenShieldState.activeBrightness();
        getWindow().setAttributes(attributes);
        pauseCameraPreviewOnly();
        refreshShieldHudFromLastStatus();
        screenShieldView.setVisibility(View.VISIBLE);
        screenShieldView.bringToFront();
    }

    private void deactivateScreenShield() {
        if (!screenShieldState.isActive()) {
            return;
        }
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = screenShieldState.deactivate();
        getWindow().setAttributes(attributes);
        resumeCameraPreviewOnly();
        if (screenShieldView != null) {
            screenShieldView.setVisibility(View.GONE);
        }
    }

    private void pauseCameraPreviewOnly() {
        if (previewView != null) {
            previewView.setVisibility(View.INVISIBLE);
        }
        if (overlayView != null) {
            overlayView.setVisibility(View.INVISIBLE);
        }
        if (boundCameraProvider == null || previewUseCase == null) {
            return;
        }

        Preview preview = previewUseCase;
        try {
            boundCameraProvider.unbind(preview);
            previewUseCase = null;
        } catch (RuntimeException ignored) {
            // Some devices/providers may reject single-use-case unbinds. Hidden preview is the fallback.
        }
    }

    private void resumeCameraPreviewOnly() {
        if (previewView != null) {
            previewView.setVisibility(View.VISIBLE);
        }
        if (overlayView != null) {
            overlayView.setVisibility(View.VISIBLE);
        }
        if (previewUseCase != null || boundCameraProvider == null || boundCameraSelector == null) {
            return;
        }

        Preview preview = createPreviewUseCase(currentDisplayRotation());
        try {
            boundCameraProvider.bindToLifecycle(this, boundCameraSelector, preview);
            previewUseCase = preview;
            updateCameraTargetRotation();
        } catch (RuntimeException error) {
            previewUseCase = null;
            showStatus("Preview resume failed: " + error.getMessage(), GateState.SUSPEND, false, 0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    private void setStatusBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(8));
        statusView.setBackground(background);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class VocaGateAnalyzer implements ImageAnalysis.Analyzer {
        private final AtomicBoolean processing = new AtomicBoolean(false);
        private GateConfig gateConfig = UserTuning.defaults().toGateConfig();
        private GateStateMachine gateStateMachine = new GateStateMachine(UserTuning.defaults().toGateConfig());
        private volatile SpeakScoreEstimator scoreEstimator = new SpeakScoreEstimator(new ScoreConfig());
        private final AnalysisLoadController loadController = new AnalysisLoadController();
        private final FirstWordProtection firstWordProtection = new FirstWordProtection(UserTuning.DEFAULT_FIRST_WORD_PROTECTION_MODE);
        private final AudioVisualFusion audioVisualFusion = new AudioVisualFusion(
                AudioAssistConfig.forMode(UserTuning.DEFAULT_AUDIO_ASSIST_MODE)
        );
        private final HeadPoseCalibrator headPoseCalibrator = new HeadPoseCalibrator();
        private final ArrayDeque<Float> ratioHistory = new ArrayDeque<>();
        private final boolean mirrorOverlay;
        private AlgorithmProfile algorithmProfile;
        private float previousRatio = 0.0f;
        private long longOpenStartedAtMs = -1L;
        private long previousFrameAtMs = -1L;

        private VocaGateAnalyzer(boolean mirrorOverlay) {
            this.mirrorOverlay = mirrorOverlay;
        }

        private synchronized void applyTuning(UserTuning tuning) {
            gateConfig = tuning.toGateConfig(algorithmProfile);
            gateStateMachine = new GateStateMachine(gateConfig);
            firstWordProtection.setMode(tuning.firstWordProtectionMode);
            audioVisualFusion.setConfig(AudioAssistConfig.forMode(tuning.audioAssistMode));
        }

        private synchronized void applyAlgorithmProfile(AlgorithmProfile profile) {
            algorithmProfile = profile;
            scoreEstimator = new SpeakScoreEstimator(profile == null
                    ? new ScoreConfig()
                    : ScoreConfig.fromProfile(profile.score()));
            applyTuning(currentTuning);
        }

        @Override
        @ExperimentalGetImage
        public void analyze(ImageProxy imageProxy) {
            long nowMs = SystemClock.elapsedRealtime();
            int thermalAtFrame = thermalStatus;
            if (!loadController.shouldAnalyze(nowMs, thermalAtFrame)) {
                imageProxy.close();
                return;
            }
            if (!processing.compareAndSet(false, true)) {
                imageProxy.close();
                return;
            }

            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                processing.set(false);
                imageProxy.close();
                return;
            }

            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees);
            int imageWidth = OverlayCoordinateMapper.orientedWidth(
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    rotationDegrees
            );
            int imageHeight = OverlayCoordinateMapper.orientedHeight(
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    rotationDegrees
            );

            detector.process(inputImage)
                    .addOnSuccessListener(faces -> handleFaces(faces, nowMs, imageWidth, imageHeight, thermalAtFrame))
                    .addOnFailureListener(error -> handleNoFace("Detection failed", nowMs, imageWidth, imageHeight, thermalAtFrame))
                    .addOnCompleteListener(task -> {
                        loadController.recordDetectionLatency(SystemClock.elapsedRealtime() - nowMs);
                        processing.set(false);
                        imageProxy.close();
                    });
        }

        private void handleFaces(List<Face> faces, long nowMs, int imageWidth, int imageHeight, int thermalAtFrame) {
            if (faces == null || faces.isEmpty()) {
                handleNoFace("No face", nowMs, imageWidth, imageHeight, thermalAtFrame);
                return;
            }

            float fps = updateFps(nowMs);
            Face face = largestFace(faces);
            headPoseCalibrator.update(face.getHeadEulerAngleY(), face.getHeadEulerAngleZ());
            float relativeRoll = headPoseCalibrator.relativeRoll(face.getHeadEulerAngleZ());
            List<Point2> rawUpperInner = pointsFrom(face.getContour(FaceContour.UPPER_LIP_BOTTOM));
            List<Point2> rawLowerInner = pointsFrom(face.getContour(FaceContour.LOWER_LIP_TOP));
            List<Point2> rawAllLipPoints = new ArrayList<>();
            rawAllLipPoints.addAll(pointsFrom(face.getContour(FaceContour.UPPER_LIP_TOP)));
            rawAllLipPoints.addAll(rawUpperInner);
            rawAllLipPoints.addAll(rawLowerInner);
            rawAllLipPoints.addAll(pointsFrom(face.getContour(FaceContour.LOWER_LIP_BOTTOM)));

            boolean trackingValid = !rawUpperInner.isEmpty() && !rawLowerInner.isEmpty() && !rawAllLipPoints.isEmpty();
            Point2 lipCenter = MouthPoseNormalizer.centerOf(rawAllLipPoints);
            List<Point2> upperInner = MouthPoseNormalizer.normalize(rawUpperInner, lipCenter, relativeRoll);
            List<Point2> lowerInner = MouthPoseNormalizer.normalize(rawLowerInner, lipCenter, relativeRoll);
            List<Point2> allLipPoints = MouthPoseNormalizer.normalize(rawAllLipPoints, lipCenter, relativeRoll);
            MouthFeatures baseFeatures = MouthFeatureExtractor.fromLipContours(
                    upperInner,
                    lowerInner,
                    allLipPoints,
                    previousRatio,
                    0.0f
            );
            float rhythm = updateRhythm(baseFeatures.mouthOpenRatio);
            MouthFeatures features = new MouthFeatures(
                    baseFeatures.mouthOpen,
                    baseFeatures.mouthWidth,
                    baseFeatures.mouthOpenRatio,
                    baseFeatures.deltaMouth,
                    rhythm,
                    baseFeatures.normalizedMouthOpen
            );

            previousRatio = features.mouthOpenRatio;
            float longOpenPenalty = updateLongOpenPenalty(features.mouthOpenRatio, nowMs);
            float frontalFaceScore = headPoseCalibrator.frontalScore(face.getHeadEulerAngleY(), face.getHeadEulerAngleZ());
            float trackStability = face.getTrackingId() == null ? 0.80f : 0.95f;
            float occlusionPenalty = trackingValid ? 0.0f : 1.0f;
            float score = scoreEstimator.estimate(
                    features,
                    frontalFaceScore,
                    trackStability,
                    occlusionPenalty,
                    longOpenPenalty
            );
            AudioAssistFrame audioFrame = audioAssistMonitor == null
                    ? AudioAssistFrame.SILENCE
                    : audioAssistMonitor.latestFrame();
            AudioVisualFusion.Result audioFusion = audioVisualFusion.fuse(
                    score,
                    features,
                    audioFrame,
                    gateConfig,
                    trackingValid,
                    nowMs
            );
            FirstWordProtection.Result firstWord = firstWordProtection.update(
                    features,
                    trackingValid,
                    frontalFaceScore,
                    nowMs
            );
            float gateScore = firstWord.active ? Math.max(audioFusion.score, gateConfig.preOpenThreshold) : audioFusion.score;

            GateState state = updateGate(gateScore, trackingValid, nowMs);
            boolean gateOpen = isGateOpen();
            boolean outputOpen = gateOpen || state == GateState.PRE_OPEN;
            if (audioRecorder != null) {
                audioRecorder.setGateOpen(outputOpen);
            }
            if (pcLinkClient != null) {
                String linkState = state == GateState.PRE_OPEN ? "PRE_OPEN" : (outputOpen ? "OPEN" : "BLOCKED");
                pcLinkClient.updateGateState(linkState, outputOpen, nowMs, gateScore);
            }
            List<Point2> overlayPoints = new ArrayList<>(rawAllLipPoints);
            updateVisualFeedback(
                    overlayPoints,
                    imageWidth,
                    imageHeight,
                    statusLabel(statusLabelForFrame(firstWord.active, audioFusion.audioAssisted), thermalAtFrame),
                    state,
                    outputOpen,
                    gateScore,
                    features.mouthOpenRatio,
                    rhythm,
                    fps
            );
        }

        private void handleNoFace(String label, long nowMs, int imageWidth, int imageHeight, int thermalAtFrame) {
            float fps = updateFps(nowMs);
            GateState state = updateGate(0.0f, false, nowMs);
            if (audioRecorder != null) {
                audioRecorder.setGateOpen(false);
            }
            if (pcLinkClient != null) {
                pcLinkClient.updateGateState("BLOCKED", false, nowMs, 0.0f);
            }
            previousRatio = 0.0f;
            ratioHistory.clear();
            firstWordProtection.reset();
            longOpenStartedAtMs = -1L;
            updateVisualFeedback(
                    new ArrayList<>(),
                    imageWidth,
                    imageHeight,
                    statusLabel(label, thermalAtFrame),
                    state,
                    false,
                    0.0f,
                    0.0f,
                    0.0f,
                    fps
            );
        }

        private String statusLabelForFrame(boolean firstWordActive, boolean audioAssisted) {
            if (firstWordActive) {
                return "首字保护";
            }
            if (audioAssisted) {
                return "音频辅助";
            }
            return "Face tracked";
        }

        private void updateVisualFeedback(
                List<Point2> overlayPoints,
                int imageWidth,
                int imageHeight,
                String label,
                GateState state,
                boolean gateOpen,
                float score,
                float mouthRatio,
                float rhythm,
                float fps
        ) {
            if (screenShieldState.shouldRenderOverlay()) {
                runOnUiThread(() -> overlayView.setMouthPoints(overlayPoints, imageWidth, imageHeight, mirrorOverlay));
            }
            if (screenShieldState.shouldUpdateStatusText()) {
                showStatus(label, state, gateOpen, score, mouthRatio, rhythm, fps);
            } else if (screenShieldState.shouldUpdateShieldHud()) {
                showShieldHudStatus(label, state, gateOpen, score);
            }
        }

        private synchronized GateState updateGate(float score, boolean trackingValid, long nowMs) {
            return gateStateMachine.update(score, trackingValid, nowMs);
        }

        private synchronized boolean isGateOpen() {
            return gateStateMachine.isGateOpen();
        }

        private float updateFps(long nowMs) {
            if (previousFrameAtMs <= 0L) {
                previousFrameAtMs = nowMs;
                return 0.0f;
            }
            long delta = Math.max(1L, nowMs - previousFrameAtMs);
            previousFrameAtMs = nowMs;
            return 1000.0f / delta;
        }

        private Face largestFace(List<Face> faces) {
            Face largest = faces.get(0);
            int largestArea = area(largest.getBoundingBox());
            for (Face face : faces) {
                int area = area(face.getBoundingBox());
                if (area > largestArea) {
                    largest = face;
                    largestArea = area;
                }
            }
            return largest;
        }

        private int area(Rect rect) {
            return Math.max(0, rect.width()) * Math.max(0, rect.height());
        }

        private List<Point2> pointsFrom(FaceContour contour) {
            List<Point2> points = new ArrayList<>();
            if (contour == null || contour.getPoints() == null) {
                return points;
            }
            for (PointF point : contour.getPoints()) {
                points.add(new Point2(point.x, point.y));
            }
            return points;
        }

        private String statusLabel(String label, int thermalAtFrame) {
            return String.format(
                    Locale.US,
                    "%s - %s - %.0fms",
                    label,
                    loadController.modeLabel(thermalAtFrame),
                    loadController.smoothedDetectionMs()
            );
        }

        private float updateRhythm(float ratio) {
            ratioHistory.addLast(ratio);
            while (ratioHistory.size() > 8) {
                ratioHistory.removeFirst();
            }

            if (ratioHistory.size() < 2) {
                return 0.0f;
            }

            float sumDelta = 0.0f;
            Float previous = null;
            for (Float value : ratioHistory) {
                if (previous != null) {
                    sumDelta += Math.abs(value - previous);
                }
                previous = value;
            }
            float averageDelta = sumDelta / (ratioHistory.size() - 1);
            return MouthFeatureExtractor.clamp01(averageDelta / 0.08f);
        }

        private float updateLongOpenPenalty(float ratio, long nowMs) {
            if (ratio < 0.42f) {
                longOpenStartedAtMs = -1L;
                return 0.0f;
            }

            if (longOpenStartedAtMs < 0L) {
                longOpenStartedAtMs = nowMs;
                return 0.0f;
            }

            long duration = nowMs - longOpenStartedAtMs;
            if (duration < 700L) {
                return 0.0f;
            }
            return MouthFeatureExtractor.clamp01((duration - 700L) / 600.0f);
        }
    }
}
