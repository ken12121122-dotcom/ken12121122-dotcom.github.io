package com.amin.pocketgba;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class LlmSettingsActivity extends Activity {
    private Spinner providerSpinner;
    private EditText modelInput;
    private EditText apiKeyInput;
    private TextView statusView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("LLM 設定");
        buildUi();
        load();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(32));
        root.setBackgroundColor(0xfff4f7f5);
        scroll.addView(root);

        root.addView(text("LLM 設定", 28, true, 0xff16231b));
        TextView note = text("API Key 只保存在此手機，使用 Android Keystore 加密；不會寫入 GitHub 或聊天紀錄。", 13, false, 0xff68766e);
        LinearLayout.LayoutParams noteParams = full(); noteParams.topMargin = dp(6); root.addView(note, noteParams);

        root.addView(label("Provider"), top(20));
        providerSpinner = new Spinner(this);
        String[] labels = {"Gemini", "OpenAI", "Claude"};
        providerSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        root.addView(providerSpinner, full());

        root.addView(label("Model"), top(16));
        modelInput = new EditText(this);
        modelInput.setSingleLine(true);
        modelInput.setHint("例如 gemini-2.5-flash");
        root.addView(modelInput, full());

        root.addView(label("API Key"), top(16));
        apiKeyInput = new EditText(this);
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setHint("貼上 API Key；留白會保留原 Key");
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(apiKeyInput, full());

        Button save = new Button(this); save.setText("儲存設定"); save.setAllCaps(false);
        save.setOnClickListener(v -> save(false));
        root.addView(save, top(22));

        Button test = new Button(this); test.setText("儲存並測試連線"); test.setAllCaps(false);
        test.setOnClickListener(v -> save(true));
        root.addView(test, top(8));

        statusView = text("尚未測試", 14, true, 0xff19794b);
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView, top(14));
        setContentView(scroll);
    }

    private void load() {
        String provider = LlmConfigStore.provider(this);
        providerSpinner.setSelection(LlmConfigStore.PROVIDER_OPENAI.equals(provider) ? 1 : LlmConfigStore.PROVIDER_CLAUDE.equals(provider) ? 2 : 0);
        modelInput.setText(LlmConfigStore.model(this));
        apiKeyInput.setHint(LlmConfigStore.hasApiKey(this) ? "已儲存 API Key；留白可保留" : "貼上 API Key");
    }

    private void save(boolean test) {
        String provider = providerSpinner.getSelectedItemPosition() == 1 ? LlmConfigStore.PROVIDER_OPENAI : providerSpinner.getSelectedItemPosition() == 2 ? LlmConfigStore.PROVIDER_CLAUDE : LlmConfigStore.PROVIDER_GEMINI;
        String model = modelInput.getText().toString().trim();
        String key = apiKeyInput.getText().toString().trim();
        try {
            LlmConfigStore.save(this, provider, model, key);
            apiKeyInput.setText("");
            apiKeyInput.setHint("已儲存 API Key；留白可保留");
            statusView.setText("已儲存 · " + LlmConfigStore.label(this));
            if (!test) { Toast.makeText(this, "LLM 設定已儲存", Toast.LENGTH_SHORT).show(); return; }
            statusView.setText("正在測試 · " + LlmConfigStore.label(this));
            LlmClient.test(this, new LlmClient.Callback() {
                @Override public void onSuccess(String reply) { runOnUiThread(() -> statusView.setText("連線成功 · " + reply)); }
                @Override public void onError(String message) { runOnUiThread(() -> statusView.setText("連線失敗 · " + message)); }
            });
        } catch (Exception error) {
            statusView.setText("儲存失敗 · " + error.getClass().getSimpleName());
        }
    }

    private TextView label(String value) { return text(value, 14, true, 0xff16231b); }
    private TextView text(String value, float size, boolean bold, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return view; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top(int margin) { LinearLayout.LayoutParams p = full(); p.topMargin = dp(margin); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
