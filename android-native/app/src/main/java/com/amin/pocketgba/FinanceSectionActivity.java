package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class FinanceSectionActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FinanceStorageConfig.ensureSourceMapping(this);
        String host = getIntent().getData() == null ? "" : getIntent().getData().getHost();
        String title;
        String sheet;
        long sheetId;
        if ("categories".equals(host)) {
            title = "分類";
            sheet = FinanceStorageConfig.CATEGORIES;
            sheetId = FinanceStorageConfig.CATEGORIES_SHEET_ID;
        } else if ("accounts".equals(host)) {
            title = "帳戶";
            sheet = FinanceStorageConfig.ACCOUNTS;
            sheetId = FinanceStorageConfig.ACCOUNTS_SHEET_ID;
        } else if ("assets".equals(host)) {
            title = "資產";
            sheet = FinanceStorageConfig.ASSETS;
            sheetId = FinanceStorageConfig.ASSETS_SHEET_ID;
        } else {
            title = "財務資料";
            sheet = FinanceStorageConfig.TRANSACTIONS;
            sheetId = FinanceStorageConfig.TRANSACTIONS_SHEET_ID;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(28);
        root.addView(h);

        TextView sub = new TextView(this);
        sub.setText("Google Sheets 分頁：" + sheet + "\nSpreadsheet ID：" + FinanceStorageConfig.SPREADSHEET_ID + "\nSheet ID：" + sheetId);
        sub.setTextSize(15);
        sub.setPadding(0, dp(12), 0, dp(18));
        root.addView(sub);

        Button open = new Button(this);
        open.setAllCaps(false);
        open.setText("開啟 Google Sheets / " + sheet);
        open.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FinanceStorageConfig.tabUrl(sheetId)))));
        root.addView(open);

        setContentView(root);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
