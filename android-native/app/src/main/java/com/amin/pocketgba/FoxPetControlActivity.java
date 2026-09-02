package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
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

    private TextToSpeech tts;
    private boolean overlayPermissionPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        initTts();
        buildUi();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (overlayPermissionPending && canDrawOverlays()) {
            overlayPermissionPending = false;
            activateMode(FoxPetPreferences.MODE_FOX);
        }
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(Locale.TAIWAN);
                applyTtsSettings();
            }
        });
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(36));
        scroll.addView(root);

        root.addView(text("🦊 狐狸控制面板", 28f, true, COLOR_TEXT), fullWidth());
        TextView intro = text(
                "控制狐狸如何取代語音球、顯示聊天與朗讀回覆。設定沿用同一套 Voice / Chat Runtime。",
                14f,
                false,
                COLOR_MUTED
        );
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
            activateMode(mode);
            String message = FoxPetPreferences.MODE_FOX.equals(mode)
                    ? "狐狸模式已儲存；角色 Overlay 會沿用同一個 Voice Runtime。"
                    : FoxPetPreferences.MODE_HIDDEN.equals(mode)
                    ? "語音入口已隱藏。"
                    : "已切換語音球模式。";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
        root.addView(modes, fullWidth());

        section(root, "狐狸互動");
        Switch draggable = toggle("允許拖曳狐狸", FoxPetPreferences.isDraggable(this));
        draggable.setOnCheckedChangeListener((button, checked) -> FoxPetPreferences.setDraggable(this, checked));
        root.addView(draggable, fullWidth());

        Switch chat = toggle("顯示聊天對話框", FoxPetPreferences.isChatBubbleEnabled(this));
        chat.setOnCheckedChangeListener((button, checked) -> FoxPetPreferences.setChatBubbleEnabled(this, checked));
        root.addView(chat, fullWidth());

        section(root, "狐狸聲音");
        Switch autoSpeak = toggle("AI 回覆自動朗讀", FoxPetPreferences.isAutoSpeakEnabled(this));
        autoSpeak.setOnCheckedChangeListener((button, checked) -> FoxPetPreferences.setAutoSpeakEnabled(this, checked));
        root.addView(autoSpeak, fullWidth());

        sliderRow(root, "語速", FoxPetPreferences.getSpeechRate(this), 0.6f, 1.6f, value -> {
            FoxPetPreferences.setSpeechRate(this, value);
            applyTtsSettings();
        });
        sliderRow(root, "音高", FoxPetPreferences.getPitch(this), 0.6f, 1.6f, value -> {
            FoxPetPreferences.setPitch(this, value);
            applyTtsSettings();
        });
        sliderRow(root, "音量", FoxPetPreferences.getVolume(this), 0f, 1f,
                value -> FoxPetPreferences.setVolume(this, value));

        Button preview = button("試聽狐狸聲音");
        preview.setOnClickListener(v -> previewVoice());
        root.addView(preview, cardParams());

        section(root, "快速控制");
        Button showFox = button("切換成狐狸模式");
        showFox.setOnClickListener(v -> {
            activateMode(FoxPetPreferences.MODE_FOX);
            Toast.makeText(this, "狐狸模式已儲存。", Toast.LENGTH_SHORT).show();
            recreate();
        });
        root.addView(showFox, cardParams());

        Button sleep = button("讓狐狸退下");
        sleep.setOnClickListener(v -> {
            activateMode(FoxPetPreferences.MODE_HIDDEN);
            Toast.makeText(this, "狐狸已退下；Accessibility Service 保持啟用。", Toast.LENGTH_SHORT).show();
            recreate();
        });
        root.addView(sleep, cardParams());

        Button accessibility = button("開啟 Accessibility 設定");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, cardParams());

        TextView note = text(
                "第一版聲音使用 Android 原生 TTS；語速、音高、音量會保存。真正固定角色聲線可後續接 Neural TTS。",
                12f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams noteParams = fullWidth();
        noteParams.topMargin = dp(18);
        root.addView(note, noteParams);

        setContentView(scroll);
    }

    private void previewVoice() {
        if (tts == null) {
            Toast.makeText(this, "TTS 尚未準備好。", Toast.LENGTH_SHORT).show();
            return;
        }
        applyTtsSettings();
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, FoxPetPreferences.getVolume(this));
        tts.speak("我是狐狸，我在。", TextToSpeech.QUEUE_FLUSH, params, "fox-preview");
    }

    private void activateMode(String mode) {
        FoxPetPreferences.setDisplayMode(this, mode);
        // Keep the existing runtime alive; presentation visibility is handled inside it.
        UniversalControlAccessibilityService.setVoiceBubbleEnabled(this, true);
        UniversalControlAccessibilityService.refreshVoicePresentation(this);
        if (FoxPetPreferences.MODE_FOX.equals(mode) && !canDrawOverlays()) {
            overlayPermissionPending = true;
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "請允許顯示在其他應用程式上層，返回後狐狸會自動出現。",
                    Toast.LENGTH_LONG).show();
            return;
        }
        FoxPresentationBridge.applyDisplayMode(this);
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void applyTtsSettings() {
        if (tts == null) return;
        tts.setSpeechRate(FoxPetPreferences.getSpeechRate(this));
        tts.setPitch(FoxPetPreferences.getPitch(this));
    }

    private interface FloatConsumer { void accept(float value); }

    private void sliderRow(LinearLayout root, String label, float current, float min, float max, FloatConsumer consumer) {
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
