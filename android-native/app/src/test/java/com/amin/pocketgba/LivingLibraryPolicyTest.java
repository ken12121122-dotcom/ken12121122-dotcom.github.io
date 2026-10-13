package com.amin.pocketgba;
import org.junit.Test;
import static org.junit.Assert.*;
public final class LivingLibraryPolicyTest {
    @Test public void onlyExactBundledAssetsAreReadable() {
        assertTrue(LivingLibraryPolicy.isHome(LivingLibraryPolicy.HOME));
        assertNotNull(LivingLibraryPolicy.asset(LivingLibraryPolicy.HOME + "?v=1"));
        for (String url : new String[]{"https://evil.example/living-library/index.html", "http://appassets.androidplatform.net/living-library/index.html", "https://appassets.androidplatform.net:443/living-library/index.html", "https://user@appassets.androidplatform.net/living-library/index.html", "https://appassets.androidplatform.net/living-library/../secret", "https://appassets.androidplatform.net/living-library/%69ndex.html"}) assertNull(url, LivingLibraryPolicy.asset(url));
    }
    @Test public void onlyExactOnlineWorkbenchCanTriggerNativeWithRealUserGesture() {
        assertTrue(LivingLibraryPolicy.isRemoteHome(LivingLibraryPolicy.REMOTE_HOME));
        assertTrue(LivingLibraryPolicy.isRemoteHome(LivingLibraryPolicy.REMOTE_HOME + "?v=12345"));
        assertTrue(LivingLibraryPolicy.isHome(LivingLibraryPolicy.REMOTE_HOME));
        assertNull("remote HTML is not an APK intercepted asset", LivingLibraryPolicy.asset(LivingLibraryPolicy.REMOTE_HOME));
        assertTrue(LivingLibraryActivity.canOpenNative(LivingLibraryPolicy.REMOTE_HOME, true, true));
        assertFalse(LivingLibraryActivity.canOpenNative(LivingLibraryPolicy.REMOTE_HOME, false, true));
        assertFalse(LivingLibraryActivity.canOpenNative(LivingLibraryPolicy.REMOTE_HOME, true, false));
        for (String url : new String[]{
            "http://ken12121122-dotcom.github.io/living-library/live/index.html",
            "https://evil.example/living-library/live/index.html",
            "https://user@ken12121122-dotcom.github.io/living-library/live/index.html",
            "https://ken12121122-dotcom.github.io:443/living-library/live/index.html",
            "https://ken12121122-dotcom.github.io/living-library/live/%69ndex.html",
            "https://ken12121122-dotcom.github.io/living-library/live/../index.html",
            "https://ken12121122-dotcom.github.io/living-library/live/index.html?route=gba",
            "https://ken12121122-dotcom.github.io/living-library/live/index.html#attack",
            "https://ken12121122-dotcom.github.io/living-library/live/other.html"
        }) {
            assertFalse(url, LivingLibraryPolicy.isHome(url));
            assertFalse(url, LivingLibraryActivity.canOpenNative(url, true, true));
        }
    }
    @Test public void routesCannotBecomeCommandsOrArbitraryActivities() {
        for (String key : LivingLibraryPolicy.ROUTES.keySet()) assertEquals(key, LivingLibraryPolicy.route("amin-library://" + key));
        for (String url : new String[]{"amin-library://permissions/", "amin-library://permissions?grant=true", "amin-library://permissions#x", "amin-library://user@permissions", "amin-library://execute", "intent://permissions", "amin-library://permissions:7"}) assertNull(url, LivingLibraryPolicy.route(url));
    }
}
