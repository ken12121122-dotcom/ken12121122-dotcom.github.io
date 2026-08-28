package com.amin.pocketgba;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Toast;

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

        RegistryCandidateStore store = new RegistryCandidateStore(this);
        NodeMetadataStore nodeStore = new NodeMetadataStore(this);
        showCandidateReview(candidate, store, nodeStore);
    }

    private void showCandidateReview(JSONObject candidate, RegistryCandidateStore store, NodeMetadataStore nodeStore) {
        String type = candidate.optString("entity_type", "");
        if (CapabilityCandidateProtocol.TYPE_CONNECT.equals(type)
                && !GraphContract.isRelationshipTypeAllowed(CapabilityCandidateProtocol.relationshipName(candidate))) {
            showRelationshipClassifier(candidate, store, nodeStore);
            return;
        }

        String title = candidateTitle(candidate);
        StringBuilder message = new StringBuilder(type.toUpperCase()).append("\n").append(title);
        if (CapabilityCandidateProtocol.TYPE_ACTION.equals(type) && candidate.optBoolean("semantic_review_required", false)) {
            message.append("\n\n此 Action 為新語意，已標記需 Semantic Review；本次人工批准只代表允許註冊，不代表未來可跳過語意審核規則。");
        }
        if (CapabilityCandidateProtocol.TYPE_CONNECT.equals(type)) {
            message.append("\n\n關係：").append(CapabilityCandidateProtocol.relationshipName(candidate));
        }
        message.append("\n\n確認註冊到 Registry？");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Registry Approval")
                .setMessage(message.toString())
                .setNegativeButton("拒絕", (d, which) -> {
                    store.reject(candidate);
                    finish();
                })
                .setPositiveButton("批准", (d, which) -> {
                    JSONObject registered = store.approve(candidate, nodeStore);
                    if (registered != null) {
                        UnifiedGraphProvider.notifyChanged(this);
                        finish();
                    } else {
                        Toast.makeText(this, "驗證未通過，未寫入 Registry", Toast.LENGTH_LONG).show();
                        if (!isFinishing()) showCandidateReview(candidate, store, nodeStore);
                    }
                })
                .setOnCancelListener(d -> finish())
                .create();
        dialog.show();
    }

    private void showRelationshipClassifier(JSONObject candidate, RegistryCandidateStore store, NodeMetadataStore nodeStore) {
        String[] relationshipTypes = GraphContract.relationshipTypes();
        new AlertDialog.Builder(this)
                .setTitle("Connect 關係分類")
                .setMessage("related_to 只能是草稿關係。請選擇正式關係後才能註冊。")
                .setItems(relationshipTypes, (dialog, which) -> {
                    if (which < 0 || which >= relationshipTypes.length) return;
                    String relationship = relationshipTypes[which];
                    try {
                        candidate.put("relationship_type", relationship);
                        candidate.put("relationshipType", relationship);
                        candidate.put("relation", relationship);
                        candidate.put("semantic_review_required", false);
                    } catch (Exception ignored) { }
                    showCandidateReview(candidate, store, nodeStore);
                })
                .setNegativeButton("拒絕", (dialog, which) -> {
                    store.reject(candidate);
                    finish();
                })
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private static String candidateTitle(JSONObject candidate) {
        String type = candidate.optString("entity_type", "");
        if (CapabilityCandidateProtocol.TYPE_ACTION.equals(type)) {
            return candidate.optString("owner_node_id", "") + " → " + CapabilityCandidateProtocol.actionName(candidate);
        }
        if (CapabilityCandidateProtocol.TYPE_CONNECT.equals(type)) {
            String source = candidate.optString("source_node_id", candidate.optString("from", ""));
            String target = candidate.optString("target_node_id", candidate.optString("to", ""));
            return source + " → " + target;
        }
        return candidate.optString("title", candidate.optString("command_id", ""));
    }
}
