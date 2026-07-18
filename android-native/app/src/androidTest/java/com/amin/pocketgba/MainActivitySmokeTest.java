package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.pm.ActivityInfo;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class MainActivitySmokeTest {
    @Rule
    public final ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void keepsLibraryPortraitAndRotatesOnlyGameplay() {
        activityRule.getScenario().onActivity(activity -> {
            FrameLayout root = activity.findViewById(R.id.root);
            WebView webView = activity.findViewById(R.id.webView);

            assertNotNull(root);
            assertNotNull(webView);
            assertEquals(View.VISIBLE, root.getVisibility());
            assertEquals(View.VISIBLE, webView.getVisibility());
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());

            activity.applyRequestedOrientation("landscape");
            assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                    activity.getRequestedOrientation()
            );

            activity.applyRequestedOrientation("portrait");
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
        });
    }
}
