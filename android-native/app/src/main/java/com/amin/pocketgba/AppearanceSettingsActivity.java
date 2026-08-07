package com.amin.pocketgba;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class AppearanceSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        AminTheme.applyBeforeCreate(this);
        super.onCreate(b);
        build();
    }

    private void build() {
        AminTheme.Palette p = AminTheme.palette(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(p.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(32));
        scroll.addView(root);

        Button back = button("← 返回", p);
        back.setOnClickListener(v -> finish());
        root.addView(back, full());

        TextView title = new TextView(this);
        title.setText("外觀設定");
        title.setTextSize(28f);
        title.setTextColor(p.text);
        title.setGravity(Gravity.START);
        LinearLayout.LayoutParams tp = full(); tp.topMargin = dp(16); tp.bottomMargin = dp(10);
        root.addView(title, tp);

        TextView note = new TextView(this);
        note.setText("選擇後套用到整個 App、提示詞鍵盤與 Unified Graph。重新進入其他頁面後會使用新主題。");
        note.setTextSize(14f); note.setTextColor(p.muted);
        root.addView(note, full());

        addTheme(root, "清新白 Clean Light", "白底、低陰影、工具感", AminTheme.CLEAN_LIGHT, p);
        addTheme(root, "柔和綠 Soft Green", "Amin 綠色識別，預設推薦", AminTheme.SOFT_GREEN, p);
        addTheme(root, "暗夜黑 Dark", "深色介面，夜間使用", AminTheme.DARK, p);
        addTheme(root, "極簡灰 Minimal", "白底、細線、最少裝飾", AminTheme.MINIMAL, p);

        setContentView(scroll);
    }

    private void addTheme(LinearLayout root, String title, String subtitle, String value, AminTheme.Palette p) {
        Button b = button((AminTheme.current(this).equals(value) ? "✓ " : "") + title + "\n" + subtitle, p);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setOnClickListener(v -> {
            AminTheme.set(this, value);
            recreate();
        });
        LinearLayout.LayoutParams lp = full(); lp.topMargin = dp(10);
        root.addView(b, lp);
    }

    private Button button(String label, AminTheme.Palette p) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(label); b.setTextColor(p.primary);
        b.setMinHeight(dp(56)); b.setPadding(dp(12), dp(8), dp(12), dp(8));
        return b;
    }

    private LinearLayout.LayoutParams full(){ return new LinearLayout.LayoutParams(-1,-2); }
    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
}
