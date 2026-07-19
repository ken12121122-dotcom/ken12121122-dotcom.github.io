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

    private TextView statusValue;
    private Button settingsButton;

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

        content.addView(text("全域控制盤", 30f, true, COLOR_TEXT), fullWidth());

        TextView intro = text(
                "這是第一版測試閉環：長按 B 回桌面、長按左右切換桌面頁、方向鍵移動游標、A 點擊並開啟 App。",
                15f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams introParams = fullWidth();
        introParams.topMargin = dp(10);
        content.addView(intro, introParams);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        statusCard.setBackground(surfaceBackground());

        statusCard.addView(text("服務狀態", 13f, true, COLOR_MUTED), fullWidth());

        statusValue = text("檢查中…", 20f, true, COLOR_TEXT);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(5);
        statusCard.addView(statusValue, statusParams);

        LinearLayout.LayoutParams cardParams = fullWidth();
        cardParams.topMargin = dp(22);
        content.addView(statusCard, cardParams);

        settingsButton = primaryButton("開啟 Android 無障礙設定");
        settingsButton.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
        LinearLayout.LayoutParams settingsParams = fullWidth();
        settingsParams.topMargin = dp(18);
        content.addView(settingsButton, settingsParams);

        TextView instructions = text(
                "設定步驟\n\n"
                        + "1. 在「已安裝的應用程式」中找到「Amin 全域控制盤」。\n"
                        + "2. 開啟服務並確認系統提示。\n"
                        + "3. 回到這一頁，狀態應顯示「已啟用」。\n\n"
                        + "操作方式\n\n"
                        + "• 方向鍵短按：移動游標\n"
                        + "• 左右鍵長按：切換桌面頁\n"
                        + "• A：點擊游標位置\n"
                        + "• B 短按：返回\n"
                        + "• B 長按：回到桌面",
                15f,
                false,
                COLOR_TEXT
        );
        instructions.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams instructionParams = fullWidth();
        instructionParams.topMargin = dp(24);
        content.addView(instructions, instructionParams);

        TextView privacy = text(
                "v0.1 不讀取其他 App 的文字、帳號或畫面內容，只送出 Home、Back、滑動與點擊手勢。",
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

    private void refreshStatus() {
        boolean enabled = isUniversalControlEnabled();
        statusValue.setText(enabled ? "已啟用，可以開始測試" : "尚未啟用");
        statusValue.setTextColor(enabled ? COLOR_ACCENT : 0xff9a5b00);
        settingsButton.setText(enabled ? "檢查或關閉服務" : "開啟 Android 無障礙設定");
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

    private TextView text(String value, float sizeSp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
