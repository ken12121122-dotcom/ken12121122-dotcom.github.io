package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class PromptCaptureActivity extends Activity {
    private PromptStore store;
    private String selectedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new PromptStore(this);
        try {
            selectedText = PromptText.requireContent(
                    getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            );
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        buildCategoryPicker();
    }

    private void buildCategoryPicker() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(18));
        root.setBackgroundColor(Color.WHITE);

        TextView title = text("存到提示詞", 21f, true, 0xff16231b);
        root.addView(title, fullWidth());

        TextView preview = text(PromptText.preview(selectedText, 120), 14f, false, 0xff68766e);
        LinearLayout.LayoutParams previewParams = fullWidth();
        previewParams.topMargin = dp(8);
        previewParams.bottomMargin = dp(14);
        root.addView(preview, previewParams);

        List<PromptStore.Category> categories = store.listCategories();
        for (PromptStore.Category category : categories) {
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText(category.name);
            button.setTextSize(16f);
            button.setTextColor(0xff105f39);
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setOnClickListener(view -> save(category));
            LinearLayout.LayoutParams params = fullWidth();
            params.topMargin = dp(4);
            root.addView(button, params);
        }

        Button cancel = new Button(this);
        cancel.setAllCaps(false);
        cancel.setText("取消");
        cancel.setTextColor(0xff68766e);
        cancel.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams cancelParams = fullWidth();
        cancelParams.topMargin = dp(8);
        root.addView(cancel, cancelParams);
        setContentView(root);
    }

    private void save(PromptStore.Category category) {
        try {
            store.savePrompt(category.id, selectedText);
            Toast.makeText(this, "已存到「" + category.name + "」", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, "儲存失敗", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        if (store != null) store.close();
        super.onDestroy();
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.2f);
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
