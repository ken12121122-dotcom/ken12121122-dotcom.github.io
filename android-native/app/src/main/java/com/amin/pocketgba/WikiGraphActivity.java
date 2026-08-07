package com.amin.pocketgba;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class WikiGraphActivity extends Activity {
    private WebView webView;
    private PromptStore promptStore;
    private String graphMode="knowledge";
    private String focusNode="";

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);graphMode=getIntent().getStringExtra("graph_mode");if(graphMode==null)graphMode="knowledge";focusNode=getIntent().getStringExtra("focus_node");if(focusNode==null)focusNode="";promptStore=new PromptStore(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.BLACK);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        webView=new WebView(this);webView.setBackgroundColor(Color.TRANSPARENT);WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setDatabaseEnabled(false);s.setAllowFileAccess(true);s.setAllowContentAccess(false);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(true);s.setCacheMode(WebSettings.LOAD_NO_CACHE);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);s.setSupportZoom(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebViewClient(new WebViewClient());webView.setWebChromeClient(new WebChromeClient());webView.addJavascriptInterface(new WikiBridge(),"AminWiki");setContentView(webView);webView.loadUrl("file:///android_asset/amin-wiki-graph/index.html");
    }

    @Override public void onBackPressed(){if(webView!=null){webView.evaluateJavascript("document.getElementById('article').classList.contains('open')?(document.getElementById('article').classList.remove('open'),true):false",v->{if(!"true".equals(v))WikiGraphActivity.super.onBackPressed();});return;}super.onBackPressed();}
    @Override protected void onDestroy(){if(webView!=null){webView.removeJavascriptInterface("AminWiki");webView.stopLoading();webView.loadUrl("about:blank");webView.destroy();webView=null;}if(promptStore!=null)promptStore.close();super.onDestroy();}

    private final class WikiBridge {
        @JavascriptInterface public void close(){runOnUiThread(WikiGraphActivity.this::finish);}
        @JavascriptInterface public String getPromptGraphJson(){return promptStore.graphJson();}
        @JavascriptInterface public String getGraphMode(){return graphMode;}
        @JavascriptInterface public String getFocusNode(){return focusNode;}
        @JavascriptInterface public void openPrompt(String rawId){try{long id=Long.parseLong(rawId);android.content.Intent i=new android.content.Intent(WikiGraphActivity.this,PromptEditorActivity.class);i.putExtra("prompt_id",id);runOnUiThread(()->startActivity(i));}catch(NumberFormatException ignored){}}
    }
}
