package com.amin.pocketgba;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.json.JSONObject;

public final class FinanceTransactionActivity extends Activity {
    private static final int REQ_FORM = 6101;
    private static final int REQ_SHEETS = 6102;
    private JSONObject pending;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FinanceStorageConfig.ensureSourceMapping(this);
        openForm();
    }

    private void openForm() {
        Intent i = new Intent(this, SchemaFormActivity.class);
        i.putExtra(SchemaFormActivity.EXTRA_CONTRACT_ID, "transaction_v1");
        startActivityForResult(i, REQ_FORM);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            if (requestCode == REQ_FORM) finish();
            return;
        }
        if (requestCode == REQ_FORM && data != null) {
            pending = parse(data.getStringExtra(SchemaFormActivity.EXTRA_RESULT_JSON));
            ensureConnected();
        } else if (requestCode == REQ_SHEETS && pending != null) {
            appendPending();
        }
    }

    private void ensureConnected() {
        GoogleSheetsConnectionStore store = new GoogleSheetsConnectionStore(this);
        if (!store.isReady(FinanceStorageConfig.SOURCE_ID)) {
            Intent i = new Intent(this, GoogleSheetsConnectionActivity.class);
            i.putExtra(GoogleSheetsConnectionActivity.EXTRA_SOURCE_ID, FinanceStorageConfig.SOURCE_ID);
            startActivityForResult(i, REQ_SHEETS);
            return;
        }
        appendPending();
    }

    private void appendPending() {
        JSONObject record = pending;
        if (record == null) return;
        pending = null;
        try {
            if (!record.has("transaction_id")) record.put("transaction_id", "tx_" + System.currentTimeMillis());
            if (!record.has("created_at")) record.put("created_at", java.time.OffsetDateTime.now().toString());
        } catch (Exception ignored) {}

        DataContract contract = new DataContract(new ContractRegistry(this).get("transaction_v1"));
        StorageAdapter adapter = new GoogleSheetsAdapter(
                FinanceStorageConfig.SOURCE_ID,
                FinanceStorageConfig.TRANSACTIONS,
                new GoogleSheetsApiTransport(this)
        );
        Toast.makeText(this, "正在寫入 Transactions…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                StorageAdapter.Result result = adapter.append(contract, record);
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(result.success ? "已儲存" : "儲存失敗")
                        .setMessage(result.success ? "資料已寫入 Google Sheets / Transactions" : result.message)
                        .setPositiveButton("查看收支明細", (d, w) -> {
                            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("amin-data://transactions")).setPackage(getPackageName()));
                            finish();
                        })
                        .setNegativeButton("回財務首頁", (d, w) -> finish())
                        .show());
            } catch (Exception error) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("寫入失敗")
                        .setMessage(error.getMessage())
                        .setPositiveButton("重新連線", (d, w) -> ensureConnected())
                        .setNegativeButton("取消", (d, w) -> finish())
                        .show());
            }
        }).start();
    }

    private static JSONObject parse(String raw) {
        try { return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw); }
        catch (Exception e) { return new JSONObject(); }
    }
}
