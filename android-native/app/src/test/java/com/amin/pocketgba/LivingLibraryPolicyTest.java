package com.amin.pocketgba;
import org.junit.Test;
import static org.junit.Assert.*;
public final class LivingLibraryPolicyTest {
    @Test public void onlyExactBundledAssetsAreReadable() {
        assertTrue(LivingLibraryPolicy.isHome(LivingLibraryPolicy.HOME));
        assertNotNull(LivingLibraryPolicy.asset(LivingLibraryPolicy.HOME + "?v=1"));
        for (String url : new String[]{"https://evil.example/living-library/index.html", "http://appassets.androidplatform.net/living-library/index.html", "https://appassets.androidplatform.net:443/living-library/index.html", "https://user@appassets.androidplatform.net/living-library/index.html", "https://appassets.androidplatform.net/living-library/../secret", "https://appassets.androidplatform.net/living-library/%69ndex.html"}) assertNull(url, LivingLibraryPolicy.asset(url));
    }
    @Test public void routesCannotBecomeCommandsOrArbitraryActivities() {
        for (String key : LivingLibraryPolicy.ROUTES.keySet()) assertEquals(key, LivingLibraryPolicy.route("amin-library://" + key));
        for (String url : new String[]{"amin-library://permissions/", "amin-library://permissions?grant=true", "amin-library://permissions#x", "amin-library://user@permissions", "amin-library://execute", "intent://permissions", "amin-library://permissions:7"}) assertNull(url, LivingLibraryPolicy.route(url));
    }
}
