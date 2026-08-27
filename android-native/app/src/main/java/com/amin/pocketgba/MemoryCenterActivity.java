package com.amin.pocketgba;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MemoryCenterActivity extends Activity {
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff07110d);
        getWindow().setNavigationBarColor(0xff07110d);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff07110d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("🧠 Pocket 記憶");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28f);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("本機記憶中心 · Memory v1 UI");
        subtitle.setTextColor(0xff9fb6aa);
        subtitle.setTextSize(14f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(6);
        subtitleParams.bottomMargin = dp(22);
        root.addView(subtitle, subtitleParams);

        addCard(root, "👤 關於你", "個人資料、偏好與穩定事實", "尚未建立長期記憶");
        addCard(root, "📁 專案", "正在進行與曾經討論的專案", "尚未建立專案記憶");
        addCard(root, "👥 人物", "人物與關係記憶", "尚未建立人物記憶");
        addCard(root, "📅 經歷與事件", "對話中形成的事件記憶", "尚未建立事件記憶");
        addCard(root, "🕘 最近形成", "最近由 Memory Writer 形成的候選記憶", "Memory Writer 尚未接線");

        TextView note = new TextView(this);
        note.setText("目前此頁先作為正式功能地圖入口與記憶管理 UI 骨架。下一階段接 Room / SQLite、Memory Reader、Memory Writer 與來源追溯，不影響既有 Voice Orb、Node Registry 與手機控制指令。");
        note.setTextColor(0xff9fb6aa);
        note.setTextSize(13f);
        note.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        noteParams.topMargin = dp(18);
        root.addView(note, noteParams);

        setContentView(scroll);
    }

    private void addCard(LinearLayout parent, String titleText, String description, String status) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundColor(0xff173c29);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18f);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextColor(0xffb9c9c0);
        desc.setTextSize(14f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dp(5);
        card.addView(desc, descParams);

        TextView statusView = new TextView(this);
        statusView.setText(status);
        statusView.setTextColor(0xff75d9a5);
        statusView.setTextSize(12f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(10);
        card.addView(statusView, statusParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(12);
        parent.addView(card, cardParams);
    }
}
