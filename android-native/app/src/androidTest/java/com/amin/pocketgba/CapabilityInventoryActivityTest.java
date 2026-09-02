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
public final class CapabilityInventoryActivityTest {
    @Test public void rendersGovernedCapabilityInventoryInsideSingleCanvas() throws Exception {
        try (ActivityScenario<WikiGraphActivity> scenario = ActivityScenario.launch(WikiGraphActivity.class)) {
            AtomicReference<WebView> webView = new AtomicReference<>();
            scenario.onActivity(activity -> webView.set(findWebView(
                    activity.findViewById(android.R.id.content))));
            assertNotNull(webView.get());

            String result = evaluateWhenReady(scenario, webView.get(),
                    "(()=>{document.getElementById('capabilityBtn')?.click();"
                            + "const s=window.AminGraphSmoke?AminGraphSmoke.state():{};"
                            + "s.capabilityManagerOpen=document.getElementById('capabilityManager')?.classList.contains('open');"
                            + "s.readOnlyBoundary=document.getElementById('capabilityManager')?.textContent.includes('不可執行');"
                            + "s.commandFilter=!![...document.getElementById('capabilityTypeFilter').options].find(o=>o.value==='COMMAND');"
                            + "s.certificationFilter=!!document.getElementById('capabilityCertificationFilter');"
                            + "document.querySelector('#capabilityList [data-capability-node]')?.click();"
                            + "s.capabilityDetail=document.getElementById('panelBody')?.textContent.includes('來源紀錄：');"
                            + "s.structuredDetail=!document.getElementById('panelBody')?.textContent.includes('[object Object]');"
                            + "s.connectHidden=document.getElementById('source')?.classList.contains('hidden');"
                            + "return JSON.stringify(s)})()");

            assertTrue(result, result.contains("\\\"layout\\\":\\\"single-force-canvas\\\""));
            assertTrue(result, result.matches(".*\\\\\"capabilityNodeCount\\\\\":[1-9][0-9]*.*"));
            assertTrue(result, result.matches(".*\\\\\"capabilityPanelCount\\\\\":[1-9][0-9]*.*"));
            assertTrue(result, result.contains("\\\"unsafeCapabilityCount\\\":0"));
            assertTrue(result, result.contains("\\\"capabilityManagerOpen\\\":true"));
            assertTrue(result, result.contains("\\\"readOnlyBoundary\\\":true"));
            assertTrue(result, result.contains("\\\"commandFilter\\\":true"));
            assertTrue(result, result.contains("\\\"certificationFilter\\\":true"));
            assertTrue(result, result.contains("\\\"capabilityDetail\\\":true"));
            assertTrue(result, result.contains("\\\"structuredDetail\\\":true"));
            assertTrue(result, result.contains("\\\"connectHidden\\\":true"));
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
            if (latest.contains("capabilityNodeCount") && !latest.contains("capabilityNodeCount\\\":0")) {
                return latest;
            }
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
