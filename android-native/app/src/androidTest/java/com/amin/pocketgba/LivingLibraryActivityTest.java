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
        long deadline = System.currentTimeMillis() + 15000;
        String result = "";
        while (System.currentTimeMillis() < deadline) {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> value = new java.util.concurrent.atomic.AtomicReference<>("");
            rule.getScenario().onActivity(activity -> activity.workbenchView().evaluateJavascript(
                "(function(){var l=window.LivingLibrary;if(!l)return 'loading';"
                + "if(l.books.length!==14||typeof AminPackageCanvasFiles!=='undefined'||typeof AminGen2!=='undefined')return 'unsafe';"
                + "l.select('android');l.openBook();var cards=document.querySelectorAll('#drawerFunctions button');"
                + "if(cards.length!==16)return 'missing-cards';var p=document.querySelector('[data-card-id=\\\"public.android.permissions\\\"]');p.click();"
                + "var a=document.querySelector('#sourcePage a');if(!a||a.getAttribute('href')!=='amin-library://permissions')return 'missing-route';"
                + "document.getElementById('readerBack').click();if(!document.getElementById('readerShell').hidden)return 'broken-back';"
                + "document.getElementById('closeDrawer').click();return 'ok';})()",
                answer -> { value.set(answer); latch.countDown(); }));
            assertTrue("WebView callback timed out", latch.await(3, java.util.concurrent.TimeUnit.SECONDS));
            result = value.get();
            if ("\"ok\"".equals(result)) return;
            if (!"\"loading\"".equals(result)) break;
            Thread.sleep(100);
        }
        fail("Public workbench did not complete in real WebView: " + result);
    }
}
