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
}
