package com.vocagate.demo;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.core.update.HttpUrlUpdateClient;
import com.vocagate.core.update.UpdateCheckClient;
import com.vocagate.core.update.UpdateCheckResult;
import com.vocagate.core.update.UpdateClientConfig;
import com.vocagate.core.update.UpdatePreferences;
import com.vocagate.core.update.UpdateProtocolParser;
import com.vocagate.demo.core.UpdateStatusFormatter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateSettingsActivity extends ComponentActivity {
    private static final String PREFS_UPDATE = "update";
    private static final String PREF_UPDATE_CHECKS_ENABLED = "update_checks_enabled";
    private static final String PREF_ANONYMOUS_STATS_ENABLED = "anonymous_stats_enabled";
    private static final String PREF_UPDATE_SERVER_URL = "update_server_url";
    private static final String PREF_UPDATE_LAST_CHECKED_AT_MS = "update_last_checked_at_ms";
    private static final String PREF_UPDATE_LAST_PROFILE_ID = "update_last_profile_id";
    private static final String PREF_UPDATE_LAST_PROFILE_JSON = "update_last_profile_json";
    private static final String PREF_UPDATE_LAST_PROFILE_UPDATED_AT_MS = "update_last_profile_updated_at_ms";
    private static final String ANDROID_APP_VERSION = "0.1.0";

    private SharedPreferences updatePreferences;
    private ExecutorService updateExecutor;
    private final AtomicBoolean updateCheckRunning = new AtomicBoolean(false);
    private TextView statusLabel;
    private TextView profileLabel;
    private CheckBox updateChecksCheckBox;
    private CheckBox anonymousStatsCheckBox;
    private EditText updateServerEditText;
    private Button checkButton;
    private boolean bindingUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updatePreferences = getSharedPreferences(PREFS_UPDATE, MODE_PRIVATE);
        updateExecutor = Executors.newSingleThreadExecutor();
        buildUi();
        refreshLabels(loadUpdatePreferences());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateExecutor != null) {
            updateExecutor.shutdown();
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(22, 24, 28));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("算法更新");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22.0f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(title, matchWrap());

        statusLabel = smallText();
        statusLabel.setPadding(0, dp(14), 0, 0);
        content.addView(statusLabel, matchWrap());

        profileLabel = smallText();
        profileLabel.setPadding(0, dp(4), 0, dp(12));
        content.addView(profileLabel, matchWrap());

        UpdatePreferences loaded = loadUpdatePreferences();
        bindingUi = true;
        updateChecksCheckBox = checkbox("检查更新", loaded.updateChecksEnabled());
        anonymousStatsCheckBox = checkbox("匿名使用统计", loaded.anonymousStatsEnabled());
        bindingUi = false;
        updateChecksCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> onSettingChanged());
        anonymousStatsCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> onSettingChanged());
        content.addView(updateChecksCheckBox, matchWrap());
        content.addView(anonymousStatsCheckBox, matchWrap());

        TextView serverTitle = smallText();
        serverTitle.setText("更新服务器 URL");
        serverTitle.setPadding(0, dp(14), 0, dp(4));
        content.addView(serverTitle, matchWrap());

        updateServerEditText = new EditText(this);
        updateServerEditText.setSingleLine(true);
        updateServerEditText.setTextColor(Color.WHITE);
        updateServerEditText.setHintTextColor(Color.rgb(170, 170, 170));
        updateServerEditText.setTextSize(16.0f);
        updateServerEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        updateServerEditText.setHint("https://updates.vocagate.app");
        updateServerEditText.setText(loaded.updateServerUrl());
        updateServerEditText.setBackground(inputBackground());
        updateServerEditText.setPadding(dp(12), 0, dp(12), 0);
        content.addView(updateServerEditText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        checkButton = new Button(this);
        checkButton.setAllCaps(false);
        checkButton.setText("立即检查算法更新");
        checkButton.setOnClickListener(view -> checkAlgorithmUpdates());
        LinearLayout.LayoutParams checkParams = matchWrap();
        checkParams.topMargin = dp(14);
        content.addView(checkButton, checkParams);

        Button closeButton = new Button(this);
        closeButton.setAllCaps(false);
        closeButton.setText("返回");
        closeButton.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams closeParams = matchWrap();
        closeParams.topMargin = dp(8);
        content.addView(closeButton, closeParams);

        setContentView(scrollView);
    }

    private void checkAlgorithmUpdates() {
        UpdatePreferences saved = saveUpdatePreferencesFromUi();
        runAlgorithmUpdateCheck(saved);
    }

    private void onSettingChanged() {
        if (bindingUi) {
            return;
        }
        UpdatePreferences saved = saveUpdatePreferencesFromUi();
        refreshLabels(saved);
        if (saved.updateChecksEnabled()) {
            runAlgorithmUpdateCheck(saved);
        }
    }

    private void runAlgorithmUpdateCheck(UpdatePreferences saved) {
        if (!saved.updateChecksEnabled()) {
            refreshLabels(saved);
            return;
        }
        if (!updateCheckRunning.compareAndSet(false, true)) {
            statusLabel.setText("检查中...");
            return;
        }
        checkButton.setEnabled(false);
        statusLabel.setText("检查中...");
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
            runOnUiThread(() -> handleUpdateResult(result));
        });
    }

    private void handleUpdateResult(UpdateCheckResult result) {
        updateCheckRunning.set(false);
        checkButton.setEnabled(true);
        if (result.success()) {
            updatePreferences.edit()
                    .putLong(PREF_UPDATE_LAST_CHECKED_AT_MS, result.checkedAtMs())
                    .putString(PREF_UPDATE_LAST_PROFILE_ID, result.profile().profileId())
                    .putString(PREF_UPDATE_LAST_PROFILE_JSON, result.profileJson())
                    .putLong(PREF_UPDATE_LAST_PROFILE_UPDATED_AT_MS, result.checkedAtMs())
                    .apply();
            statusLabel.setText("已更新 " + result.profile().profileId());
            refreshProfileLabel();
            return;
        }

        if (result.checkedAtMs() > 0L) {
            updatePreferences.edit()
                    .putLong(PREF_UPDATE_LAST_CHECKED_AT_MS, result.checkedAtMs())
                    .apply();
        }
        String message = result.errorMessage().isEmpty() ? result.statusMessage() : result.errorMessage();
        statusLabel.setText(UpdateStatusFormatter.shortStatus(message));
        refreshProfileLabel();
    }

    private UpdatePreferences saveUpdatePreferencesFromUi() {
        boolean updateChecksEnabled = updateChecksCheckBox == null || updateChecksCheckBox.isChecked();
        boolean anonymousStatsEnabled = anonymousStatsCheckBox == null || anonymousStatsCheckBox.isChecked();
        String serverUrl = updateServerEditText == null
                ? UpdatePreferences.DEFAULT_UPDATE_SERVER_URL
                : updateServerEditText.getText().toString().trim();
        updatePreferences.edit()
                .putBoolean(PREF_UPDATE_CHECKS_ENABLED, updateChecksEnabled)
                .putBoolean(PREF_ANONYMOUS_STATS_ENABLED, anonymousStatsEnabled)
                .putString(PREF_UPDATE_SERVER_URL, UpdatePreferences.normalizeServerUrl(serverUrl))
                .apply();
        return loadUpdatePreferences();
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

    private void refreshLabels(UpdatePreferences preferences) {
        statusLabel.setText(UpdateStatusFormatter.statusText(preferences));
        refreshProfileLabel();
    }

    private void refreshProfileLabel() {
        profileLabel.setText(UpdateStatusFormatter.profileText(loadCachedAlgorithmProfile(), loadLastProfileUpdatedAtMs()));
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

    private long loadLastProfileUpdatedAtMs() {
        long updatedAtMs = updatePreferences.getLong(PREF_UPDATE_LAST_PROFILE_UPDATED_AT_MS, 0L);
        if (updatedAtMs > 0L) {
            return updatedAtMs;
        }
        return loadCachedAlgorithmProfile() == null
                ? 0L
                : updatePreferences.getLong(PREF_UPDATE_LAST_CHECKED_AT_MS, 0L);
    }

    private CheckBox checkbox(String text, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setTextColor(Color.WHITE);
        checkBox.setTextSize(16.0f);
        checkBox.setChecked(checked);
        return checkBox;
    }

    private TextView smallText() {
        TextView textView = new TextView(this);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(13.0f);
        return textView;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(70, 255, 255, 255));
        background.setStroke(dp(1), Color.argb(140, 255, 255, 255));
        background.setCornerRadius(dp(8));
        return background;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
