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
    private Button settingsButton;
    private Button overlayButton;

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

        content.addView(text("全域控制盤 v0.2", 30f, true, COLOR_TEXT), fullWidth());

        TextView intro = text(
                "無障礙服務只需首次在 Android 設定啟用。之後由 App 暫停或恢復浮動控制，平常用喚醒球展開鍵盤。",
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
        LinearLayout.LayoutParams overlayValueParams = fullWidth();
        overlayValueParams.topMargin = dp(7);
        statusCard.addView(overlayValue, overlayValueParams);

        autoHideValue = text("鍵盤自動收合：檢查中…", 14f, false, COLOR_MUTED);
        LinearLayout.LayoutParams autoHideValueParams = fullWidth();
        autoHideValueParams.topMargin = dp(4);
        statusCard.addView(autoHideValue, autoHideValueParams);

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

        overlayButton = primaryButton("顯示浮動喚醒球");
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

        LinearLayout autoHideCard = surfaceCard();
        autoHideCard.addView(text("鍵盤自動收合", 17f, true, COLOR_TEXT), fullWidth());

        TextView autoHideHint = text(
                "控制盤展開後若沒有操作，到時間會自動收起；喚醒球仍會留在螢幕邊緣。",
                13f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams hintParams = fullWidth();
        hintParams.topMargin = dp(6);
        autoHideCard.addView(autoHideHint, hintParams);

        LinearLayout optionRow = new LinearLayout(this);
        optionRow.setOrientation(LinearLayout.HORIZONTAL);
        optionRow.setWeightSum(4f);
        LinearLayout.LayoutParams optionRowParams = fullWidth();
        optionRowParams.topMargin = dp(12);

        optionRow.addView(autoHideOption("關閉", 0), weightedOptionParams());
        optionRow.addView(autoHideOption("5 秒", 5), weightedOptionParams());
        optionRow.addView(autoHideOption("10 秒", 10), weightedOptionParams());
        optionRow.addView(autoHideOption("20 秒", 20), weightedOptionParams());
        autoHideCard.addView(optionRow, optionRowParams);

        LinearLayout.LayoutParams autoHideCardParams = fullWidth();
        autoHideCardParams.topMargin = dp(20);
        content.addView(autoHideCard, autoHideCardParams);

        TextView instructions = text(
                "操作方式\n\n"
                        + "• 浮動喚醒球：點一下展開／收起，拖曳可移到左右邊緣\n"
                        + "• 喚醒球 2 秒未碰：自動淡化，碰到立即恢復\n"
                        + "• 方向鍵：移動游標；左右長按：水平切換頁面\n"
                        + "• A：點擊游標；B：返回；B 長按：回桌面\n"
                        + "• ⌂：首頁　▣：最近使用\n"
                        + "• ⇧／⇩：頁面上下捲動　●：游標長按　—：立即收起",
                15f,
                false,
                COLOR_TEXT
        );
        instructions.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams instructionParams = fullWidth();
        instructionParams.topMargin = dp(24);
        content.addView(instructions, instructionParams);

        TextView privacy = text(
                "v0.2 不讀取其他 App 的文字、帳號或畫面內容，只送出系統動作、滑動、點擊與長按手勢。",
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

        statusValue.setText(serviceEnabled ? "已啟用，可以使用" : "尚未啟用");
        statusValue.setTextColor(serviceEnabled ? COLOR_ACCENT : COLOR_WARNING);
        overlayValue.setText("浮動控制：" + (overlayEnabled ? "顯示中" : "已暫停"));
        autoHideValue.setText(
                "鍵盤自動收合：" + (autoHideSeconds == 0 ? "關閉" : autoHideSeconds + " 秒")
        );

        settingsButton.setText(serviceEnabled ? "檢查或關閉無障礙服務" : "首次啟用無障礙服務");
        overlayButton.setEnabled(serviceEnabled);
        overlayButton.setAlpha(serviceEnabled ? 1f : 0.5f);
        overlayButton.setText(overlayEnabled ? "暫停浮動控制" : "顯示浮動喚醒球");
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

    private LinearLayout.LayoutParams weightedOptionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
