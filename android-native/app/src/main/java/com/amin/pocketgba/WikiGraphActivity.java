package com.amin.pocketgba;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public final class WikiGraphActivity extends Activity {
    private WebView webView;
    private PromptStore promptStore;
    private ResourceMappingStore resourceMappingStore;
    private AppNavigationCandidateStore navigationCandidateStore;
    private String graphMode="all";
    private String focusNode="";

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        String requestedMode=getIntent().getStringExtra("graph_mode");if(requestedMode!=null&&!requestedMode.trim().isEmpty())graphMode=requestedMode;
        focusNode=getIntent().getStringExtra("focus_node");if(focusNode==null)focusNode="";
        promptStore=new PromptStore(this);resourceMappingStore=new ResourceMappingStore(this);navigationCandidateStore=new AppNavigationCandidateStore(this);
        AminTheme.Palette p=AminTheme.palette(this);getWindow().setStatusBarColor(p.background);getWindow().setNavigationBarColor(p.background);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        webView=new WebView(this);webView.setBackgroundColor(p.background);WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setDatabaseEnabled(false);s.setAllowFileAccess(true);s.setAllowContentAccess(false);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(true);s.setCacheMode(WebSettings.LOAD_NO_CACHE);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);s.setSupportZoom(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){super.onPageFinished(view,url);applyWebTheme();}});webView.setWebChromeClient(new WebChromeClient());webView.addJavascriptInterface(new WikiBridge(),"AminWiki");setContentView(webView);webView.loadUrl("file:///android_asset/amin-wiki-graph/index.html");
    }

    @Override protected void onResume(){super.onResume();if(webView!=null)webView.evaluateJavascript("window.AminReloadLocalMappings?AminReloadLocalMappings():false",null);}

    private void applyWebTheme(){if(webView==null)return;AminTheme.Palette p=AminTheme.palette(this);String js="(()=>{const r=document.documentElement.style;r.setProperty('--bg','"+hex(p.background)+"');r.setProperty('--panel','"+hex(p.surface)+"');r.setProperty('--text','"+hex(p.text)+"');r.setProperty('--muted','"+hex(p.muted)+"');r.setProperty('--border','"+hex(p.border)+"');})();";webView.evaluateJavascript(js,null);}
    private String hex(int color){return String.format("#%06X",0xFFFFFF & color);}

    @Override public void onBackPressed(){if(webView!=null){webView.evaluateJavascript("window.AminGraphBack?AminGraphBack():false",v->{if(!"true".equals(v))WikiGraphActivity.super.onBackPressed();});return;}super.onBackPressed();}

    private void openExternal(String rawUrl){if(!ExternalResourcePolicy.isAllowedHttps(rawUrl)){Toast.makeText(this,"僅允許開啟 HTTPS 外部資源",Toast.LENGTH_SHORT).show();return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(rawUrl.trim())));}catch(ActivityNotFoundException e){Toast.makeText(this,"找不到可開啟此資源的 App",Toast.LENGTH_SHORT).show();}catch(RuntimeException e){Toast.makeText(this,"外部資源連結無效",Toast.LENGTH_SHORT).show();}}
    private void openResourceManager(String nodeId,String nodeTitle){Intent i=new Intent(this,ResourceMappingManagerActivity.class);i.putExtra("node_id",nodeId);i.putExtra("node_title",nodeTitle);startActivity(i);}
    private void openAppRoute(String rawRoute){try{Uri uri=Uri.parse(rawRoute==null?"":rawRoute.trim());String scheme=uri.getScheme();if(scheme==null||!scheme.startsWith("amin-")){Toast.makeText(this,"不允許的 App Route",Toast.LENGTH_SHORT).show();return;}startActivity(new Intent(Intent.ACTION_VIEW,uri).setPackage(getPackageName()));}catch(RuntimeException e){Toast.makeText(this,"App Route 無效",Toast.LENGTH_SHORT).show();}}

    private String appNavigationJson(){JSONArray pages=new JSONArray();try{PackageManager pm=getPackageManager();ActivityInfo[] infos=pm.getPackageInfo(getPackageName(),PackageManager.GET_ACTIVITIES|PackageManager.GET_META_DATA).activities;if(infos!=null)for(ActivityInfo info:infos){Bundle m=info.metaData;if(m==null||!m.getBoolean("amin.graph.visible",false))continue;JSONObject p=new JSONObject();String graphId=m.getString("amin.graph.id",info.name);p.put("id",graphId);p.put("capabilityId",GraphContract.capabilityId(GraphContract.APP_ORIGIN,graphId,m.getString("amin.graph.capability_id","")));p.put("title",m.getString("amin.graph.title",info.name.substring(info.name.lastIndexOf('.')+1)));p.put("parent",m.getString("amin.graph.parent","app-core"));p.put("route",m.getString("amin.graph.route",""));p.put("direction",m.getString("amin.graph.direction","right"));p.put("slot",m.getInt("amin.graph.slot",0));p.put("activity",info.name);p.put("origin","app");p.put("locked",true);pages.put(p);}}catch(Exception ignored){}
        try{JSONObject root=new JSONObject();root.put("format","amin-app-navigation");root.put("version",2);root.put("rootId","app-core");root.put("rootCapabilityId",GraphContract.capabilityId(GraphContract.APP_ORIGIN,"app-core",""));root.put("rootTitle","Amin Pocket");root.put("rootRoute","amin-home://open");root.put("pages",pages);return root.toString();}catch(Exception e){return "{\"pages\":[]}";}}

    private String promptGraphJson(){try{JSONObject root=new JSONObject(promptStore.graphJson());JSONArray links=root.optJSONArray("links");if(links!=null)for(int i=0;i<links.length();i++){JSONObject l=links.optJSONObject(i);if(l==null)continue;String a=l.optString("a","");String b=l.optString("b","");if(a.startsWith("prompt:"))l.put("a",a.substring(7));if(b.startsWith("prompt:"))l.put("b",b.substring(7));}return root.toString();}catch(Exception ignored){return "{\"nodes\":[],\"links\":[]}";}}

    @Override protected void onDestroy(){if(webView!=null){webView.removeJavascriptInterface("AminWiki");webView.stopLoading();webView.loadUrl("about:blank");webView.destroy();webView=null;}if(promptStore!=null)promptStore.close();super.onDestroy();}

    private final class WikiBridge {
        @JavascriptInterface public void close(){runOnUiThread(WikiGraphActivity.this::finish);}
        @JavascriptInterface public String getPromptGraphJson(){return promptGraphJson();}
        @JavascriptInterface public String getGraphMode(){return graphMode;}
        @JavascriptInterface public String getFocusNode(){return focusNode;}
        @JavascriptInterface public String getThemeName(){return AminTheme.current(WikiGraphActivity.this);}
        @JavascriptInterface public String getLocalResourceMappingsJson(){return resourceMappingStore.allJson();}
        @JavascriptInterface public String getAppNavigationJson(){return appNavigationJson();}
        @JavascriptInterface public String getNodeRegistryJson(){return GraphContract.nodeRegistryJson(appNavigationJson());}
        @JavascriptInterface public String getTypedEdgesJson(){return GraphContract.typedEdgesJson(appNavigationJson());}
        @JavascriptInterface public String getAppNavigationCandidateJson(){return navigationCandidateStore.getJson();}
        @JavascriptInterface public void saveAppNavigationCandidateJson(String json){navigationCandidateStore.saveJson(json);}
        @JavascriptInterface public void clearAppNavigationCandidate(){navigationCandidateStore.clear();}
        @JavascriptInterface public void openAppRoute(String route){runOnUiThread(()->WikiGraphActivity.this.openAppRoute(route));}
        @JavascriptInterface public void openResourceManager(String nodeId,String nodeTitle){runOnUiThread(()->WikiGraphActivity.this.openResourceManager(nodeId,nodeTitle));}
        @JavascriptInterface public void openExternalResource(String url){runOnUiThread(()->openExternal(url));}
        @JavascriptInterface public void openPrompt(String rawId){try{long id=Long.parseLong(rawId);Intent i=new Intent(WikiGraphActivity.this,PromptEditorActivity.class);i.putExtra("prompt_id",id);runOnUiThread(()->startActivity(i));}catch(NumberFormatException ignored){}}
    }
}
