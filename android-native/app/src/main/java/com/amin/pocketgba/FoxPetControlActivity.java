package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class FoxPetControlActivity extends Activity {
    private static final int COLOR_BG = 0xfff4f7f5;
    private static final int COLOR_TEXT = 0xff16231b;
    private static final int COLOR_MUTED = 0xff68766e;
    private static final int COLOR_ACCENT = 0xff19794b;

    private TextView speechRateValue;
    private TextView pitchValue;
    private TextView volumeValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(36));
        scroll.addView(root);

        TextView title = text("🦊 狐狸控制面板", 28f, true, COLOR_TEXT);
        root.addView(title, fullWidth());
        TextView intro = text("控制狐狸如何取代語音球、顯示聊天與朗讀回覆。所有設定沿用同一套 Voice / Chat Runtime。", 14f, false, COLOR_MUTED);
        LinearLayout.LayoutParams introParams = fullWidth();
        introParams.topMargin = dp(8);
        root.addView(intro, introParams);

        section(root, "顯示模式");
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        RadioButton voice = radio("🎙 語音球");
        RadioButton fox = radio("🦊 狐狸模式");
        RadioButton hidden = radio("隱藏入口");
        modes.addView(voice);
        modes.addView(fox);
        modes.addView(hidden);
        String current = FoxPetPreferences.getDisplayMode(this);
        if (FoxPetPreferences.MODE_FOX.equals(current)) fox.setChecked(true);
        else if (FoxPetPreferences.MODE_HIDDEN.equals(current)) hidden.setChecked(true);
        else voice.setChecked(true);
        modes.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = checkedId == fox.getId() ? FoxPetPreferences.MODE_FOX
                    : checkedId == hidden.getId() ? FoxPetPreferences.MODE_HIDDEN
                    : FoxPetPreferences.MODE_VOICE_BALL;
            FoxPetPreferences.setDisplayMode(this, mode);
            applyPresentation(mode);
        });
        root.addView(modes, fullWidth());

        section(root, "狐狸互動");
        Switch draggable = toggle("允許拖曳狐狸", FoxPetPreferences.isDraggable(this));
        draggable.setOnCheckedChangeListener((button, checked) -> {
            FoxPetPreferences.setDraggable(this, checked);
            UniversalControlAccessibilityService.refreshVoicePresentation(this);
        });
        root.addView(draggable, fullWidth());

        Switch chat = toggle("顯示聊天對話框", FoxPetPreferences.isChatBubbleEnabled(this));
        chat.setOnCheckedChangeListener((button, checked) -> {
            FoxPetPreferences.setChatBubbleEnabled(this, checked);
            UniversalControlAccessibilityService.refreshVoicePresentation(this);
        });
        root.addView(chat, fullWidth());

        section(root, "狐狸聲音");
        Switch autoSpeak = toggle("AI 回覆自動朗讀", FoxPetPreferences.isAutoSpeakEnabled(this));
        autoSpeak.setOnCheckedChangeListener((button, checked) -> {
            FoxPetPreferences.setAutoSpeakEnabled(this, checked);
            UniversalControlAccessibilityService.refreshVoicePresentation(this);
        });
        root.addView(autoSpeak, fullWidth());

        speechRateValue = sliderRow(root, "語速", FoxPetPreferences.getSpeechRate(this), 0.6f, 1.6f,
                value -> {
                    FoxPetPreferences.setSpeechRate(this, value);
                    UniversalControlAccessibilityService.refreshVoicePresentation(this);
                });
        pitchValue = sliderRow(root, "音高", FoxPetPreferences.getPitch(this), 0.6f, 1.6f,
                value -> {
                    FoxPetPreferences.setPitch(this, value);
                    UniversalControlAccessibilityService.refreshVoicePresentation(this);
                });
        volumeValue = sliderRow(root, "音量", FoxPetPreferences.getVolume(this), 0f, 1f,
                value -> {
                    FoxPetPreferences.setVolume(this, value);
                    UniversalControlAccessibilityService.refreshVoicePresentation(this);
                });

        Button preview = button("試聽狐狸聲音");
        preview.setOnClickListener(v -> UniversalControlAccessibilityService.previewFoxVoice(this));
        root.addView(preview, cardParams());

        section(root, "快速控制");
        Button showFox = button("立即顯示狐狸");
        showFox.setOnClickListener(v -> {
            FoxPetPreferences.setDisplayMode(this, FoxPetPreferences.MODE_FOX);
            applyPresentation(FoxPetPreferences.MODE_FOX);
        });
        root.addView(showFox, cardParams());

        Button sleep = button("讓狐狸退下");
        sleep.setOnClickListener(v -> UniversalControlAccessibilityService.requestFoxDormant(this));
        root.addView(sleep, cardParams());

        Button accessibility = button("開啟 Accessibility 設定");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, cardParams());

        TextView note = text("第一版使用 Android 原生 TTS。真正固定角色聲線可在後續接 Neural TTS，但不需要改 Chat Runtime。", 12f, false, COLOR_MUTED);
        LinearLayout.LayoutParams noteParams = fullWidth();
        noteParams.topMargin = dp(18);
        root.addView(note, noteParams);

        setContentView(scroll);
    }

    private void applyPresentation(String mode) {
        UniversalControlAccessibilityService.setVoiceBubbleEnabled(this, !FoxPetPreferences.MODE_HIDDEN.equals(mode));
        UniversalControlAccessibilityService.refreshVoicePresentation(this);
        Toast.makeText(this, FoxPetPreferences.MODE_FOX.equals(mode) ? "已切換狐狸模式" : FoxPetPreferences.MODE_HIDDEN.equals(mode) ? "已隱藏語音入口" : "已切換語音球模式", Toast.LENGTH_SHORT).show();
    }

    private interface FloatConsumer { void accept(float value); }

    private TextView sliderRow(LinearLayout root, String label, float current, float min, float max, FloatConsumer consumer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(label, 15f, true, COLOR_TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView valueText = text(String.format(Locale.TAIWAN, "%.2f", current), 13f, false, COLOR_MUTED);
        header.addView(valueText);
        row.addView(header, fullWidth());
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        int progress = Math.round((current - min) / (max - min) * 100f);
        bar.setProgress(Math.max(0, Math.min(100, progress)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (!fromUser) return;
                float value = min + (max - min) * (p / 100f);
                valueText.setText(String.format(Locale.TAIWAN, "%.2f", value));
                consumer.accept(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        row.addView(bar, fullWidth());
        root.addView(row, cardParams());
        return valueText;
    }

    private void section(LinearLayout root, String value) {
        TextView view = text(value, 18f, true, COLOR_TEXT);
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(24);
        params.bottomMargin = dp(8);
        root.addView(view, params);
    }

    private RadioButton radio(String label) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setTextSize(16f);
        button.setTextColor(COLOR_TEXT);
        button.setPadding(0, dp(7), 0, dp(7));
        return button;
    }

    private Switch toggle(String label, boolean checked) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextSize(16f);
        view.setTextColor(COLOR_TEXT);
        view.setChecked(checked);
        view.setPadding(0, dp(10), 0, dp(10));
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15f);
        return button;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(8);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
