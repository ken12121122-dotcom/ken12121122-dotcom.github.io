package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class FinanceActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FinanceStorageConfig.ensureSourceMapping(this);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(text("財務", 30, true));
        root.addView(text("Google Sheets：Amin Pocket｜個人財務資料庫", 13, false));

        Button add = action("＋ 新增收支", "輸入日期、收支類型、分類、品項、金額、帳戶、商家與備註");
        add.setOnClickListener(v -> startActivity(new Intent(this, FinanceTransactionActivity.class)));
        root.addView(add, params());

        Button transactions = action("收支明細", "對應 Google Sheets / Transactions");
        transactions.setOnClickListener(v -> open("amin-data://transactions"));
        root.addView(transactions, params());

        Button categories = action("分類", "對應 Google Sheets / Categories");
        categories.setOnClickListener(v -> open("amin-finance://categories"));
        root.addView(categories, params());

        Button accounts = action("帳戶", "對應 Google Sheets / Accounts");
        accounts.setOnClickListener(v -> open("amin-finance://accounts"));
        root.addView(accounts, params());

        Button assets = action("資產", "對應 Google Sheets / Assets");
        assets.setOnClickListener(v -> open("amin-finance://assets"));
        root.addView(assets, params());

        Button sheet = action("開啟 Transactions Sheet", "直接查看新增收支實際寫入的 Google Sheets 分頁");
        sheet.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FinanceStorageConfig.tabUrl(FinanceStorageConfig.TRANSACTIONS_SHEET_ID)))));
        root.addView(sheet, params());

        Button graph = action("查看財務節點", "開啟關聯圖並聚焦 finance");
        graph.setOnClickListener(v -> {
            Intent i = new Intent(this, WikiGraphActivity.class);
            i.putExtra("focus_node", "finance");
            startActivity(i);
        });
        root.addView(graph, params());

        setContentView(scroll);
    }

    private void open(String route) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(route)).setPackage(getPackageName()));
    }

    private Button action(String title, String subtitle) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setText(title + "\n" + subtitle);
        b.setTextSize(16);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        return b;
    }

    private TextView text(String value, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private LinearLayout.LayoutParams params() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(76));
        p.topMargin = dp(10);
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
