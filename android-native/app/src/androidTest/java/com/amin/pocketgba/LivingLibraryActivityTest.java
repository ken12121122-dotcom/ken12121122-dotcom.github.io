package com.amin.pocketgba;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public final class LivingLibraryActivityTest {
    @Rule public final ActivityScenarioRule<LivingLibraryActivity> rule = new ActivityScenarioRule<>(LivingLibraryActivity.class);
    @Test public void bundledWorkbenchAndNoNewBridge() throws Exception {
        rule.getScenario().onActivity(activity -> {
            try (java.io.InputStream in = activity.getAssets().open("workbench/living-library/index.html")) {
                assertTrue(in.available() > 0);
                assertTrue(activity.getApplicationInfo().loadLabel(activity.getPackageManager()).toString().startsWith("念動圖書館"));
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
    @Test public void framesRedirectsAndSyntheticNavigationCannotOpenNative() {
        assertTrue(LivingLibraryActivity.canOpenNative(LivingLibraryPolicy.HOME, true, true));
        assertFalse(LivingLibraryActivity.canOpenNative(LivingLibraryPolicy.HOME, false, true));
        assertFalse(LivingLibraryActivity.canOpenNative(LivingLibraryPolicy.HOME, true, false));
        assertFalse(LivingLibraryActivity.canOpenNative("https://evil.example", true, true));
        assertFalse(LivingLibraryActivity.canOpenNative("", true, true));
    }
    @Test public void realWebViewOpensAndroidBookAndReturnsWithoutPrivateAuthorization() throws Exception {
        // evaluateJavascript callbacks can be discarded while the initial load is navigating.
        // Wait for an actual committed bundled HOME before testing live DOM interactions.
        long navigationDeadline = System.currentTimeMillis() + 30000;
        boolean committed = false;
        while (System.currentTimeMillis() < navigationDeadline) {
            final boolean[] home = {false};
            rule.getScenario().onActivity(activity ->
                home[0] = LivingLibraryPolicy.isHome(activity.committedUrl));
            if (home[0]) { committed = true; break; }
            Thread.sleep(100);
        }
        assertTrue("Bundled Living Library home never committed", committed);
        long deadline = System.currentTimeMillis() + 30000;
        String result = "";
        while (System.currentTimeMillis() < deadline) {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> value = new java.util.concurrent.atomic.AtomicReference<>("");
            rule.getScenario().onActivity(activity -> activity.workbenchView().evaluateJavascript(
                "(function(){var l=window.LivingLibrary;if(!l)return 'loading';"
                + "if(!['research','startup','knowledge','missions','learning','finance','safety','writing','design','teaching','gaming','system','gpt','android'].every(id=>l.books.some(b=>b.id===id))||new Set(l.books.map(b=>b.id)).size!==l.books.length||typeof AminPackageCanvasFiles!=='undefined'||typeof AminGen2!=='undefined')return 'unsafe';"
                + "l.select('android');l.openBook();var cards=document.querySelectorAll('#drawerFunctions button');"
                + "if(cards.length!==16)return 'missing-cards';var p=document.querySelector('[data-card-id=\\\"public.android.permissions\\\"]');p.click();"
                + "var a=document.querySelector('#sourcePage a');if(!a||a.getAttribute('href')!=='amin-library://permissions')return 'missing-route';"
                + "document.getElementById('readerBack').click();if(!document.getElementById('readerShell').hidden)return 'broken-back';"
                + "document.getElementById('closeDrawer').click();return 'ok';})()",
                answer -> { value.set(answer); latch.countDown(); }));
            assertTrue("WebView callback timed out", latch.await(8, java.util.concurrent.TimeUnit.SECONDS));
            result = value.get();
            if ("\"ok\"".equals(result)) return;
            if (!"\"loading\"".equals(result)) break;
            Thread.sleep(100);
        }
        fail("Public workbench did not complete in real WebView: " + result);
    }
    @Test public void voiceTransportExistsOnlyWithSupportedWebMessageFeature() throws Exception {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> value = new java.util.concurrent.atomic.AtomicReference<>("");
            final boolean[] supported = {false};
            rule.getScenario().onActivity(activity -> {
                supported[0] = androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER);
                activity.workbenchView().evaluateJavascript(
                    "(function(){if(!window.LivingLibrary)return 'loading';"
                    + "var bridge=window.LivingLibraryNativeVoice;"
                    + "if(!bridge)return 'unsupported';"
                    + "bridge.postMessage(JSON.stringify({v:1,op:'start',session:'invalid session!'}));"
                    + "return typeof bridge.postMessage==='function'&&typeof AminGen2==='undefined'&&typeof AminPackageCanvasFiles==='undefined'?'scoped':'unsafe';})()",
                    answer -> { value.set(answer); latch.countDown(); });
            });
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) continue;
            if ("\"loading\"".equals(value.get())) { Thread.sleep(100); continue; }
            assertEquals(supported[0] ? "\"scoped\"" : "\"unsupported\"", value.get());
            return;
        }
        fail("Voice transport did not initialize in trusted library WebView");
    }
}
