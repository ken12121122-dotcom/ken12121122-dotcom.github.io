package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class PackageCanvasActivityTest {
    @Rule
    public final ActivityScenarioRule<PackageCanvasActivity> activityRule =
            new ActivityScenarioRule<>(PackageCanvasActivity.class);

    @Test
    public void opensLivePackageCanvasPage() {
        activityRule.getScenario().onActivity(activity ->
                assertEquals("https://ken12121122-dotcom.github.io/packagecanvas/", activity.startUrl()));
    }

    @Test
    public void bridgeRefusesPagesOtherThanPackageCanvas() {
        activityRule.getScenario().onActivity(activity -> {
            activity.committedUrl = "https://ken12121122-dotcom.github.io/amin-vault/gba.html";
            PackageCanvasActivity.FolderBridge bridge = activity.new FolderBridge();
            assertFalse(bridge.isAvailable());
            assertFalse(bridge.pickFolder());
            assertFalse(bridge.forgetFolder("any"));
            assertTrue(bridge.listFolders().contains("untrusted"));
            assertTrue(bridge.listMarkdown("any").contains("untrusted"));
            assertTrue(bridge.readText("any", "README.md").contains("untrusted"));
        });
    }

    @Test
    public void bridgeListsNoFoldersBeforeTheUserGrantsOne() {
        activityRule.getScenario().onActivity(activity -> {
            activity.committedUrl = "https://ken12121122-dotcom.github.io/packagecanvas/";
            PackageCanvasActivity.FolderBridge bridge = activity.new FolderBridge();
            assertTrue(bridge.isAvailable());
            try {
                JSONObject folders = new JSONObject(bridge.listFolders());
                assertTrue(folders.getBoolean("ok"));
                assertEquals(0, folders.getJSONArray("folders").length());
                JSONObject missing = new JSONObject(bridge.listMarkdown("not-granted"));
                assertFalse(missing.getBoolean("ok"));
                JSONObject unread = new JSONObject(bridge.readText("not-granted", "README.md"));
                assertFalse(unread.getBoolean("ok"));
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
    }
}
