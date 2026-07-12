package com.amin.pocketgba;

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
public final class ControlCenterActivityTest {
    @Rule
    public final ActivityScenarioRule<ControlCenterActivity> activityRule =
            new ActivityScenarioRule<>(ControlCenterActivity.class);

    @Test
    public void rendersVisibleStartupStatusAndNativeEntries() {
        activityRule.getScenario().onActivity(activity -> {
            View root = activity.findViewById(android.R.id.content);
            assertTrue(containsText(root, "原生控制台"));
            assertTrue(containsText(root, "重新自動偵測"));
            assertTrue(containsText(root, "進入 Pokémon GBA 遊戲庫"));
            assertTrue(containsText(root, "開啟權限控制中心"));
            assertTrue(containsText(root, "開啟原生 APK 更新中心"));
            assertTrue(containsText(root, "這一包實際新增了什麼"));
        });
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
