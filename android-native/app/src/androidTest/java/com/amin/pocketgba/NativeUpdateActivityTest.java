package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class NativeUpdateActivityTest {
    @Rule
    public final ActivityScenarioRule<UpdateHubActivity> activityRule =
            new ActivityScenarioRule<>(UpdateHubActivity.class);

    @Test
    public void rendersCleanPortraitUpdateHub() {
        activityRule.getScenario().onActivity(activity -> {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
            View root = activity.findViewById(android.R.id.content);
            assertTrue(containsText(root, "版本與更新"));
            assertTrue(containsText(root, "目前安裝版本"));
            assertTrue(containsText(root, "重新檢查"));
            assertTrue(containsText(root, "更新安全機制"));
        });
    }

    @Test
    public void updateDeepLinkResolvesToTheCleanHub() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("amin-update://check"));
        intent.setPackage(context.getPackageName());
        ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

        assertNotNull(resolved);
        assertEquals(
                UpdateHubActivity.class.getName(),
                resolved.activityInfo.name
        );
    }

    private boolean containsText(View view, String target) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (target.contentEquals(value)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index += 1) {
                if (containsText(group.getChildAt(index), target)) return true;
            }
        }
        return false;
    }
}
