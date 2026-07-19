package com.amin.pocketgba;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public final class UniversalControlSetupActivity extends Activity {
    private static final int COLOR_BG = 0xfff4f7f5;
    private static final int COLOR_SURFACE = 0xffffffff;
    private static final int COLOR_TEXT = 0xff16231b;
    private static final int COLOR_MUTED = 0xff68766e;
    private static final int COLOR_ACCENT = 0xff19794b;
    private static final int COLOR_BORDER = 0xffd9e4de;
    private static final int COLOR_WARNING = 0xff9a5b00;

    private TextView statusValue;
    private TextView overlayValue;
    private TextView autoHideValue;
    private TextView modeValue;
    private TextView cursorStepValue;
    private Button settingsButton;
    private Button overlayButton;
    private Button modeButton;
    private SeekBar cursorStepSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(26), dp(22), dp(34));
        scroll.addView(content);

        content.addView(text("GBA 全域控制 v0.4", 30f, true, COLOR_TEXT), fullWidth());

        TextView intro = text(
                "新增可調游標步距與兩種控制模式。游標模式用方向鍵精準移動；捲動模式則直接用方向鍵滑動畫面。",
                15f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams introParams = fullWidth();
        introParams.topMargin = dp(10);
        content.addView(intro, introParams);

        LinearLayout statusCard = surfaceCard();
        statusCard.addView(text("服務狀態", 13f, true, COLOR_MUTED), fullWidth());

        statusValue = text("檢查中…", 20f, true, COLOR_TEXT);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(5);
        statusCard.addView(statusValue, statusParams);

        overlayValue = text("浮動控制：檢查中…", 14f, false, COLOR_MUTED);
        addStatusLine(statusCard, overlayValue);

        modeValue = text("目前模式：檢查中…", 14f, false, COLOR_MUTED);
        addStatusLine(statusCard, modeValue);

        cursorStepValue = text("游標位移：檢查中…", 14f, false, COLOR_MUTED);
        addStatusLine(statusCard, cursorStepValue);

        autoHideValue = text("按鍵自動收合：檢查中…", 14f, false, COLOR_MUTED);
        addStatusLine(statusCard, autoHideValue);

        LinearLayout.LayoutParams cardParams = fullWidth();
        cardParams.topMargin = dp(22);
        content.addView(statusCard, cardParams);

        settingsButton = secondaryButton("開啟 Android 無障礙設定");
        settingsButton.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
        LinearLayout.LayoutParams settingsParams = fullWidth();
        settingsParams.topMargin = dp(16);
        content.addView(settingsButton, settingsParams);

        overlayButton = primaryButton("顯示 GBA 浮動按鍵");
        overlayButton.setOnClickListener(view -> {
            if (!isUniversalControlEnabled()) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            boolean next = !UniversalControlAccessibilityService.isOverlayEnabled(this);
            UniversalControlAccessibilityService.setOverlayEnabled(this, next);
            refreshStatus();
        });
        LinearLayout.LayoutParams overlayParams = fullWidth();
        overlayParams.topMargin = dp(10);
        content.addView(overlayButton, overlayParams);

        content.addView(buildCursorStepCard(), spacedCardParams());
        content.addView(buildModeCard(), spacedCardParams());
        content.addView(buildAutoHideCard(), spacedCardParams());

        TextView instructions = text(
                "操作方式\n\n"
                        + "游標模式\n"
                        + "• 方向鍵短按：依設定距離移動一次\n"
                        + "• 方向鍵長按：持續移動，放開立即停止\n"
                        + "• A：點擊；長按 A：長按畫面\n\n"
                        + "捲動模式\n"
                        + "• 上／下：捲動畫面\n"
                        + "• 左／右：水平切換頁面\n"
                        + "• 長按方向鍵：連續捲動\n\n"
                        + "模式切換\n"
                        + "• 短按 Select：最近使用\n"
                        + "• 長按 Select：切換游標／捲動模式\n"
                        + "• B：返回；長按 B：回桌面\n"
                        + "• Start：回桌面；長按 Start：收起全部按鍵",
                15f,
                false,
                COLOR_TEXT
        );
        instructions.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams instructionParams = fullWidth();
        instructionParams.topMargin = dp(24);
        content.addView(instructions, instructionParams);

        TextView privacy = text(
                "v0.4 不讀取其他 App 的文字、帳號或畫面內容，只送出系統動作、滑動、點擊與長按手勢。",
                13f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams privacyParams = fullWidth();
        privacyParams.topMargin = dp(22);
        content.addView(privacy, privacyParams);

        Button closeButton = secondaryButton("返回控制中心");
        closeButton.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams closeParams = fullWidth();
        closeParams.topMargin = dp(24);
        content.addView(closeButton, closeParams);

        setContentView(scroll);
    }

    private LinearLayout buildCursorStepCard() {
        LinearLayout card = surfaceCard();
        card.addView(text("游標位移距離", 17f, true, COLOR_TEXT), fullWidth());

        TextView hint = text(
                "範圍 2～64 dp。數字越小越精細，數字越大移動越快。新版預設為 16 dp。",
                13f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams hintParams = fullWidth();
        hintParams.topMargin = dp(6);
        card.addView(hint, hintParams);

        TextView liveValue = text("16 dp", 24f, true, COLOR_ACCENT);
        liveValue.setTag("cursor-step-live-value");
        LinearLayout.LayoutParams valueParams = fullWidth();
        valueParams.topMargin = dp(12);
        card.addView(liveValue, valueParams);

        cursorStepSeekBar = new SeekBar(this);
        cursorStepSeekBar.setMin(2);
        cursorStepSeekBar.setMax(64);
        cursorStepSeekBar.setProgress(
                UniversalControlAccessibilityService.getCursorStepDp(this)
        );
        cursorStepSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int safe = Math.max(2, progress);
                liveValue.setText(safe + " dp");
                if (fromUser) {
                    UniversalControlAccessibilityService.setCursorStepDp(
                            UniversalControlSetupActivity.this,
                            safe
                    );
                    refreshStatus();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams seekParams = fullWidth();
        seekParams.topMargin = dp(5);
        card.addView(cursorStepSeekBar, seekParams);

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setWeightSum(3f);
        presets.addView(cursorPresetButton("精細 8", 8), weightedPresetParams());
        presets.addView(cursorPresetButton("標準 16", 16), weightedPresetParams());
        presets.addView(cursorPresetButton("快速 32", 32), weightedPresetParams());
        LinearLayout.LayoutParams presetsParams = fullWidth();
        presetsParams.topMargin = dp(10);
        card.addView(presets, presetsParams);

        return card;
    }

    private LinearLayout buildModeCard() {
        LinearLayout card = surfaceCard();
        card.addView(text("控制模式", 17f, true, COLOR_TEXT), fullWidth());

        TextView hint = text(
                "游標模式適合精準點擊；捲動模式適合 ChatGPT、社群與網頁。長按 Select 可隨時切換。",
                13f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams hintParams = fullWidth();
        hintParams.topMargin = dp(6);
        card.addView(hint, hintParams);

        modeButton = secondaryButton("切換模式");
        modeButton.setOnClickListener(view -> {
            UniversalControlAccessibilityService.toggleControlMode(this);
            refreshStatus();
        });
        LinearLayout.LayoutParams buttonParams = fullWidth();
        buttonParams.topMargin = dp(12);
        card.addView(modeButton, buttonParams);
        return card;
    }

    private LinearLayout buildAutoHideCard() {
        LinearLayout card = surfaceCard();
        card.addView(text("按鍵自動收合", 17f, true, COLOR_TEXT), fullWidth());

        TextView hint = text(
                "GBA 按鍵展開後若沒有操作，到時間會全部收起；喚醒球仍留在螢幕邊緣。",
                13f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams hintParams = fullWidth();
        hintParams.topMargin = dp(6);
        card.addView(hint, hintParams);

        LinearLayout optionRow = new LinearLayout(this);
        optionRow.setOrientation(LinearLayout.HORIZONTAL);
        optionRow.setWeightSum(4f);
        optionRow.addView(autoHideOption("關閉", 0), weightedOptionParams());
        optionRow.addView(autoHideOption("5 秒", 5), weightedOptionParams());
        optionRow.addView(autoHideOption("10 秒", 10), weightedOptionParams());
        optionRow.addView(autoHideOption("20 秒", 20), weightedOptionParams());
        LinearLayout.LayoutParams rowParams = fullWidth();
        rowParams.topMargin = dp(12);
        card.addView(optionRow, rowParams);
        return card;
    }

    private Button cursorPresetButton(String label, int stepDp) {
        Button button = optionButton(label);
        button.setOnClickListener(view -> {
            UniversalControlAccessibilityService.setCursorStepDp(this, stepDp);
            if (cursorStepSeekBar != null) {
                cursorStepSeekBar.setProgress(stepDp);
            }
            refreshStatus();
        });
        return button;
    }

    private Button autoHideOption(String label, int seconds) {
        Button button = optionButton(label);
        button.setOnClickListener(view -> {
            UniversalControlAccessibilityService.setAutoHideSeconds(this, seconds);
            refreshStatus();
        });
        return button;
    }

    private void refreshStatus() {
        boolean serviceEnabled = isUniversalControlEnabled();
        boolean overlayEnabled = UniversalControlAccessibilityService.isOverlayEnabled(this);
        int autoHideSeconds = UniversalControlAccessibilityService.getAutoHideSeconds(this);
        int cursorStep = UniversalControlAccessibilityService.getCursorStepDp(this);
        String modeLabel = UniversalControlAccessibilityService.getControlModeLabel(this);

        statusValue.setText(serviceEnabled ? "已啟用，可以使用" : "尚未啟用");
        statusValue.setTextColor(serviceEnabled ? COLOR_ACCENT : COLOR_WARNING);
        overlayValue.setText("浮動控制：" + (overlayEnabled ? "顯示中" : "已暫停"));
        modeValue.setText("目前模式：" + modeLabel);
        cursorStepValue.setText("游標位移：" + cursorStep + " dp");
        autoHideValue.setText(
                "按鍵自動收合：" + (autoHideSeconds == 0 ? "關閉" : autoHideSeconds + " 秒")
        );

        settingsButton.setText(serviceEnabled ? "檢查或關閉無障礙服務" : "首次啟用無障礙服務");
        overlayButton.setEnabled(serviceEnabled);
        overlayButton.setAlpha(serviceEnabled ? 1f : 0.5f);
        overlayButton.setText(overlayEnabled ? "暫停 GBA 浮動按鍵" : "顯示 GBA 浮動按鍵");
        modeButton.setText(
                UniversalControlAccessibilityService.MODE_SCROLL.equals(
                        UniversalControlAccessibilityService.getControlMode(this)
                ) ? "切換到游標模式" : "切換到捲動模式"
        );

        if (cursorStepSeekBar != null && cursorStepSeekBar.getProgress() != cursorStep) {
            cursorStepSeekBar.setProgress(cursorStep);
        }
    }

    private boolean isUniversalControlEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null || enabledServices.isBlank()) {
            return false;
        }

        ComponentName expected = new ComponentName(
                this,
                UniversalControlAccessibilityService.class
        );
        for (String entry : enabledServices.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(entry);
            if (expected.equals(component)) {
                return true;
            }
        }
        return false;
    }

    private void addStatusLine(LinearLayout card, TextView value) {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(5);
        card.addView(value, params);
    }

    private LinearLayout surfaceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(surfaceBackground());
        return card;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_ACCENT));
        button.setMinHeight(dp(52));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(15f);
        button.setBackgroundTintList(ColorStateList.valueOf(0xffe4ece7));
        button.setMinHeight(dp(50));
        return button;
    }

    private Button optionButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(12f);
        button.setMinWidth(0);
        button.setMinHeight(dp(44));
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(0xffe4ece7));
        return button;
    }

    private TextView text(String value, float sizeSp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.2f);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable surfaceBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_SURFACE);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), COLOR_BORDER);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams spacedCardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(20);
        return params;
    }

    private LinearLayout.LayoutParams weightedOptionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams weightedPresetParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
