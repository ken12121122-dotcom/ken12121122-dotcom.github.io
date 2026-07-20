package com.amin.pocketgba;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class VoiceCommandActivityTest {
    @Rule
    public final ActivityScenarioRule<VoiceCommandActivity> activityRule =
            new ActivityScenarioRule<>(VoiceCommandActivity.class);

    @Test
    public void rendersPressToTalkSurfaceWithoutStartingBackgroundListening() {
        onView(withContentDescription("按住開始語音辨識，放開後執行指令"))
                .check(matches(isDisplayed()));

        activityRule.getScenario().onActivity(activity -> {
            View root = activity.findViewById(android.R.id.content);
            assertTrue(containsText(root, "Amin 語音指令"));
            assertTrue(containsText(root, "按住說話"));
            assertTrue(containsText(root, "第一版不會在背景持續監聽"));
        });
    }

    private boolean containsText(View view, String target) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().contains(target)) return true;
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
