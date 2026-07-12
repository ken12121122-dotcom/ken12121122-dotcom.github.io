package com.amin.pocketgba;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class PermissionCenterActivityTest {
    @Rule
    public final ActivityScenarioRule<PermissionCenterActivity> activityRule =
            new ActivityScenarioRule<>(PermissionCenterActivity.class);

    @Test
    public void rendersPermissionControlsAndSecurityExplanation() {
        onView(withText("權限控制中心")).check(matches(isDisplayed()));
        onView(withText("啟用建議權限")).check(matches(isDisplayed()));
        onView(withText("通知")).check(matches(isDisplayed()));
        onView(withText("安裝 App 更新")).check(matches(isDisplayed()));
        onView(withText("開啟 App 完整權限頁")).check(matches(isDisplayed()));
        onView(withText("開啟通知詳細設定")).check(matches(isDisplayed()));
        onView(withText("開啟電池最佳化設定")).check(matches(isDisplayed()));
        onView(withText("整機檔案管理  ·  不要求")).check(matches(isDisplayed()));
    }

    @Test
    public void manifestContainsOnlyTheApprovedPermissionSurface() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                context.getPackageName(),
                PackageManager.GET_PERMISSIONS
        );
        Set<String> requested = packageInfo.requestedPermissions == null
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(packageInfo.requestedPermissions));

        assertTrue(requested.contains(Manifest.permission.INTERNET));
        assertTrue(requested.contains(Manifest.permission.ACCESS_NETWORK_STATE));
        assertTrue(requested.contains(Manifest.permission.VIBRATE));
        assertTrue(requested.contains(Manifest.permission.POST_NOTIFICATIONS));
        assertTrue(requested.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES));

        assertFalse(requested.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE));
        assertFalse(requested.contains(Manifest.permission.CAMERA));
        assertFalse(requested.contains(Manifest.permission.RECORD_AUDIO));
        assertFalse(requested.contains(Manifest.permission.ACCESS_FINE_LOCATION));
        assertFalse(requested.contains(Manifest.permission.READ_CONTACTS));
        assertFalse(requested.contains(Manifest.permission.READ_SMS));
        assertFalse(requested.contains(Manifest.permission.CALL_PHONE));
        assertFalse(requested.contains(Manifest.permission.SYSTEM_ALERT_WINDOW));
    }

    @Test
    public void permissionDeepLinkResolvesToTheNativeActivity() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("amin-permissions://open"));
        intent.setPackage(context.getPackageName());
        ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

        assertNotNull(resolved);
        assertEquals(
                PermissionCenterActivity.class.getName(),
                resolved.activityInfo.name
        );
    }
}
