package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
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
    public void rendersClearPortraitPermissionActions() {
        activityRule.getScenario().onActivity(activity -> {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
            View root = activity.findViewById(android.R.id.content);
            assertTrue(containsText(root, "權限與裝置"));
            assertTrue(containsText(root, "依序處理建議設定"));
            assertTrue(containsText(root, "通知"));
            assertTrue(containsText(root, "安裝 App 更新"));
            assertTrue(containsText(root, "App 完整設定"));
            assertTrue(containsText(root, "電池管理"));
            assertTrue(containsText(root, "顯示安全設計說明"));
        });
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
