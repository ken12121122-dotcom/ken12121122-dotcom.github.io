package com.amin.pocketgba;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import org.json.JSONObject;

/** Explicit human approval gate for new registry entities. */
public final class RegistryApprovalActivity extends Activity {
    public static final String EXTRA_CANDIDATE_JSON = "candidate_json";

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        String raw = getIntent().getStringExtra(EXTRA_CANDIDATE_JSON);
        JSONObject candidate;
        try { candidate = raw == null ? null : new JSONObject(raw); }
        catch (Exception error) { candidate = null; }
        if (candidate == null) { finish(); return; }

        String type = candidate.optString("entity_type", "").toUpperCase();
        String title = candidate.optString("title", candidate.optString("command_id", ""));
        String message = type + "\n" + title + "\n\n確認註冊到 Registry？";
        RegistryCandidateStore store = new RegistryCandidateStore(this);
        NodeMetadataStore nodeStore = new NodeMetadataStore(this);
        JSONObject finalCandidate = candidate;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Registry")
                .setMessage(message)
                .setNegativeButton("拒絕", (d, which) -> {
                    store.reject(finalCandidate);
                    finish();
                })
                .setPositiveButton("批准", (d, which) -> {
                    JSONObject registered = store.approve(finalCandidate, nodeStore);
                    if (registered != null) UnifiedGraphProvider.notifyChanged(this);
                    finish();
                })
                .setOnCancelListener(d -> finish())
                .create();
        dialog.setOnDismissListener(d -> { if (!isFinishing()) finish(); });
        dialog.show();
    }
}
