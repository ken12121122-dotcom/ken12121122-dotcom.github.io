package com.amin.pocketgba;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class ControlCenterActivityTest {
    @Rule
    public final ActivityScenarioRule<ControlCenterActivity> activityRule =
            new ActivityScenarioRule<>(ControlCenterActivity.class);

    @Test
    public void rendersVisibleStartupStatusAndNativeEntries() {
        assertVisible("原生控制台");
        assertVisible("重新自動偵測");
        assertVisible("進入 Pokémon GBA 遊戲庫");
        assertVisible("開啟權限控制中心");
        assertVisible("開啟原生 APK 更新中心");
        assertVisible("這一包實際新增了什麼");
    }

    private void assertVisible(String value) {
        onView(withText(value))
                .perform(scrollTo())
                .check(matches(isDisplayed()));
    }
}
