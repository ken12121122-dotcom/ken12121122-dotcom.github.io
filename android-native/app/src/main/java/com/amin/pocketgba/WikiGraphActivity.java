package com.amin.pocketgba;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/** Unified graph viewer. Registry and runtime remain authoritative; the HTML asset owns graph presentation. */
public final class WikiGraphActivity extends Activity {
    private static final String GRAPH_SETTINGS_PREFS = "amin_graph_settings";
    private static final String GRAPH_SETTINGS_KEY = "semantic_zoom_ui";

    private WebView webView;
    private NodeMetadataStore nodeMetadataStore;
    private String focusNode = "";

    private final BroadcastReceiver graphChangedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            reloadUnifiedGraph();
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        focusNode = getIntent().getStringExtra("focus_node");
        if (focusNode == null) focusNode = "";
        nodeMetadataStore = new NodeMetadataStore(this);

        AminTheme.Palette palette = AminTheme.palette(this);
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        webView = new WebView(this);
        webView.setBackgroundColor(palette.background);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyWebTheme();
                reloadUnifiedGraph();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WikiBridge(), "AminWiki");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/amin-wiki-graph/index.html");

        IntentFilter filter = new IntentFilter(UnifiedGraphProvider.ACTION_CHANGED);
        ContextCompat.registerReceiver(
                this,
                graphChangedReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override protected void onResume() {
        super.onResume();
        reloadUnifiedGraph();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(graphChangedReceiver); } catch (RuntimeException ignored) { }
        if (webView != null) {
            webView.removeJavascriptInterface("AminWiki");
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("window.AminGraphBack?AminGraphBack():false", value -> {
                if (!"true".equals(value)) WikiGraphActivity.super.onBackPressed();
            });
            return;
        }
        super.onBackPressed();
    }

    private void reloadUnifiedGraph() {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript("window.AminReloadUnifiedGraph?AminReloadUnifiedGraph():false", null));
    }

    private void applyWebTheme() {
        if (webView == null) return;
        AminTheme.Palette palette = AminTheme.palette(this);
        String js = "(()=>{const r=document.documentElement.style;"
                + "r.setProperty('--bg','" + hex(palette.background) + "');"
                + "r.setProperty('--panel','" + hex(palette.surface) + "');"
                + "r.setProperty('--text','" + hex(palette.text) + "');"
                + "r.setProperty('--muted','" + hex(palette.muted) + "');"
                + "r.setProperty('--border','" + hex(palette.border) + "');})();";
        webView.evaluateJavascript(js, null);
    }

    private String hex(int color) { return String.format("#%06X", 0xFFFFFF & color); }

    private String graphSettingsJson() {
        return getSharedPreferences(GRAPH_SETTINGS_PREFS, MODE_PRIVATE).getString(GRAPH_SETTINGS_KEY, "");
    }

    private void saveGraphSettingsJson(String json) {
        try {
            new JSONObject(json);
            getSharedPreferences(GRAPH_SETTINGS_PREFS, MODE_PRIVATE).edit().putString(GRAPH_SETTINGS_KEY, json).apply();
        } catch (Exception ignored) { }
    }

    private final class WikiBridge {
        @JavascriptInterface public void close() { runOnUiThread(WikiGraphActivity.this::finish); }
        @JavascriptInterface public String getThemeName() { return AminTheme.current(WikiGraphActivity.this); }
        @JavascriptInterface public String getFocusNode() { return focusNode; }
        @JavascriptInterface public String getGraphSettingsJson() { return graphSettingsJson(); }
        @JavascriptInterface public void saveGraphSettingsJson(String json) { WikiGraphActivity.this.saveGraphSettingsJson(json); }
        @JavascriptInterface public String getUnifiedGraphJson() {
            return UnifiedGraphProvider.graphJson(WikiGraphActivity.this, nodeMetadataStore);
        }
        @JavascriptInterface public String getRuntimeEdgeTraceJson() {
            return GraphRuntimeEdgeTrace.snapshotJson().toString();
        }
        @JavascriptInterface public String getRuntimeFlowTraceJson() {
            return GraphRuntimeFlowTrace.snapshotJson().toString();
        }
        @JavascriptInterface public boolean addUnifiedEdge(String from, String to, String relation) {
            String source = from == null ? "" : from.trim();
            String target = to == null ? "" : to.trim();
            if (source.isEmpty() || target.isEmpty() || source.equals(target)) return false;
            String type = relation == null ? "" : relation.trim();
            if (type.isEmpty()) type = "related_to";
            try {
                JSONObject edge = new JSONObject()
                        .put("edge_id", "edge:" + UUID.randomUUID().toString().substring(0, 8))
                        .put("from", source)
                        .put("to", target)
                        .put("relation", type)
                        .put("status", "active")
                        .put("gate", new JSONObject().put("enabled", true))
                        .put("command_chain", new JSONArray());
                nodeMetadataStore.addOrReplaceEdge(edge);
                return true;
            } catch (Exception error) { return false; }
        }
        @JavascriptInterface public boolean saveUnifiedEdgeJson(String edgeJson) {
            try {
                JSONObject edge = new JSONObject(edgeJson == null ? "{}" : edgeJson);
                String source = edge.optString("from", "").trim();
                String target = edge.optString("to", "").trim();
                if (source.isEmpty() || target.isEmpty() || source.equals(target)) return false;
                String edgeId = edge.optString("edge_id", edge.optString("edgeId", "")).trim();
                if (edgeId.isEmpty()) edgeId = "edge:" + UUID.randomUUID().toString().substring(0, 8);
                String relation = edge.optString("relation", edge.optString("type", "related_to")).trim();
                if (relation.isEmpty()) relation = "related_to";
                edge.put("edge_id", edgeId);
                edge.put("from", source);
                edge.put("to", target);
                edge.put("relation", relation);
                edge.put("status", edge.optString("status", "active"));
                if (edge.optJSONObject("gate") == null) edge.put("gate", new JSONObject().put("enabled", true));
                if (edge.optJSONArray("command_chain") == null) edge.put("command_chain", new JSONArray());
                nodeMetadataStore.addOrReplaceEdge(edge);
                return true;
            } catch (Exception error) { return false; }
        }
        @JavascriptInterface public void removeUnifiedEdge(String edgeId) {
            nodeMetadataStore.removeEdge(edgeId == null ? "" : edgeId.trim());
        }
        @JavascriptInterface public void openNodeInspector(String nodeId) {
            runOnUiThread(() -> {
                Intent intent = new Intent(WikiGraphActivity.this, NodeInspectorActivity.class);
                intent.putExtra(NodeInspectorActivity.EXTRA_NODE_ID, nodeId);
                startActivity(intent);
            });
        }
        @JavascriptInterface public void toast(String text) {
            runOnUiThread(() -> Toast.makeText(WikiGraphActivity.this, text == null ? "" : text, Toast.LENGTH_SHORT).show());
        }
    }
}
