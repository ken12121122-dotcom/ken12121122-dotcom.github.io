package com.amin.pocketgba;

import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class ControlCenterActivityTest {
    @Rule
    public final ActivityScenarioRule<ControlCenterActivity> activityRule =
            new ActivityScenarioRule<>(ControlCenterActivity.class);

    @Before
    public void setUpIntents() {
        Intents.init();
    }

    @After
    public void releaseIntents() {
        Intents.release();
    }

    @Test
    public void rendersSimplifiedPortraitDashboard() {
        activityRule.getScenario().onActivity(activity -> {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
            View root = activity.findViewById(android.R.id.content);
            assertTrue(containsText(root, "遊戲控制台"));
            assertTrue(containsText(root, "開啟 GBA 遊戲庫"));
            assertTrue(containsText(root, "Amin 語音指令"));
            assertTrue(containsText(root, "目前狀態"));
            assertTrue(containsText(root, "權限與裝置"));
            assertTrue(containsText(root, "版本與更新"));
            assertTrue(containsText(root, "顯示系統詳細資訊"));
            assertTrue(containsText(root, BuildConfig.VERSION_NAME));
        });
    }

    @Test
    public void gbaCardRoutesToLibrary() {
        assertCardRoutesTo(
                "進入 Pokémon GBA 遊戲庫",
                MainActivity.class.getName()
        );
    }

    @Test
    public void voiceCardRoutesToPressToTalk() {
        assertCardRoutesTo(
                "開啟 Amin 語音指令",
                VoiceCommandActivity.class.getName()
        );
    }

    @Test
    public void permissionCardRoutesToPermissionCenter() {
        assertCardRoutesTo(
                "開啟權限控制中心",
                PermissionCenterActivity.class.getName()
        );
    }

    @Test
    public void updateCardRoutesToUpdateCenter() {
        assertCardRoutesTo(
                "開啟原生 APK 更新中心",
                UpdateHubActivity.class.getName()
        );
    }

    private void assertCardRoutesTo(String contentDescription, String componentName) {
        intending(hasComponent(componentName))
                .respondWith(new ActivityResult(Activity.RESULT_OK, null));

        clickCard(contentDescription);

        List<Intent> recorded = Intents.getIntents();
        boolean found = false;
        for (Intent intent : recorded) {
            if (intent.getComponent() != null
                    && componentName.equals(intent.getComponent().getClassName())) {
                found = true;
                break;
            }
        }
        assertTrue("Expected route was not recorded: " + componentName, found);
    }

    private void clickCard(String contentDescription) {
        activityRule.getScenario().onActivity(activity -> {
            View root = activity.findViewById(android.R.id.content);
            View target = findByContentDescription(root, contentDescription);
            assertNotNull("Missing card: " + contentDescription, target);
            assertTrue("Card did not accept click: " + contentDescription, target.performClick());
        });
    }

    private View findByContentDescription(View view, String target) {
        CharSequence description = view.getContentDescription();
        if (description != null && target.contentEquals(description)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index += 1) {
                View match = findByContentDescription(group.getChildAt(index), target);
                if (match != null) return match;
            }
        }
        return null;
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
