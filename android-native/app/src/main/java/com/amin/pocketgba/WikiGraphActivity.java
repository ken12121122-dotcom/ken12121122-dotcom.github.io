package com.amin.pocketgba;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public final class WikiGraphActivity extends Activity {
    private WebView webView;
    private PromptStore promptStore;
    private String graphMode="knowledge";
    private String focusNode="";

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);graphMode=getIntent().getStringExtra("graph_mode");if(graphMode==null)graphMode="knowledge";focusNode=getIntent().getStringExtra("focus_node");if(focusNode==null)focusNode="";promptStore=new PromptStore(this);
        AminTheme.Palette p=AminTheme.palette(this);getWindow().setStatusBarColor(p.background);getWindow().setNavigationBarColor(p.background);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        webView=new WebView(this);webView.setBackgroundColor(p.background);WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setDatabaseEnabled(false);s.setAllowFileAccess(true);s.setAllowContentAccess(false);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(true);s.setCacheMode(WebSettings.LOAD_NO_CACHE);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);s.setSupportZoom(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){super.onPageFinished(view,url);applyWebTheme();}});webView.setWebChromeClient(new WebChromeClient());webView.addJavascriptInterface(new WikiBridge(),"AminWiki");setContentView(webView);webView.loadUrl("file:///android_asset/amin-wiki-graph/index.html");
    }

    private void applyWebTheme(){
        if(webView==null)return;AminTheme.Palette p=AminTheme.palette(this);
        String js="(()=>{const r=document.documentElement.style;"+
                "r.setProperty('--bg','"+hex(p.background)+"');"+
                "r.setProperty('--panel','"+hex(p.surface)+"');"+
                "r.setProperty('--text','"+hex(p.text)+"');"+
                "r.setProperty('--muted','"+hex(p.muted)+"');"+
                "r.setProperty('--border','"+hex(p.border)+"');"+
                "r.setProperty('--knowledge','"+hex(p.primary)+"');document.body.style.background='"+hex(p.background)+"';})();";
        webView.evaluateJavascript(js,null);
    }
    private String hex(int color){return String.format("#%06X",0xFFFFFF & color);}

    @Override public void onBackPressed(){
        if(webView!=null){
            webView.evaluateJavascript("window.AminGraphBack?AminGraphBack():false",v->{if(!"true".equals(v))WikiGraphActivity.super.onBackPressed();});
            return;
        }
        super.onBackPressed();
    }

    private void openExternal(String rawUrl){
        try{
            Uri uri=Uri.parse(rawUrl==null?"":rawUrl.trim());
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().trim().isEmpty()){
                Toast.makeText(this,"僅允許開啟 HTTPS 外部資源",Toast.LENGTH_SHORT).show();return;
            }
            startActivity(new Intent(Intent.ACTION_VIEW,uri));
        }catch(ActivityNotFoundException e){Toast.makeText(this,"找不到可開啟此資源的 App",Toast.LENGTH_SHORT).show();}
        catch(RuntimeException e){Toast.makeText(this,"外部資源連結無效",Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onDestroy(){if(webView!=null){webView.removeJavascriptInterface("AminWiki");webView.stopLoading();webView.loadUrl("about:blank");webView.destroy();webView=null;}if(promptStore!=null)promptStore.close();super.onDestroy();}

    private final class WikiBridge {
        @JavascriptInterface public void close(){runOnUiThread(WikiGraphActivity.this::finish);}
        @JavascriptInterface public String getPromptGraphJson(){return promptStore.graphJson();}
        @JavascriptInterface public String getGraphMode(){return graphMode;}
        @JavascriptInterface public String getFocusNode(){return focusNode;}
        @JavascriptInterface public String getThemeName(){return AminTheme.current(WikiGraphActivity.this);}
        @JavascriptInterface public void openExternalResource(String url){runOnUiThread(()->openExternal(url));}
        @JavascriptInterface public void openPrompt(String rawId){try{long id=Long.parseLong(rawId);Intent i=new Intent(WikiGraphActivity.this,PromptEditorActivity.class);i.putExtra("prompt_id",id);runOnUiThread(()->startActivity(i));}catch(NumberFormatException ignored){}}
    }
}
