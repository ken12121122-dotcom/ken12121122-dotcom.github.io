package com.amin.pocketgba;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class AgentControlDashboardActivityTest {
    @Test public void rendersReadOnlyAgentEvidenceDashboardInsideSingleCanvas() throws Exception {
        try (ActivityScenario<WikiGraphActivity> scenario = ActivityScenario.launch(WikiGraphActivity.class)) {
            AtomicReference<WebView> webView = new AtomicReference<>();
            scenario.onActivity(activity -> webView.set(findWebView(
                    activity.findViewById(android.R.id.content))));
            assertNotNull(webView.get());

            String result = evaluateWhenReady(scenario, webView.get(),
                    "(()=>{document.getElementById('agentBtn').click();"
                            + "const manager=document.getElementById('agentManager'),rect=manager.getBoundingClientRect(),state=window.AminGraphSmoke.state();"
                            + "return JSON.stringify({canvas:document.querySelectorAll('canvas#graph').length,"
                            + "open:manager.classList.contains('open'),safeBottom:rect.bottom<=innerHeight-48,"
                            + "topClose:!!document.getElementById('agentCloseTop'),"
                            + "roster:state.agentNodeCount,panel:state.agentPanelCount,"
                            + "attentionSurface:!!document.getElementById('ownerAttentionList'),"
                            + "readOnly:state.agentReadOnly,unsafe:state.agentUnsafeCount,"
                            + "controlsHidden:getComputedStyle(document.querySelector('.nav')).visibility==='hidden',"
                            + "layout:state.layout});})()");

            assertTrue(result, result.contains("\"canvas\":1"));
            assertTrue(result, result.contains("\"open\":true"));
            assertTrue(result, result.contains("\"safeBottom\":true"));
            assertTrue(result, result.contains("\"topClose\":true"));
            assertTrue(result, result.contains("\"roster\":3"));
            assertTrue(result, result.contains("\"panel\":3"));
            assertTrue(result, result.contains("\"attentionSurface\":true"));
            assertTrue(result, result.contains("\"readOnly\":true"));
            assertTrue(result, result.contains("\"unsafe\":0"));
            assertTrue(result, result.contains("\"controlsHidden\":true"));
            assertTrue(result, result.contains("single-force-canvas"));
        }
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
            if (latest.contains("single-force-canvas") && latest.contains("\"panel\":3")) return latest;
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
