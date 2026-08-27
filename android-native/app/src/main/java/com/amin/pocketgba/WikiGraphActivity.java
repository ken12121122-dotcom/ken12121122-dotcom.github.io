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

/** Unified graph viewer. Registry data and runtime overlays stay independently writable. */
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
                injectRuntimeOverlay();
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

    /**
     * Runtime flow is intentionally injected as a separate display layer.
     * If this layer fails, the registry graph renderer remains fully usable.
     */
    private void injectRuntimeOverlay() {
        if (webView == null) return;
        String js = "(()=>{"
                + "if(window.__aminRuntimeOverlayInstalled)return;window.__aminRuntimeOverlayInstalled=true;"
                + "let enabled=true;"
                + "const tools=document.querySelector('.tools');"
                + "const toggle=document.createElement('button');toggle.id='runtimeToggle';toggle.textContent='LIVE ON';"
                + "if(tools)tools.appendChild(toggle);"
                + "const panel=document.createElement('div');panel.id='runtimeOverlay';"
                + "Object.assign(panel.style,{position:'fixed',left:'9px',right:'9px',bottom:'42px',zIndex:'11',pointerEvents:'none',display:'none',gap:'6px',alignItems:'stretch',overflowX:'auto',padding:'8px',border:'1px solid varCss('--border'),borderRadius:'14px',background:'color-mix(in srgb, '+varCss('--panel')+' 92%, transparent)',boxShadow:'0 10px 30px rgba(0,0,0,.12)'});"
                + "document.body.appendChild(panel);"
                + "toggle.onclick=()=>{enabled=!enabled;toggle.textContent=enabled?'LIVE ON':'LIVE OFF';if(!enabled)panel.style.display='none';else refresh();};"
                + "function varCss(n){return getComputedStyle(document.documentElement).getPropertyValue(n).trim()||'#fff'}"
                + "function color(s){s=String(s||'');if(s==='failed'||s==='rejected'||s==='blocked')return varCss('--blocked');if(s==='waiting'||s==='approval_waiting')return varCss('--waiting');if(s==='pending'||s==='generated')return varCss('--pending');return varCss('--active')}"
                + "function escapeText(v){return String(v==null?'':v).replace(/[&<>\"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c]))}"
                + "function refresh(){if(!enabled)return;try{const raw=window.AminWiki?.getRuntimeFlowTraceJson?.();const flows=raw?JSON.parse(raw):[];panel.innerHTML='';if(!Array.isArray(flows)||!flows.length){panel.style.display='none';return;}panel.style.display='flex';const flow=flows[flows.length-1]||{};const head=document.createElement('div');Object.assign(head.style,{minWidth:'110px',padding:'7px 9px',borderRadius:'10px',background:varCss('--panel'),border:'1px solid '+varCss('--border'),fontSize:'11px',fontWeight:'800'});head.innerHTML='<div>LIVE FLOW</div><div style=\"font-weight:600;color:'+varCss('--muted')+'\">'+escapeText(flow.title||flow.type||'Runtime')+'</div>';panel.appendChild(head);const steps=Array.isArray(flow.steps)?flow.steps:[];steps.forEach((s,i)=>{const wrap=document.createElement('div');Object.assign(wrap.style,{display:'flex',alignItems:'center',gap:'6px',minWidth:'fit-content'});const arrow=document.createElement('span');arrow.textContent='→';arrow.style.color=varCss('--muted');wrap.appendChild(arrow);const card=document.createElement('div');Object.assign(card.style,{minWidth:'96px',padding:'7px 8px',borderRadius:'10px',background:varCss('--panel'),border:'1px solid '+varCss('--border'),fontSize:'10px'});card.innerHTML='<div style=\"display:flex;gap:6px;align-items:center\"><span style=\"width:9px;height:9px;border-radius:50%;display:inline-block;background:'+color(s.status)+'\"></span><strong>'+escapeText(s.title||s.step_id||('Step '+(i+1)))+'</strong></div><div style=\"margin-top:3px;color:'+varCss('--muted')+'\">'+escapeText(s.status||'pending')+'</div>';wrap.appendChild(card);panel.appendChild(wrap);});if(flow.final_node_id){const wrap=document.createElement('div');Object.assign(wrap.style,{display:'flex',alignItems:'center',gap:'6px',minWidth:'fit-content'});wrap.innerHTML='<span style=\"color:'+varCss('--muted')+'\">→</span><div style=\"padding:7px 8px;border-radius:10px;background:'+varCss('--panel')+';border:1px solid '+varCss('--active')+';font-size:10px\"><strong>NODE</strong><div style=\"color:'+varCss('--muted')+'\">'+escapeText(flow.final_node_id)+'</div></div>';panel.appendChild(wrap)}}catch(e){panel.style.display='none'}}"
                + "window.__aminRuntimeOverlayRefresh=refresh;refresh();setInterval(refresh,700);"
                + "})();";
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
