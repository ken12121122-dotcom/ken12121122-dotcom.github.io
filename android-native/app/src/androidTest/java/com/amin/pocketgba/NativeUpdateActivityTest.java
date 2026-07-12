package com.amin.pocketgba;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

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
    public final ActivityScenarioRule<NativeUpdateActivity> activityRule =
            new ActivityScenarioRule<>(NativeUpdateActivity.class);

    @Test
    public void rendersUpdateCenterShellBeforeNetworkResult() {
        assertScrollableTextVisible("Amin Native Update Center");
        assertScrollableTextVisible("重新檢查");
        assertScrollableTextVisible("關閉");
    }

    private void assertScrollableTextVisible(String text) {
        onView(withText(text))
                .perform(scrollTo())
                .check(matches(isDisplayed()));
    }

    @Test
    public void updateDeepLinkResolvesToTheNativeActivity() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("amin-update://check"));
        intent.setPackage(context.getPackageName());
        ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

        assertNotNull(resolved);
        assertEquals(
                NativeUpdateActivity.class.getName(),
                resolved.activityInfo.name
        );
    }
}
