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
        if (requestCode == REQ_FORM) {
            if (resultCode != RESULT_OK || data == null) { finish(); return; }
            pending = parse(data.getStringExtra(SchemaFormActivity.EXTRA_RESULT_JSON));
            ensureConnected();
            return;
        }
        if (requestCode == REQ_SHEETS) {
            if (resultCode == RESULT_OK && pending != null) {
                appendPending();
            } else if (pending != null) {
                showAuthorizationInterrupted();
            } else {
                finish();
            }
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

    private void showAuthorizationInterrupted() {
        new AlertDialog.Builder(this)
                .setTitle("尚未完成 Google Sheets 授權")
                .setMessage("剛才填寫的收支資料仍保留。你可以重新授權後繼續寫入，或取消這筆資料。")
                .setPositiveButton("重新授權", (d, w) -> ensureConnected())
                .setNegativeButton("取消這筆", (d, w) -> { pending = null; finish(); })
                .setCancelable(false)
                .show();
    }

    private void appendPending() {
        JSONObject record = pending;
        if (record == null) return;
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
                runOnUiThread(() -> {
                    if (result.success) {
                        pending = null;
                        new AlertDialog.Builder(this)
                                .setTitle("已儲存")
                                .setMessage("資料已寫入 Google Sheets / Transactions")
                                .setPositiveButton("查看收支明細", (d, w) -> {
                                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("amin-data://transactions")).setPackage(getPackageName()));
                                    finish();
                                })
                                .setNegativeButton("回財務首頁", (d, w) -> finish())
                                .show();
                    } else {
                        showWriteFailure(result.message);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> showWriteFailure(error.getMessage()));
            }
        }).start();
    }

    private void showWriteFailure(String message) {
        new AlertDialog.Builder(this)
                .setTitle("寫入失敗")
                .setMessage((message == null || message.trim().isEmpty()) ? "Google Sheets 寫入失敗" : message)
                .setPositiveButton("重新取得授權", (d, w) -> {
                    new GoogleSheetsConnectionStore(this).setSessionToken(FinanceStorageConfig.SOURCE_ID, "");
                    ensureConnected();
                })
                .setNegativeButton("保留資料稍後再試", null)
                .show();
    }

    private static JSONObject parse(String raw) {
        try { return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw); }
        catch (Exception e) { return new JSONObject(); }
    }
}
