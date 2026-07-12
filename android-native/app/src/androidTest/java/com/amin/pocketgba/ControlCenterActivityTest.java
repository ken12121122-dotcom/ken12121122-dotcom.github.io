package com.amin.pocketgba;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;
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
            assertTrue(containsText(root, "目前狀態"));
            assertTrue(containsText(root, "權限與裝置"));
            assertTrue(containsText(root, "版本與更新"));
            assertTrue(containsText(root, "顯示系統詳細資訊"));
        });
    }

    @Test
    public void primaryCardsRouteToRealActivities() {
        intending(hasComponent(MainActivity.class.getName()))
                .respondWith(new ActivityResult(Activity.RESULT_OK, null));
        intending(hasComponent(PermissionCenterActivity.class.getName()))
                .respondWith(new ActivityResult(Activity.RESULT_OK, null));
        intending(hasComponent(UpdateHubActivity.class.getName()))
                .respondWith(new ActivityResult(Activity.RESULT_OK, null));

        onView(withContentDescription("進入 Pokémon GBA 遊戲庫")).perform(scrollTo(), click());
        intended(hasComponent(MainActivity.class.getName()));

        onView(withContentDescription("開啟權限控制中心")).perform(scrollTo(), click());
        intended(hasComponent(PermissionCenterActivity.class.getName()));

        onView(withContentDescription("開啟原生 APK 更新中心")).perform(scrollTo(), click());
        intended(hasComponent(UpdateHubActivity.class.getName()));
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
