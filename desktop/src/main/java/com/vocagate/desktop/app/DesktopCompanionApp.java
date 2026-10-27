package com.vocagate.desktop.app;

import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.core.update.HttpUrlUpdateClient;
import com.vocagate.core.update.UpdateCheckClient;
import com.vocagate.core.update.UpdateCheckResult;
import com.vocagate.core.update.UpdateClientConfig;
import com.vocagate.core.update.UpdatePreferences;
import com.vocagate.core.update.UpdateProtocolParser;
import com.vocagate.core.update.UpdateSchedule;
import com.vocagate.desktop.core.GateHoldController;
import com.vocagate.desktop.core.GateMessage;
import com.vocagate.desktop.core.KeyStrokeSpec;
import com.vocagate.desktop.core.RobotKeyActuator;
import com.vocagate.desktop.mediapipe.MediaPipeFaceLandmarkerFrameAnalyzer;
import com.vocagate.desktop.server.ConnectionChannel;
import com.vocagate.desktop.server.ConnectionMode;
import com.vocagate.desktop.server.DesktopGateServer;
import com.vocagate.desktop.vision.DesktopMotionScoreAnalyzer;
import com.vocagate.desktop.vision.DesktopRecognitionResult;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class DesktopCompanionApp {
    private static final int DEFAULT_PORT = 27183;
    private static final long HEARTBEAT_TIMEOUT_MS = 500L;
    private static final long LOCAL_RECOGNITION_INTERVAL_MS = 100L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Color APP_BACKGROUND = new Color(247, 248, 250);
    private static final Color CARD_BACKGROUND = new Color(255, 255, 255);
    private static final Color CARD_BORDER = new Color(220, 224, 229);
    private static final Color TEXT_PRIMARY = new Color(32, 36, 42);
    private static final Color TEXT_SECONDARY = new Color(88, 94, 104);
    private static final Color CONTROL_BORDER = new Color(210, 215, 222);
    private static final Color GREEN = new Color(52, 151, 78);
    private static final Color GREEN_DARK = new Color(38, 128, 66);
    private static final Color BLOCKED_GRAY = new Color(86, 89, 94);
    private static final Dimension CONTROL_SIZE = new Dimension(280, 34);
    private final JFrame frame;
    private final DesktopCameraPreferences cameraPreferences;
    private final DesktopUpdatePreferences updatePreferences;
    private final DesktopCameraProvider cameraProvider;
    private final DesktopMotionScoreAnalyzer localAnalyzer;
    private final DesktopLocalRecognitionLifecycle localRecognitionLifecycle;
    private final DesktopUsbReverseSetup usbReverseSetup;
    private final JTextField portField;
    private final JComboBox<String> inputSourceCombo;
    private final JComboBox<DesktopCameraDevice> cameraCombo;
    private final JButton refreshCameraButton;
    private final JButton showCameraButton;
    private final JComboBox<String> modeCombo;
    private final JComboBox<String> keyCombo;
    private final KeyBindingDisplayField customKeyField;
    private final JCheckBox updateChecksBox;
    private final JCheckBox anonymousStatsBox;
    private final JTextField updateServerField;
    private final JButton checkUpdatesButton;
    private final JLabel updateStatusLabel;
    private final JButton startButton;
    private final JButton stopButton;
    private final JLabel serverStatusLabel;
    private final JLabel channelLabel;
    private final JLabel gateStatusLabel;
    private final JLabel lastEventLabel;
    private final JLabel localIpLabel;
    private final JLabel pairingCodeLabel;
    private final JLabel localRecognitionLabel;
    private final JLabel localScoreLabel;
    private final String pairingCode;
    private GateHoldController controller;
    private DesktopGateServer server;
    private volatile DesktopCameraPreviewSession cameraPreviewSession;
    private DesktopLocalGateBridge localGateBridge;
    private JFrame cameraPreviewFrame;
    private final ExecutorService cameraResourceExecutor;
    private final ExecutorService pcLinkSetupExecutor;
    private final ExecutorService mediaPipeWarmupExecutor;
    private final ExecutorService updateCheckExecutor;
    private ScheduledExecutorService localRecognitionExecutor;
    private volatile MediaPipeFaceLandmarkerFrameAnalyzer mediaPipeAnalyzer;
    private MediaPipeFaceLandmarkerFrameAnalyzer warmedMediaPipeAnalyzer;
    private CompletableFuture<MediaPipeFaceLandmarkerFrameAnalyzer> mediaPipeWarmupFuture;
    private volatile AlgorithmProfile cachedAlgorithmProfile;
    private volatile String localEngineLabel = "OpenCV";
    private boolean updatingCameraCombo;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DesktopCompanionApp().show());
    }

    private DesktopCompanionApp() {
        frame = new JFrame("VocaGate Desktop");
        cameraResourceExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vocagate-camera-resource-close");
            thread.setDaemon(true);
            return thread;
        });
        pcLinkSetupExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vocagate-usb-reverse-setup");
            thread.setDaemon(true);
            return thread;
        });
        mediaPipeWarmupExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vocagate-mediapipe-warmup");
            thread.setDaemon(true);
            return thread;
        });
        updateCheckExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vocagate-update-check");
            thread.setDaemon(true);
            return thread;
        });
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopCameraPreview();
                stopServer();
                cancelMediaPipeWarmup();
                closeWarmedMediaPipeAnalyzer(false);
                cameraResourceExecutor.shutdownNow();
                pcLinkSetupExecutor.shutdownNow();
                mediaPipeWarmupExecutor.shutdownNow();
                updateCheckExecutor.shutdownNow();
                closeCameraPreviewWindow();
                frame.dispose();
            }
        });

        cameraPreferences = new DesktopCameraPreferences();
        updatePreferences = new DesktopUpdatePreferences();
        UpdatePreferences loadedUpdatePreferences = updatePreferences.load();
        cachedAlgorithmProfile = loadCachedAlgorithmProfile();
        cameraProvider = DesktopCameraProviderFactory.createDefault();
        localAnalyzer = new DesktopMotionScoreAnalyzer();
        localRecognitionLifecycle = new DesktopLocalRecognitionLifecycle();
        usbReverseSetup = new DesktopUsbReverseSetup();
        portField = new JTextField(String.valueOf(DEFAULT_PORT));
        inputSourceCombo = new JComboBox<>(new String[]{
                DesktopInputSource.PHONE.displayName(),
                DesktopInputSource.LOCAL_CAMERA.displayName()
        });
        inputSourceCombo.setSelectedItem(cameraPreferences.loadInputSource().displayName());
        cameraCombo = new JComboBox<>();
        refreshCameraButton = new JButton("刷新");
        showCameraButton = new JButton("查看摄像头");
        showCameraButton.setEnabled(false);
        modeCombo = new JComboBox<>(new String[]{"Auto", "USB", "Wi-Fi"});
        keyCombo = new JComboBox<>(DesktopKeyOptions.keyOptions());
        customKeyField = new KeyBindingDisplayField("ALT+V");
        customKeyField.setEnabled(false);
        customKeyField.setToolTipText("选择自定义后，点击这里并按下按键或组合键");
        updateChecksBox = new JCheckBox("检查更新", loadedUpdatePreferences.updateChecksEnabled());
        anonymousStatsBox = new JCheckBox("匿名使用统计", loadedUpdatePreferences.anonymousStatsEnabled());
        updateServerField = new JTextField(loadedUpdatePreferences.updateServerUrl());
        checkUpdatesButton = new JButton("检查算法更新");
        updateStatusLabel = new DesktopFixedTextLabel(updateStatusText(loadedUpdatePreferences), new Dimension(210, 32));
        startButton = new JButton("启动");
        stopButton = new JButton("停止");
        stopButton.setEnabled(false);
        serverStatusLabel = new DesktopStatusPill("Stopped");
        channelLabel = new DesktopFixedTextLabel("-", new Dimension(210, 32));
        gateStatusLabel = new DesktopStatusPill("BLOCKED");
        gateStatusLabel.setIcon(new DesktopLineIcon(DesktopLineIcon.Type.LOCK, 14, Color.WHITE));
        gateStatusLabel.setIconTextGap(8);
        gateStatusLabel.setBackground(BLOCKED_GRAY);
        lastEventLabel = new DesktopFixedTextLabel("-", new Dimension(210, 32));
        localIpLabel = new JLabel(localIpAddress());
        pairingCode = String.format(Locale.US, "%06d", RANDOM.nextInt(1000000));
        pairingCodeLabel = new JLabel(pairingCode);
        localRecognitionLabel = new DesktopFixedTextLabel("未启用", new Dimension(210, 32));
        localScoreLabel = new DesktopFixedTextLabel("-", new Dimension(210, 32));

        styleControls();

        startButton.addActionListener(event -> startServer());
        stopButton.addActionListener(event -> stopServer());
        inputSourceCombo.addActionListener(event -> updateInputSource());
        cameraCombo.addActionListener(event -> updateCameraSelection());
        refreshCameraButton.addActionListener(event -> refreshCameraDevices());
        showCameraButton.addActionListener(event -> showCameraPreviewWindow());
        keyCombo.addActionListener(event -> {
            updateCustomKeyField();
            updateKeySpec();
        });
        customKeyField.setOnRequestRebind(this::showCustomKeyDialog);
        modeCombo.addActionListener(event -> updateMode());
        checkUpdatesButton.addActionListener(event -> checkAlgorithmUpdates());

        frame.setContentPane(buildContent());
        updateInputSource();
        maybeAutoCheckAlgorithmUpdates();
        frame.setSize(900, 720);
        frame.setMinimumSize(new Dimension(760, 640));
        frame.setLocationRelativeTo(null);
    }

    private void styleControls() {
        styleCombo(inputSourceCombo);
        styleCombo(cameraCombo);
        styleCombo(modeCombo);
        styleCombo(keyCombo);
        styleTextField(portField);
        styleTextField(updateServerField);
        styleCheckBox(updateChecksBox);
        styleCheckBox(anonymousStatsBox);
        styleValueBox(localIpLabel);
        styleValueBox(pairingCodeLabel);
        styleStatusValue(channelLabel);
        styleStatusValue(lastEventLabel);
        styleStatusValue(localRecognitionLabel);
        styleStatusValue(localScoreLabel);
        styleStatusValue(updateStatusLabel);

        styleSecondaryButton(refreshCameraButton);
        refreshCameraButton.setIcon(icon(DesktopLineIcon.Type.REFRESH, 16));
        refreshCameraButton.setIconTextGap(8);
        refreshCameraButton.setPreferredSize(new Dimension(104, 34));

        styleSecondaryButton(showCameraButton);
        showCameraButton.setIcon(icon(DesktopLineIcon.Type.CAMERA, 16));
        showCameraButton.setHorizontalTextPosition(SwingConstants.LEFT);
        showCameraButton.setIconTextGap(10);

        stylePrimaryButton(startButton);
        startButton.setIcon(new DesktopLineIcon(DesktopLineIcon.Type.PLAY, 17, Color.WHITE));
        startButton.setIconTextGap(8);

        styleSecondaryButton(stopButton);
        stopButton.setIcon(icon(DesktopLineIcon.Type.STOP, 16));
        stopButton.setIconTextGap(8);

        styleSecondaryButton(checkUpdatesButton);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(APP_BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(22, 20, 20, 20));

        JPanel columns = new JPanel(new GridLayout(1, 2, 18, 0));
        columns.setOpaque(false);

        JPanel leftStack = new JPanel();
        leftStack.setOpaque(false);
        leftStack.setLayout(new BoxLayout(leftStack, BoxLayout.Y_AXIS));
        leftStack.add(buildDeviceCard());
        leftStack.add(Box.createVerticalStrut(14));
        leftStack.add(buildKeyCard());
        leftStack.add(Box.createVerticalStrut(14));
        leftStack.add(buildUpdateCard());

        columns.add(leftStack);
        columns.add(buildStatusCard());

        root.add(columns, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildDeviceCard() {
        JPanel body = formPanel();
        int row = 0;
        addFormRow(body, row++, DesktopLineIcon.Type.MIC, "输入来源", inputSourceCombo);

        JPanel cameraPanel = new JPanel(new GridBagLayout());
        cameraPanel.setOpaque(false);
        GridBagConstraints combo = new GridBagConstraints();
        combo.gridx = 0;
        combo.gridy = 0;
        combo.weightx = 1.0;
        combo.fill = GridBagConstraints.HORIZONTAL;
        combo.insets = new Insets(0, 0, 0, 8);
        cameraPanel.add(cameraCombo, combo);
        GridBagConstraints refresh = new GridBagConstraints();
        refresh.gridx = 1;
        refresh.gridy = 0;
        refresh.fill = GridBagConstraints.HORIZONTAL;
        cameraPanel.add(refreshCameraButton, refresh);
        addFormRow(body, row++, DesktopLineIcon.Type.CAMERA, "摄像头", cameraPanel);

        addFormRow(body, row++, DesktopLineIcon.Type.VIDEO, "摄像头预览", showCameraButton);
        addFormRow(body, row++, DesktopLineIcon.Type.LINK, "连接方式", modeCombo);
        addFormRow(body, row++, DesktopLineIcon.Type.NETWORK, "监听端口", portField);
        addFormRow(body, row++, DesktopLineIcon.Type.MONITOR, "本机 IP", localIpLabel);
        addFormRow(body, row, DesktopLineIcon.Type.WIFI, "Wi-Fi 配对码", pairingCodeLabel);
        return card("设备与连接", DesktopLineIcon.Type.MIC, body);
    }

    private JPanel buildKeyCard() {
        JPanel body = formPanel();
        JPanel keyPanel = new JPanel(new GridBagLayout());
        keyPanel.setOpaque(false);

        GridBagConstraints combo = new GridBagConstraints();
        combo.gridx = 0;
        combo.gridy = 0;
        combo.weightx = 1.0;
        combo.fill = GridBagConstraints.HORIZONTAL;
        combo.insets = new Insets(0, 0, 0, 10);
        keyPanel.add(keyCombo, combo);

        GridBagConstraints custom = new GridBagConstraints();
        custom.gridx = 1;
        custom.gridy = 0;
        custom.fill = GridBagConstraints.HORIZONTAL;
        keyPanel.add(customKeyField, custom);

        addFormRow(body, 0, null, "按住按键", keyPanel);
        return card("按键控制", DesktopLineIcon.Type.KEYBOARD, body);
    }

    private JPanel buildUpdateCard() {
        JPanel body = formPanel();
        addFormRow(body, 0, null, "检查更新", updateChecksBox);
        addFormRow(body, 1, null, "匿名统计", anonymousStatsBox);
        addFormRow(body, 2, null, "更新服务器", updateServerField);
        addFormRow(body, 3, null, "状态", updateStatusLabel);
        addFormRow(body, 4, null, "", checkUpdatesButton);
        return card("算法更新", DesktopLineIcon.Type.SERVER, body);
    }

    private JPanel buildStatusCard() {
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);

        JPanel actions = new JPanel(new GridLayout(1, 2, 18, 0));
        actions.setOpaque(false);
        actions.add(startButton);
        actions.add(stopButton);
        body.add(actions, BorderLayout.NORTH);

        DesktopRoundedPanel statusList = new DesktopRoundedPanel(12, CARD_BACKGROUND, CARD_BORDER);
        statusList.setLayout(new GridBagLayout());
        statusList.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        int row = 0;
        row = addStatusRow(statusList, row, DesktopLineIcon.Type.SERVER, "服务状态", serverStatusLabel);
        row = addStatusRow(statusList, row, DesktopLineIcon.Type.SIGNAL, "当前通道", channelLabel);
        row = addStatusRow(statusList, row, DesktopLineIcon.Type.MIC, "开麦状态", gateStatusLabel);
        row = addStatusRow(statusList, row, DesktopLineIcon.Type.CLOCK, "最近事件", lastEventLabel);
        row = addStatusRow(statusList, row, DesktopLineIcon.Type.USER, "本机识别", localRecognitionLabel);
        addStatusRow(statusList, row, DesktopLineIcon.Type.STAR, "本机分数", localScoreLabel);
        body.add(statusList, BorderLayout.CENTER);

        return card("运行状态", DesktopLineIcon.Type.ACTIVITY, body);
    }

    private JPanel card(String title, DesktopLineIcon.Type iconType, JComponent content) {
        DesktopRoundedPanel card = new DesktopRoundedPanel(14, CARD_BACKGROUND, CARD_BORDER);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setIcon(icon(iconType, 22));
        titleLabel.setIconTextGap(12);
        header.add(titleLabel, BorderLayout.WEST);
        card.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        JComponent separator = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                graphics.setColor(CARD_BORDER);
                graphics.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
            }
        };
        separator.setPreferredSize(new Dimension(1, 1));
        center.add(separator, BorderLayout.NORTH);
        center.add(content, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);
        return card;
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    private void addFormRow(JPanel panel, int row, DesktopLineIcon.Type iconType, String label, JComponent value) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0.0;
        labelConstraints.fill = GridBagConstraints.HORIZONTAL;
        labelConstraints.insets = new Insets(row == 0 ? 0 : 12, 0, 0, 14);
        JLabel labelComponent = formLabel(iconType, label);
        panel.add(labelComponent, labelConstraints);

        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = row;
        valueConstraints.weightx = 1.0;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.insets = new Insets(row == 0 ? 0 : 12, 0, 0, 0);
        panel.add(value, valueConstraints);
    }

    private int addStatusRow(JPanel panel, int row, DesktopLineIcon.Type iconType, String label, JComponent value) {
        GridBagConstraints iconConstraints = new GridBagConstraints();
        iconConstraints.gridx = 0;
        iconConstraints.gridy = row;
        iconConstraints.insets = new Insets(14, 20, 14, 12);
        iconConstraints.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(icon(iconType, 22)), iconConstraints);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 1;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0.0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(14, 0, 14, 12);
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(TEXT_PRIMARY);
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.PLAIN, 15f));
        panel.add(labelComponent, labelConstraints);

        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 2;
        valueConstraints.gridy = row;
        valueConstraints.weightx = 1.0;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.insets = new Insets(14, 0, 14, 20);
        panel.add(value, valueConstraints);

        GridBagConstraints separator = new GridBagConstraints();
        separator.gridx = 0;
        separator.gridy = row + 1;
        separator.gridwidth = 3;
        separator.weightx = 1.0;
        separator.fill = GridBagConstraints.HORIZONTAL;
        JComponent line = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                graphics.setColor(new Color(232, 235, 239));
                graphics.drawLine(0, 0, getWidth(), 0);
            }
        };
        line.setPreferredSize(new Dimension(1, 1));
        if (row < 10) {
            panel.add(line, separator);
        }
        return row + 2;
    }

    private JLabel formLabel(DesktopLineIcon.Type iconType, String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_PRIMARY);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 15f));
        label.setPreferredSize(new Dimension(142, 34));
        if (iconType != null) {
            label.setIcon(icon(iconType, 22));
            label.setIconTextGap(12);
        }
        return label;
    }

    private void styleCombo(JComboBox<?> comboBox) {
        comboBox.setPreferredSize(CONTROL_SIZE);
        comboBox.setMinimumSize(new Dimension(120, CONTROL_SIZE.height));
        comboBox.setFont(comboBox.getFont().deriveFont(Font.PLAIN, 14f));
        comboBox.setBackground(Color.WHITE);
    }

    private void styleTextField(JTextField textField) {
        textField.setPreferredSize(CONTROL_SIZE);
        textField.setMinimumSize(new Dimension(120, CONTROL_SIZE.height));
        textField.setFont(textField.getFont().deriveFont(Font.PLAIN, 14f));
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CONTROL_BORDER),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        textField.setBackground(Color.WHITE);
        textField.setForeground(TEXT_PRIMARY);
    }

    private void styleCheckBox(JCheckBox checkBox) {
        checkBox.setOpaque(false);
        checkBox.setForeground(TEXT_PRIMARY);
        checkBox.setFont(checkBox.getFont().deriveFont(Font.PLAIN, 14f));
    }

    private void styleValueBox(JLabel label) {
        label.setOpaque(true);
        label.setPreferredSize(CONTROL_SIZE);
        label.setMinimumSize(new Dimension(120, CONTROL_SIZE.height));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CONTROL_BORDER),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        label.setBackground(Color.WHITE);
        label.setForeground(TEXT_PRIMARY);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
    }

    private void styleStatusValue(JLabel label) {
        label.setForeground(TEXT_PRIMARY);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
    }

    private void stylePrimaryButton(JButton button) {
        DesktopButtonStyles.applyPrimary(button);
    }

    private void styleSecondaryButton(JButton button) {
        DesktopButtonStyles.applySecondary(button);
    }

    private DesktopLineIcon icon(DesktopLineIcon.Type type, int size) {
        return new DesktopLineIcon(type, size, TEXT_PRIMARY);
    }

    private void show() {
        frame.setVisible(true);
    }

    private void checkAlgorithmUpdates() {
        UpdatePreferences saved = saveUpdatePreferencesFromUi();
        runAlgorithmUpdateCheck(saved, true);
    }

    private void maybeAutoCheckAlgorithmUpdates() {
        UpdatePreferences saved = updatePreferences.load();
        if (UpdateSchedule.shouldCheck(saved, System.currentTimeMillis())) {
            runAlgorithmUpdateCheck(saved, false);
        }
    }

    private void runAlgorithmUpdateCheck(UpdatePreferences saved, boolean manual) {
        if (!saved.updateChecksEnabled()) {
            updateStatusLabel.setText("检查更新已关闭");
            return;
        }
        if (manual) {
            checkUpdatesButton.setEnabled(false);
            updateStatusLabel.setText("检查中...");
        } else {
            updateStatusLabel.setText("自动检查中...");
        }
        updateCheckExecutor.execute(() -> {
            UpdateCheckResult result = new UpdateCheckClient(new HttpUrlUpdateClient()).checkNow(new UpdateClientConfig(
                    saved.updateServerUrl(),
                    desktopPlatform(),
                    DesktopDefaults.VERSION_NAME,
                    "stable",
                    "desktop-mediapipe-v1",
                    saved.updateChecksEnabled(),
                    saved.anonymousStatsEnabled()
            ));
            SwingUtilities.invokeLater(() -> {
                if (manual) {
                    checkUpdatesButton.setEnabled(true);
                }
                if (result.success()) {
                    updatePreferences.saveLastProfile(
                            result.checkedAtMs(),
                            result.profile().profileId(),
                            result.profileJson()
                    );
                    applyAlgorithmProfile(result.profile());
                    updateStatusLabel.setText(result.profile().profileId());
                } else {
                    if (result.checkedAtMs() > 0L) {
                        updatePreferences.saveLastCheckedAt(result.checkedAtMs());
                    }
                    String message = result.errorMessage().isEmpty() ? result.statusMessage() : result.errorMessage();
                    updateStatusLabel.setText(shortStatus(message));
                }
            });
        });
    }

    private UpdatePreferences saveUpdatePreferencesFromUi() {
        updatePreferences.saveSettings(
                updateChecksBox.isSelected(),
                anonymousStatsBox.isSelected(),
                updateServerField.getText()
        );
        return updatePreferences.load();
    }

    private String updateStatusText(UpdatePreferences preferences) {
        if (!preferences.updateChecksEnabled()) {
            return "检查更新已关闭";
        }
        if (!preferences.lastProfileId().isEmpty()) {
            return preferences.lastProfileId();
        }
        return "未检查";
    }

    private String desktopPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        if (os.contains("win")) {
            return "desktop-windows";
        }
        if (os.contains("mac")) {
            return "desktop-macos";
        }
        return "desktop";
    }

    private AlgorithmProfile loadCachedAlgorithmProfile() {
        String profileJson = updatePreferences.loadLastProfileJson();
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
        MediaPipeFaceLandmarkerFrameAnalyzer activeAnalyzer = mediaPipeAnalyzer;
        if (activeAnalyzer != null) {
            activeAnalyzer.applyAlgorithmProfile(profile);
        }
        MediaPipeFaceLandmarkerFrameAnalyzer warmedAnalyzer = warmedMediaPipeAnalyzer;
        if (warmedAnalyzer != null) {
            warmedAnalyzer.applyAlgorithmProfile(profile);
        }
    }

    private String shortStatus(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "-";
        }
        String trimmed = message.trim();
        return trimmed.length() > 42 ? trimmed.substring(0, 39) + "..." : trimmed;
    }

    private void startServer() {
        if (server != null) {
            return;
        }

        try {
            int port = parsePort();
            ConnectionMode mode = selectedMode();
            RobotKeyActuator actuator = new RobotKeyActuator();
            controller = new GateHoldController(
                    actuator,
                    selectedKeySpec(),
                    HEARTBEAT_TIMEOUT_MS
            );
            server = new DesktopGateServer(
                    port,
                    controller,
                    new UiListener(),
                    mode,
                    pairingCode
            );
            server.start();
            localGateBridge = new DesktopLocalGateBridge(controller);
            if (selectedInputSource() == DesktopInputSource.PHONE) {
                resetPhoneInputStatus();
                maybePrepareUsbReverse(port, mode);
            }
            if (localRecognitionLifecycle.onDesktopStarted()
                    && selectedInputSource() == DesktopInputSource.LOCAL_CAMERA
                    && cameraPreviewSession != null) {
                startLocalRecognition(cameraPreviewSession);
            }
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portField.setEnabled(false);
            keyCombo.setEnabled(true);
        } catch (RuntimeException e) {
            server = null;
            controller = null;
            serverStatusLabel.setText("启动失败: " + e.getMessage());
        }
    }

    private void stopServer() {
        DesktopGateServer activeServer = server;
        server = null;
        localRecognitionLifecycle.onDesktopStopped();
        stopLocalRecognition();
        if (activeServer != null) {
            activeServer.stop();
        }
        if (controller != null) {
            controller.onDisconnect();
            controller = null;
        }
        localGateBridge = null;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEnabled(true);
        channelLabel.setText("-");
        gateStatusLabel.setText("BLOCKED");
        gateStatusLabel.setBackground(BLOCKED_GRAY);
        serverStatusLabel.setText("Stopped");
        serverStatusLabel.setBackground(BLOCKED_GRAY);
    }

    private void updateKeySpec() {
        if (controller == null) {
            return;
        }

        try {
            controller.setKeySpec(selectedKeySpec());
        } catch (IllegalArgumentException e) {
            serverStatusLabel.setText("按键无效: " + e.getMessage());
        }
    }

    private KeyStrokeSpec selectedKeySpec() {
        return DesktopKeyOptions.resolveKeySpec(
                (String) keyCombo.getSelectedItem(),
                customKeyField.getText()
        );
    }

    private void updateCustomKeyField() {
        boolean custom = DesktopKeyOptions.isCustomOption((String) keyCombo.getSelectedItem());
        customKeyField.setEnabled(custom);
        if (custom) {
            customKeyField.setToolTipText("点击设置自定义按键");
        } else {
            customKeyField.setToolTipText("选择自定义后，点击这里并按下按键或组合键");
        }
    }

    private void showCustomKeyDialog() {
        if (!DesktopKeyOptions.isCustomOption((String) keyCombo.getSelectedItem())) {
            return;
        }

        JDialog dialog = new JDialog(frame, "设置按键", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JLabel title = new JLabel("正在修改按键");
        JLabel hint = new JLabel("请按一个键，或按住 Ctrl/Shift/Alt/Cmd 再按一个键");
        JLabel preview = new JLabel("当前: " + customKeyField.getText());
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(event -> dialog.dispose());

        JPanel messagePanel = new JPanel(new GridLayout(3, 1, 0, 8));
        messagePanel.add(title);
        messagePanel.add(hint);
        messagePanel.add(preview);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(cancelButton, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(8, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        content.add(messagePanel, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setSize(360, 180);
        dialog.setLocationRelativeTo(frame);

        final boolean[] captureCompleted = new boolean[]{false};
        KeyEventDispatcher dispatcher = event -> {
            if (!dialog.isVisible() || !dialog.isActive()) {
                return false;
            }
            if (event.getID() == KeyEvent.KEY_PRESSED) {
                return handleCustomKeyDialogPressed(event, preview, dialog, captureCompleted);
            }
            if (event.getID() == KeyEvent.KEY_RELEASED) {
                return handleCustomKeyDialogReleased(event, dialog, captureCompleted);
            }
            return false;
        };

        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addKeyEventDispatcher(dispatcher);
        try {
            dialog.setVisible(true);
        } finally {
            focusManager.removeKeyEventDispatcher(dispatcher);
        }
    }

    private boolean handleCustomKeyDialogPressed(
            KeyEvent event,
            JLabel preview,
            JDialog dialog,
            boolean[] captureCompleted
    ) {
        if (DesktopKeyCapture.isModifierKey(event.getKeyCode())) {
            captureCompleted[0] = false;
            preview.setText("继续按一个键完成组合键");
            return true;
        }

        Optional<KeyStrokeSpec> captured = DesktopKeyCapture.capture(event);
        if (captured.isPresent()) {
            applyCapturedCustomKey(captured.get());
            captureCompleted[0] = true;
            dialog.dispose();
        } else {
            preview.setText("暂不支持这个按键，请再按一次");
        }
        return true;
    }

    private boolean handleCustomKeyDialogReleased(
            KeyEvent event,
            JDialog dialog,
            boolean[] captureCompleted
    ) {
        if (captureCompleted[0]) {
            return true;
        }

        Optional<KeyStrokeSpec> captured = DesktopKeyCapture.captureReleasedModifier(event);
        if (captured.isPresent()) {
            applyCapturedCustomKey(captured.get());
            captureCompleted[0] = true;
            dialog.dispose();
        }
        return true;
    }

    private void applyCapturedCustomKey(KeyStrokeSpec spec) {
        customKeyField.setText(spec.displayName());
        updateKeySpec();
    }

    private void updateInputSource() {
        DesktopInputSource source = selectedInputSource();
        cameraPreferences.saveInputSource(source);
        boolean localCamera = source == DesktopInputSource.LOCAL_CAMERA;
        cameraCombo.setEnabled(localCamera);
        refreshCameraButton.setEnabled(localCamera);
        updateCameraButtonState();
        if (localCamera) {
            startMediaPipeWarmup();
            refreshCameraDevices();
        } else {
            cancelMediaPipeWarmup();
            closeWarmedMediaPipeAnalyzer(true);
            stopCameraPreviewAsync();
            resetPhoneInputStatus();
            if (server == null) {
                serverStatusLabel.setText("手机 VocaGate 模式");
            }
        }
    }

    private void resetPhoneInputStatus() {
        localRecognitionLabel.setText("未启用");
        localScoreLabel.setText("-");
        lastEventLabel.setText("-");
        gateStatusLabel.setText("BLOCKED");
        gateStatusLabel.setBackground(BLOCKED_GRAY);
    }

    private void maybePrepareUsbReverse(int port, ConnectionMode mode) {
        if (mode == ConnectionMode.WIFI) {
            return;
        }

        lastEventLabel.setText("正在准备 USB reverse...");
        CompletableFuture
                .supplyAsync(() -> usbReverseSetup.prepare(port), pcLinkSetupExecutor)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> applyUsbReverseResult(result)));
    }

    private void applyUsbReverseResult(DesktopUsbReverseSetup.Result result) {
        if (server == null || selectedInputSource() != DesktopInputSource.PHONE || selectedMode() == ConnectionMode.WIFI) {
            return;
        }
        if ("-".equals(channelLabel.getText())) {
            lastEventLabel.setText(result.message());
        }
    }

    private DesktopInputSource selectedInputSource() {
        return DesktopInputSource.fromDisplayName((String) inputSourceCombo.getSelectedItem());
    }

    private void refreshCameraDevices() {
        if (selectedInputSource() != DesktopInputSource.LOCAL_CAMERA) {
            return;
        }
        updatingCameraCombo = true;
        cameraCombo.removeAllItems();
        DesktopCameraDevice selected = null;
        try {
            List<DesktopCameraDevice> devices = cameraProvider.listDevices();
            for (DesktopCameraDevice device : devices) {
                cameraCombo.addItem(device);
            }
            selected = DesktopCameraDevice
                    .selectPreferred(devices, cameraPreferences.loadCameraId().orElse(""))
                    .orElse(null);
            if (selected != null) {
                cameraCombo.setSelectedItem(selected);
            }
        } catch (RuntimeException | LinkageError e) {
            stopCameraPreview();
            serverStatusLabel.setText("摄像头枚举失败");
            localRecognitionLabel.setText("未启用");
            localScoreLabel.setText("摄像头枚举失败: " + exceptionSummary(e));
            return;
        } finally {
            updatingCameraCombo = false;
        }

        if (selected == null) {
            stopCameraPreview();
            serverStatusLabel.setText("未检测到可用摄像头");
            return;
        }
        updateCameraSelection();
    }

    private void updateCameraSelection() {
        if (updatingCameraCombo || selectedInputSource() != DesktopInputSource.LOCAL_CAMERA) {
            return;
        }
        DesktopCameraDevice device = (DesktopCameraDevice) cameraCombo.getSelectedItem();
        if (device == null) {
            return;
        }
        cameraPreferences.saveCameraId(device.id());
        startCameraPreview(device);
    }

    private void startCameraPreview(DesktopCameraDevice device) {
        stopCameraPreview();
        try {
            cameraPreviewSession = cameraProvider.openPreview(device);
            serverStatusLabel.setText("摄像头已连接: " + device.displayName());
            updateCameraButtonState();
            if (localRecognitionLifecycle.onCameraPreviewStarted()) {
                startLocalRecognition(cameraPreviewSession);
            } else {
                localRecognitionLabel.setText("未启用");
                localScoreLabel.setText("点击启动开始识别");
                cameraPreviewSession.updateRecognitionResult(DesktopRecognitionResult.noFrame());
            }
        } catch (RuntimeException | LinkageError e) {
            updateCameraButtonState();
            serverStatusLabel.setText("摄像头启动失败");
            localRecognitionLabel.setText("未启用");
            localScoreLabel.setText("摄像头启动失败: " + exceptionSummary(e));
        }
    }

    private void stopCameraPreview() {
        stopCameraPreview(false);
    }

    private void stopCameraPreviewAsync() {
        stopCameraPreview(true);
    }

    private void stopCameraPreview(boolean asyncClose) {
        localRecognitionLifecycle.onCameraPreviewStopped();
        stopLocalRecognition(asyncClose);
        closeCameraPreviewWindow();
        DesktopCameraPreviewSession session = cameraPreviewSession;
        cameraPreviewSession = null;
        if (session != null) {
            closeResource(session, asyncClose);
        }
        updateCameraButtonState();
    }

    private void startLocalRecognition(DesktopCameraPreviewSession session) {
        stopLocalRecognition();
        localAnalyzer.reset();
        mediaPipeAnalyzer = takeWarmedMediaPipeAnalyzer();
        if (mediaPipeAnalyzer == null) {
            localEngineLabel = "OpenCV fallback";
            localRecognitionLabel.setText("MediaPipe 预热中");
            startMediaPipeWarmup();
        } else {
            localEngineLabel = "MediaPipe";
            localRecognitionLabel.setText("MediaPipe");
        }
        localScoreLabel.setText("-");
        localRecognitionExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vocagate-local-camera-recognition");
            thread.setDaemon(true);
            return thread;
        });
        localRecognitionExecutor.scheduleAtFixedRate(
                () -> analyzeLocalCameraFrame(session),
                0L,
                LOCAL_RECOGNITION_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void stopLocalRecognition() {
        stopLocalRecognition(false);
    }

    private void stopLocalRecognition(boolean asyncClose) {
        ScheduledExecutorService executor = localRecognitionExecutor;
        localRecognitionExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        DesktopLocalGateBridge bridge = localGateBridge;
        if (bridge != null) {
            bridge.onRecognitionResult(DesktopRecognitionResult.noFrame(), System.currentTimeMillis());
        }
        closeMediaPipeAnalyzer(asyncClose);
        localAnalyzer.reset();
        localRecognitionLabel.setText("未启用");
        localScoreLabel.setText("-");
    }

    private void startMediaPipeWarmup() {
        if (mediaPipeAnalyzer != null || warmedMediaPipeAnalyzer != null) {
            return;
        }
        CompletableFuture<MediaPipeFaceLandmarkerFrameAnalyzer> existingFuture = mediaPipeWarmupFuture;
        if (existingFuture != null && !existingFuture.isDone()) {
            return;
        }

        if (localRecognitionExecutor == null && selectedInputSource() == DesktopInputSource.LOCAL_CAMERA) {
            localRecognitionLabel.setText("MediaPipe 预热中");
        }
        mediaPipeWarmupFuture = CompletableFuture
                .supplyAsync(this::createWarmedMediaPipeAnalyzer, mediaPipeWarmupExecutor);
        mediaPipeWarmupFuture.whenComplete((analyzer, throwable) -> SwingUtilities.invokeLater(() ->
                handleMediaPipeWarmupCompleted(analyzer, throwable)
        ));
    }

    private MediaPipeFaceLandmarkerFrameAnalyzer createWarmedMediaPipeAnalyzer() {
        Optional<MediaPipeFaceLandmarkerFrameAnalyzer> optionalAnalyzer = MediaPipeFaceLandmarkerFrameAnalyzer.createDefault(cachedAlgorithmProfile);
        if (!optionalAnalyzer.isPresent()) {
            return null;
        }
        MediaPipeFaceLandmarkerFrameAnalyzer analyzer = optionalAnalyzer.get();
        try {
            analyzer.warmUp();
            return analyzer;
        } catch (RuntimeException | LinkageError e) {
            analyzer.close();
            throw e;
        }
    }

    private void handleMediaPipeWarmupCompleted(
            MediaPipeFaceLandmarkerFrameAnalyzer analyzer,
            Throwable throwable
    ) {
        if (throwable != null) {
            closeQuietly(analyzer);
            if (selectedInputSource() == DesktopInputSource.LOCAL_CAMERA) {
                localEngineLabel = "OpenCV fallback: " + exceptionSummary(throwable);
                if (localRecognitionExecutor == null) {
                    localRecognitionLabel.setText("OpenCV fallback");
                    localScoreLabel.setText(exceptionSummary(throwable));
                }
            }
            return;
        }

        if (analyzer == null) {
            if (selectedInputSource() == DesktopInputSource.LOCAL_CAMERA && localRecognitionExecutor == null) {
                localRecognitionLabel.setText("OpenCV fallback");
            }
            return;
        }
        analyzer.applyAlgorithmProfile(cachedAlgorithmProfile);

        if (selectedInputSource() != DesktopInputSource.LOCAL_CAMERA) {
            closeResource(analyzer, true);
            return;
        }

        if (localRecognitionExecutor != null && mediaPipeAnalyzer == null) {
            mediaPipeAnalyzer = analyzer;
            localAnalyzer.reset();
            localEngineLabel = "MediaPipe";
            localRecognitionLabel.setText("MediaPipe 已接管");
            return;
        }

        warmedMediaPipeAnalyzer = analyzer;
        if (localRecognitionExecutor == null) {
            localRecognitionLabel.setText("MediaPipe 已预热");
            localScoreLabel.setText("点击启动开始识别");
        }
    }

    private MediaPipeFaceLandmarkerFrameAnalyzer takeWarmedMediaPipeAnalyzer() {
        MediaPipeFaceLandmarkerFrameAnalyzer analyzer = warmedMediaPipeAnalyzer;
        warmedMediaPipeAnalyzer = null;
        return analyzer;
    }

    private void cancelMediaPipeWarmup() {
        CompletableFuture<MediaPipeFaceLandmarkerFrameAnalyzer> future = mediaPipeWarmupFuture;
        mediaPipeWarmupFuture = null;
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private void closeWarmedMediaPipeAnalyzer(boolean asyncClose) {
        MediaPipeFaceLandmarkerFrameAnalyzer analyzer = warmedMediaPipeAnalyzer;
        warmedMediaPipeAnalyzer = null;
        if (analyzer != null) {
            closeResource(analyzer, asyncClose);
        }
    }

    private void analyzeLocalCameraFrame(DesktopCameraPreviewSession session) {
        if (session == null || session != cameraPreviewSession) {
            return;
        }

        BufferedImage frame;
        try {
            frame = session.snapshot();
        } catch (RuntimeException | LinkageError e) {
            frame = null;
        }

        DesktopRecognitionResult result;
        long nowMs = System.currentTimeMillis();
        try {
            result = analyzeLocalFrame(frame, nowMs);
        } catch (RuntimeException | LinkageError e) {
            SwingUtilities.invokeLater(() -> {
                if (session != cameraPreviewSession || selectedInputSource() != DesktopInputSource.LOCAL_CAMERA) {
                    return;
                }
                DesktopRecognitionResult noFrame = DesktopRecognitionResult.noFrame();
                localRecognitionLabel.setText("识别失败");
                localScoreLabel.setText(exceptionSummary(e));
                session.updateRecognitionResult(noFrame);
                handleLocalGateResult(noFrame);
            });
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (session != cameraPreviewSession || selectedInputSource() != DesktopInputSource.LOCAL_CAMERA) {
                return;
            }
            session.updateRecognitionResult(result);
            handleLocalGateResult(result);
            localRecognitionLabel.setText(result.statusLabel());
            localScoreLabel.setText(String.format(
                    Locale.US,
                    "%s  score=%.2f  motion=%.2f  fps=%.1f  roi=%s",
                    localEngineLabel,
                    result.score(),
                    result.motionScore(),
                    result.fps(),
                    result.mouthRegionLabel()
            ));
        });
    }

    private DesktopRecognitionResult analyzeLocalFrame(BufferedImage frame, long nowMs) {
        MediaPipeFaceLandmarkerFrameAnalyzer analyzer = mediaPipeAnalyzer;
        if (analyzer != null) {
            try {
                return analyzer.analyze(frame, nowMs);
            } catch (RuntimeException | LinkageError e) {
                localEngineLabel = "OpenCV fallback: " + exceptionSummary(e);
                closeMediaPipeAnalyzer();
                localAnalyzer.reset();
            }
        }
        return localAnalyzer.analyze(frame, nowMs);
    }

    private void closeMediaPipeAnalyzer() {
        closeMediaPipeAnalyzer(false);
    }

    private void closeMediaPipeAnalyzer(boolean asyncClose) {
        MediaPipeFaceLandmarkerFrameAnalyzer analyzer = mediaPipeAnalyzer;
        mediaPipeAnalyzer = null;
        if (analyzer != null) {
            closeResource(analyzer, asyncClose);
        }
    }

    private void closeResource(AutoCloseable closeable, boolean asyncClose) {
        if (closeable == null) {
            return;
        }
        if (asyncClose) {
            DesktopAsyncCloser.closeAsync(closeable, cameraResourceExecutor, throwable -> SwingUtilities.invokeLater(() ->
                    serverStatusLabel.setText("后台关闭摄像头资源失败: " + exceptionSummary(throwable))
            ));
            return;
        }
        try {
            closeable.close();
        } catch (RuntimeException e) {
            serverStatusLabel.setText("关闭摄像头资源失败: " + exceptionSummary(e));
        } catch (Exception e) {
            serverStatusLabel.setText("关闭摄像头资源失败: " + exceptionSummary(e));
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private void showCameraPreviewWindow() {
        DesktopCameraPreviewSession session = cameraPreviewSession;
        if (session == null) {
            serverStatusLabel.setText("摄像头未启动");
            return;
        }
        if (cameraPreviewFrame != null && cameraPreviewFrame.isDisplayable()) {
            cameraPreviewFrame.toFront();
            cameraPreviewFrame.requestFocus();
            return;
        }

        JComponent component = session.component();
        Container parent = component.getParent();
        if (parent != null) {
            parent.remove(component);
            parent.revalidate();
            parent.repaint();
        }

        JFrame preview = new JFrame("VocaGate 摄像头预览");
        preview.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        preview.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (cameraPreviewFrame == preview) {
                    cameraPreviewFrame = null;
                }
            }
        });
        JPanel content = new JPanel(new BorderLayout());
        content.add(component, BorderLayout.CENTER);
        preview.setContentPane(content);
        preview.setSize(760, 520);
        preview.setLocationRelativeTo(frame);
        cameraPreviewFrame = preview;
        preview.setVisible(true);
    }

    private void handleLocalGateResult(DesktopRecognitionResult result) {
        if (selectedInputSource() != DesktopInputSource.LOCAL_CAMERA) {
            return;
        }
        boolean open = isOpenLocalState(result);
        gateStatusLabel.setText(open ? result.state().name() : "BLOCKED");
        gateStatusLabel.setBackground(open ? GREEN : BLOCKED_GRAY);
        lastEventLabel.setText(String.format(
                Locale.US,
                "LOCAL %s  score=%.2f",
                open ? result.state().name() : "BLOCKED",
                result.score()
        ));
        DesktopLocalGateBridge bridge = localGateBridge;
        if (bridge != null) {
            bridge.onRecognitionResult(result, System.currentTimeMillis());
        }
    }

    private boolean isOpenLocalState(DesktopRecognitionResult result) {
        switch (result.state()) {
            case PRE_OPEN:
            case OPEN:
            case HOLD:
                return true;
            default:
                return false;
        }
    }

    private void closeCameraPreviewWindow() {
        JFrame preview = cameraPreviewFrame;
        cameraPreviewFrame = null;
        if (preview != null) {
            preview.setVisible(false);
            preview.dispose();
        }
    }

    private void updateCameraButtonState() {
        showCameraButton.setEnabled(selectedInputSource() == DesktopInputSource.LOCAL_CAMERA && cameraPreviewSession != null);
    }

    private String exceptionSummary(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = throwable.getClass().getSimpleName();
        }
        if (message.length() > 120) {
            return message.substring(0, 117) + "...";
        }
        return message;
    }

    private int parsePort() {
        int port = Integer.parseInt(portField.getText().trim());
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("端口范围无效");
        }
        return port;
    }

    private void updateMode() {
        if (server != null) {
            ConnectionMode mode = selectedMode();
            server.setMode(mode);
            if (selectedInputSource() == DesktopInputSource.PHONE) {
                maybePrepareUsbReverse(parsePort(), mode);
            }
        }
    }

    private ConnectionMode selectedMode() {
        String value = (String) modeCombo.getSelectedItem();
        if ("USB".equals(value)) {
            return ConnectionMode.USB;
        }
        if ("Wi-Fi".equals(value)) {
            return ConnectionMode.WIFI;
        }
        return ConnectionMode.AUTO;
    }

    private String localIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "请查看系统网络 IP";
    }

    private final class UiListener implements DesktopGateServer.Listener {
        @Override
        public void onStatus(String status) {
            SwingUtilities.invokeLater(() -> {
                serverStatusLabel.setText(status);
                serverStatusLabel.setBackground(status.startsWith("Listening") ? GREEN : BLOCKED_GRAY);
            });
        }

        @Override
        public void onChannel(ConnectionChannel channel) {
            SwingUtilities.invokeLater(() -> channelLabel.setText(channel == null ? "-" : channel.name()));
        }

        @Override
        public void onMessage(GateMessage message) {
            if (!message.isState() && !message.isHeartbeat()) {
                return;
            }

            SwingUtilities.invokeLater(() -> {
                if (message.isOpen()) {
                    gateStatusLabel.setText("OPEN");
                    gateStatusLabel.setBackground(GREEN);
                } else {
                    gateStatusLabel.setText("BLOCKED");
                    gateStatusLabel.setBackground(BLOCKED_GRAY);
                }
                lastEventLabel.setText(String.format(
                        Locale.US,
                        "%s  t=%d  score=%.2f",
                        message.isOpen() ? "OPEN" : "BLOCKED",
                        message.androidTimeMs(),
                        message.score()
                ));
            });
        }
    }
}
