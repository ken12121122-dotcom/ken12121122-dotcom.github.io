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
public final class WikiGraphActivityTest {
    @Test public void rendersReadOnlyGitHubWorkInsideSingleCanvas() throws Exception {
        try (ActivityScenario<WikiGraphActivity> scenario = ActivityScenario.launch(WikiGraphActivity.class)) {
            AtomicReference<WebView> webView = new AtomicReference<>();
            scenario.onActivity(activity -> webView.set(findWebView(activity.findViewById(android.R.id.content))));
            assertNotNull(webView.get());

            String result = evaluateWhenReady(scenario, webView.get(),
                    "(()=>{document.getElementById('workBtn').click();return JSON.stringify({"
                            + "canvas:document.querySelectorAll('canvas#graph').length,"
                            + "workButton:!!document.getElementById('workBtn'),"
                            + "workPanel:document.getElementById('workManager').classList.contains('open'),"
                            + "refresh:!!document.getElementById('workRefresh'),"
                            + "rawLogs:window.AminGraphSmoke.state().rawLogsEnabled,"
                            + "layout:window.AminGraphSmoke.state().layout});})()");

            assertTrue(result, result.contains("\\\"canvas\\\":1"));
            assertTrue(result, result.contains("\\\"workButton\\\":true"));
            assertTrue(result, result.contains("\\\"workPanel\\\":true"));
            assertTrue(result, result.contains("\\\"refresh\\\":true"));
            assertTrue(result, result.contains("\\\"rawLogs\\\":false"));
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
