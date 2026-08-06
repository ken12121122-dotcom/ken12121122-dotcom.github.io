package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class PromptKeyboardSetupActivity extends Activity {
    private TextView state;
    private PromptStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new PromptStore(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xfff4f7f5);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(32));
        scroll.addView(content);

        Button back = button("← 返回控制台");
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setOnClickListener(view -> finish());
        content.addView(back, fullWidth());

        TextView eyebrow = text("PROMPT KEYBOARD", 12f, true, 0xff19794b);
        LinearLayout.LayoutParams eyebrowParams = fullWidth();
        eyebrowParams.topMargin = dp(18);
        content.addView(eyebrow, eyebrowParams);
        content.addView(text("提示詞鍵盤", 30f, true, 0xff16231b), fullWidth());

        state = text("檢查中…", 15f, false, 0xff68766e);
        LinearLayout.LayoutParams stateParams = fullWidth();
        stateParams.topMargin = dp(12);
        stateParams.bottomMargin = dp(18);
        content.addView(state, stateParams);

        Button enable = button("1. 啟用 Amin 提示詞鍵盤");
        enable.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        content.addView(enable, fullWidth());

        Button choose = button("2. 選擇目前鍵盤");
        choose.setOnClickListener(view -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        });
        LinearLayout.LayoutParams chooseParams = fullWidth();
        chooseParams.topMargin = dp(8);
        content.addView(choose, chooseParams);

        TextView instructions = text(
                "使用方式：在其他 App 選取文字，點選「存到提示詞」，選擇分類；之後切換到 Amin 提示詞鍵盤即可直接輸入。",
                15f,
                false,
                0xff16231b
        );
        instructions.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams instructionParams = fullWidth();
        instructionParams.topMargin = dp(22);
        content.addView(instructions, instructionParams);
        setContentView(scroll);
    }

    private void refreshState() {
        boolean enabled = false;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            List<InputMethodInfo> methods = manager.getEnabledInputMethodList();
            for (InputMethodInfo method : methods) {
                if (getPackageName().equals(method.getPackageName())) {
                    enabled = true;
                    break;
                }
            }
        }
        state.setText((enabled ? "鍵盤已啟用" : "鍵盤尚未啟用")
                + " · 已儲存 " + store.countPrompts() + " 筆提示詞");
        state.setTextColor(enabled ? 0xff19794b : 0xff9a5b00);
    }

    @Override
    protected void onDestroy() {
        if (store != null) store.close();
        super.onDestroy();
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15f);
        button.setTextColor(0xff105f39);
        button.setMinHeight(dp(54));
        return button;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
