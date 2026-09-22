package com.amin.pocketgba;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.fox.app.data.profile.KnowledgeProfile;
import com.fox.app.data.profile.KnowledgeProfileStore;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class WikiGraphActivityTest {
    @Test public void rendersReadOnlyGitHubWorkInsideSingleCanvas() throws Exception {
        try (ActivityScenario<WikiGraphActivity> scenario = ActivityScenario.launch(WikiGraphActivity.class)) {
            AtomicReference<WebView> webView = new AtomicReference<>();
            scenario.onActivity(activity -> webView.set(findWebView(activity.findViewById(android.R.id.content))));
            assertNotNull(webView.get());

            // Fixed-position "bottom:auto" resolves to pixels in WebView; verify actual bounds instead.
            String result = evaluateWhenReady(scenario, webView.get(),
                    "(()=>{document.getElementById('workBtn').click();"
                            + "const manager=document.getElementById('workManager');"
                            + "const rect=manager.getBoundingClientRect();"
                            + "return JSON.stringify({"
                            + "canvas:document.querySelectorAll('canvas#graph').length,"
                            + "workButton:!!document.getElementById('workBtn'),"
                            + "workPanel:manager.classList.contains('open'),"
                            + "refresh:!!document.getElementById('workRefresh'),"
                            + "topClose:!!document.getElementById('workCloseTop'),"
                            + "safeBottom:rect.bottom<=innerHeight-48,"
                            + "scrollContain:getComputedStyle(manager).overscrollBehaviorY==='contain',"
                            + "graphControlsHidden:getComputedStyle(document.querySelector('.nav')).visibility==='hidden',"
                            + "graphControlsRelocated:document.querySelector('.nav').getBoundingClientRect().bottom<innerHeight/2&&getComputedStyle(document.querySelector('.nav')).flexDirection==='row',"
                            + "rawLogs:window.AminGraphSmoke.state().rawLogsEnabled,"
                            + "layout:window.AminGraphSmoke.state().layout});})()");

            assertTrue(result, result.contains("\\\"canvas\\\":1"));
            assertTrue(result, result.contains("\\\"workButton\\\":true"));
            assertTrue(result, result.contains("\\\"workPanel\\\":true"));
            assertTrue(result, result.contains("\\\"refresh\\\":true"));
            assertTrue(result, result.contains("\\\"topClose\\\":true"));
            assertTrue(result, result.contains("\\\"safeBottom\\\":true"));
            assertTrue(result, result.contains("\\\"scrollContain\\\":true"));
            assertTrue(result, result.contains("\\\"graphControlsHidden\\\":true"));
            assertTrue(result, result.contains("\\\"graphControlsRelocated\\\":true"));
            assertTrue(result, result.contains("\\\"rawLogs\\\":false"));
            assertTrue(result, result.contains("single-force-canvas"));
        }
    }

    @Test public void switchesWikiGraphSaveSlotFromSystemToFoxKnowledgeProfile() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        KnowledgeProfile profile = new KnowledgeProfileStore(context).createProfile("GRAPH_TEST_KB");
        new GraphProfileStore(context).setActiveProfileId(GraphProfileStore.SYSTEM_PROFILE_ID);

        try (ActivityScenario<WikiGraphActivity> scenario = ActivityScenario.launch(WikiGraphActivity.class)) {
            AtomicReference<WebView> webView = new AtomicReference<>();
            scenario.onActivity(activity -> webView.set(findWebView(activity.findViewById(android.R.id.content))));
            assertNotNull(webView.get());

            String initial = evaluateWhenReady(scenario, webView.get(),
                    "(()=>{const s=document.getElementById('profileSelect');"
                            + "const g=JSON.parse(AminWiki.getUnifiedGraphJson());"
                            + "return JSON.stringify({layout:window.AminGraphSmoke?.state().layout,"
                            + "selector:!!s,system:[...s.options].some(o=>o.value==='AMIN_SYSTEM'),"
                            + "fox:[...s.options].some(o=>o.value==='" + profile.getId() + "'),"
                            + "active:s.value,type:g.graphProfile?.type,id:g.graphProfile?.id});})()");
            JSONObject initialJson = decodeJavascriptJsonString(initial);
            assertTrue(initial, initialJson.optBoolean("selector"));
            assertTrue(initial, initialJson.optBoolean("system"));
            assertTrue(initial, initialJson.optBoolean("fox"));
            assertTrue(initial, "AMIN_SYSTEM".equals(initialJson.optString("active")));
            assertTrue(initial, "github".equals(initialJson.optString("type")));

            evaluateOnce(scenario, webView.get(),
                    "(()=>{const s=document.getElementById('profileSelect');"
                            + "s.value='" + profile.getId() + "';"
                            + "s.dispatchEvent(new Event('change',{bubbles:true}));return true;})()");
            Thread.sleep(500L);

            String switched = evaluateOnce(scenario, webView.get(),
                    "(()=>{const g=JSON.parse(AminWiki.getUnifiedGraphJson());"
                            + "return JSON.stringify({selected:document.getElementById('profileSelect').value,"
                            + "type:g.graphProfile?.type,id:g.graphProfile?.id,"
                            + "workHidden:document.getElementById('workBtn').classList.contains('hidden'),"
                            + "connectHidden:document.getElementById('connectsBtn').classList.contains('hidden')});})()");
            JSONObject switchedJson = decodeJavascriptJsonString(switched);
            assertTrue(switched, profile.getId().equals(switchedJson.optString("selected")));
            assertTrue(switched, "fox".equals(switchedJson.optString("type")));
            assertTrue(switched, profile.getId().equals(switchedJson.optString("id")));
            assertTrue(switched, switchedJson.optBoolean("workHidden"));
            assertTrue(switched, switchedJson.optBoolean("connectHidden"));
        } finally {
            new GraphProfileStore(context).setActiveProfileId(GraphProfileStore.SYSTEM_PROFILE_ID);
        }
    }

    @Test public void createsKnowledgeSaveSlotDirectlyInsideWiki() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new GraphProfileStore(context).setActiveProfileId(GraphProfileStore.SYSTEM_PROFILE_ID);

        try (ActivityScenario<WikiGraphActivity> scenario = ActivityScenario.launch(WikiGraphActivity.class)) {
            AtomicReference<WebView> webView = new AtomicReference<>();
            scenario.onActivity(activity -> webView.set(findWebView(activity.findViewById(android.R.id.content))));
            assertNotNull(webView.get());

            String initial = evaluateWhenReady(scenario, webView.get(),
                    "(()=>{document.getElementById('profileManagerBtn').click();"
                            + "return JSON.stringify({layout:window.AminGraphSmoke?.state().layout,"
                            + "manager:document.getElementById('profileManager').classList.contains('open'),"
                            + "openFolder:!!document.getElementById('openKnowledgeFolder'),"
                            + "createEmpty:!!document.getElementById('createEmptyProfile'),"
                            + "attach:!!document.getElementById('attachActiveFolder')});})()");
            JSONObject initialJson = decodeJavascriptJsonString(initial);
            assertTrue(initial, initialJson.optBoolean("manager"));
            assertTrue(initial, initialJson.optBoolean("openFolder"));
            assertTrue(initial, initialJson.optBoolean("createEmpty"));
            assertTrue(initial, initialJson.optBoolean("attach"));

            String created = evaluateOnce(scenario, webView.get(),
                    "(()=>{const id=AminWiki.createEmptyKnowledgeProfile('WIKI_CREATED_KB');"
                            + "window.AminGraphProfilesRefresh();"
                            + "const s=document.getElementById('profileSelect');"
                            + "const state=JSON.parse(AminWiki.getGraphProfilesJson());"
                            + "const item=state.profiles.find(p=>p.id===id);"
                            + "return JSON.stringify({id,selected:s.value,"
                            + "exists:!!item,name:item?.name,type:item?.type,configured:item?.configured});})()");
            JSONObject createdJson = decodeJavascriptJsonString(created);
            String id = createdJson.optString("id");
            assertTrue(created, !id.isEmpty());
            assertTrue(created, id.equals(createdJson.optString("selected")));
            assertTrue(created, createdJson.optBoolean("exists"));
            assertTrue(created, "WIKI_CREATED_KB".equals(createdJson.optString("name")));
            assertTrue(created, "fox".equals(createdJson.optString("type")));
            assertTrue(created, !createdJson.optBoolean("configured"));
        } finally {
            new GraphProfileStore(context).setActiveProfileId(GraphProfileStore.SYSTEM_PROFILE_ID);
        }
    }

    @Test public void confirmsAndProjectsFocusRouteInsideExistingCanvas() throws Exception {
        try (ActivityScenario<WikiGraphActivity> scenario = ActivityScenario.launch(WikiGraphActivity.class)) {
            AtomicReference<WebView> webView = new AtomicReference<>();
            AtomicReference<Boolean> domStorageEnabled = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                WebView found = findWebView(activity.findViewById(android.R.id.content));
                webView.set(found);
                domStorageEnabled.set(found != null && found.getSettings().getDomStorageEnabled());
            });
            assertNotNull(webView.get());
            assertTrue("Focus Path persistence requires WebView DOM storage",
                    domStorageEnabled.get());

            String ready = evaluateWhenReady(scenario, webView.get(),
                    "JSON.stringify({layout:window.AminGraphSmoke?.state().layout})");
            assertTrue(ready, ready.contains("single-force-canvas"));

            String proposed = evaluateOnce(scenario, webView.get(),
                    "(()=>{try{window.AminGraphSmoke.pausePhysics();"
                            + "localStorage.removeItem('amin-focus-chain-pins-v1');"
                            + "const prepared=window.AminGraphSmoke.prepareFirstFocusRoute();"
                            + "const state=window.AminGraphSmoke.state();"
                            + "return JSON.stringify({prepared,"
                            + "review:document.getElementById('routeReview').classList.contains('open'),"
                            + "choiceCount:document.querySelectorAll('[data-approve-route]').length,"
                            + "confirmation:document.getElementById('routeReviewSummary').textContent.includes('確認前 Canvas 不會改變'),"
                            + "activeBeforeApproval:state.focus.active,"
                            + "canvas:document.querySelectorAll('canvas#graph').length,layout:state.layout});"
                            + "}catch(error){return JSON.stringify({prepared:false,error:String(error),stack:String(error?.stack||'')});}})()");
            assertTrue(proposed, proposed.contains("\\\"prepared\\\":true"));
            assertTrue(proposed, proposed.contains("\\\"review\\\":true"));
            assertTrue(proposed, proposed.contains("\\\"choiceCount\\\":"));
            assertTrue(proposed, !proposed.contains("\\\"choiceCount\\\":0"));
            assertTrue(proposed, proposed.contains("\\\"confirmation\\\":true"));
            assertTrue(proposed, proposed.contains("\\\"activeBeforeApproval\\\":false"));
            assertTrue(proposed, proposed.contains("\\\"canvas\\\":1"));

            String approved = evaluateOnce(scenario, webView.get(),
                    "(()=>{const approved=window.AminGraphSmoke.approveFirstFocusRoute();"
                            + "document.getElementById('focusBtn').click();"
                            + "document.querySelector('[data-focus-action=pin-current]')?.click();"
                            + "const manager=document.getElementById('focusManager'),rect=manager.getBoundingClientRect(),state=window.AminGraphSmoke.state();"
                            + "return JSON.stringify({approved,active:state.focus.active,routeCount:state.focus.routeCount,"
                            + "maxOpen:state.focus.maxOpenChains,maxDepth:state.focus.maxDepthPerChain,"
                            + "visibleBounded:state.focus.visibleNodeCount<=state.fullNodeCount,"
                            + "managerOpen:manager.classList.contains('open'),safeBottom:rect.bottom<=innerHeight-48,"
                            + "hudTop:document.getElementById('focusHud').getBoundingClientRect().bottom<innerHeight/2,"
                            + "pinStored:localStorage.getItem('amin-focus-chain-pins-v1').includes('route_id'),"
                            + "canvas:document.querySelectorAll('canvas#graph').length,layout:state.layout});})()");
            assertTrue(approved, approved.contains("\\\"approved\\\":true"));
            assertTrue(approved, approved.contains("\\\"active\\\":true"));
            assertTrue(approved, approved.contains("\\\"routeCount\\\":1"));
            assertTrue(approved, approved.contains("\\\"maxOpen\\\":3"));
            assertTrue(approved, approved.contains("\\\"maxDepth\\\":6"));
            assertTrue(approved, approved.contains("\\\"visibleBounded\\\":true"));
            assertTrue(approved, approved.contains("\\\"managerOpen\\\":true"));
            assertTrue(approved, approved.contains("\\\"safeBottom\\\":true"));
            assertTrue(approved, approved.contains("\\\"hudTop\\\":true"));
            assertTrue(approved, approved.contains("\\\"pinStored\\\":true"));
            assertTrue(approved, approved.contains("\\\"canvas\\\":1"));
        }
    }

    private static JSONObject decodeJavascriptJsonString(String value) throws Exception {
        Object decoded = new JSONTokener(value == null ? "" : value).nextValue();
        if (decoded instanceof String) return new JSONObject((String) decoded);
        if (decoded instanceof JSONObject) return (JSONObject) decoded;
        return new JSONObject();
    }

    private static String evaluateOnce(ActivityScenario<WikiGraphActivity> scenario,
                                       WebView webView, String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");
        scenario.onActivity(activity -> webView.evaluateJavascript(script, value -> {
            result.set(value == null ? "" : value);
            latch.countDown();
        }));
        assertTrue("WebView focus action timed out", latch.await(30, TimeUnit.SECONDS));
        return result.get();
    }

    private static String evaluateWhenReady(ActivityScenario<WikiGraphActivity> scenario,
                                            WebView webView, String script) throws Exception {
        String latest = "";
        for (int attempt = 0; attempt < 30; attempt++) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> result = new AtomicReference<>("");
            scenario.onActivity(activity -> webView.evaluateJavascript(script, value -> {
                result.set(value == null ? "" : value);
                latch.countDown();
            }));
            if (!latch.await(3, TimeUnit.SECONDS)) {
                Thread.sleep(250L);
                continue;
            }
            latest = result.get();
            if (latest.contains("single-force-canvas")) return latest;
            Thread.sleep(250L);
        }
        return latest;
    }

    private static WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
