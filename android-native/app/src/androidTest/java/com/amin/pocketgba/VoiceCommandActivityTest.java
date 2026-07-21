package com.amin.pocketgba;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class VoiceCommandActivityTest {
    @Test
    public void rendersPressToTalkAndIndependentFloatingControls() {
        ActivityScenario<VoiceCommandActivity> scenario =
                ActivityScenario.launch(VoiceCommandActivity.class);
        try {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertTrue(containsText(root, "Amin 語音指令"));
                assertTrue(containsText(root, "按住說話"));
                assertTrue(containsText(root, "不會在背景持續監聽"));
                assertTrue(containsText(root, "浮動按鈕"));
                assertTrue(containsText(root, "個別開啟或關閉"));
                assertTrue(containsText(root, VoiceCommandCatalog.getCommandCount() + " 個動作"));
                assertTrue(containsText(root, VoiceCommandCatalog.getPhraseCount() + " 種可說法"));
                assertNotNull(findByContentDescription(
                        root,
                        "按住開始語音辨識，放開後執行指令"
                ));
                assertNotNull(findByContentDescription(root, "查看全部語音指令"));
                assertNotNull(findByContentDescription(root, "切換語音浮動按鈕"));
                assertNotNull(findByContentDescription(root, "切換鍵盤浮動按鈕"));
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void keyboardAndVoiceFloatingPreferencesAreIndependent() {
        Context context = ApplicationProvider.getApplicationContext();
        boolean originalKeyboard =
                UniversalControlAccessibilityService.isKeyboardBubbleEnabled(context);
        boolean originalVoice =
                UniversalControlAccessibilityService.isVoiceBubbleEnabled(context);
        try {
            UniversalControlAccessibilityService.setKeyboardBubbleEnabled(context, false);
            UniversalControlAccessibilityService.setVoiceBubbleEnabled(context, true);
            assertFalse(UniversalControlAccessibilityService.isKeyboardBubbleEnabled(context));
            assertTrue(UniversalControlAccessibilityService.isVoiceBubbleEnabled(context));

            UniversalControlAccessibilityService.setKeyboardBubbleEnabled(context, true);
            UniversalControlAccessibilityService.setVoiceBubbleEnabled(context, false);
            assertTrue(UniversalControlAccessibilityService.isKeyboardBubbleEnabled(context));
            assertFalse(UniversalControlAccessibilityService.isVoiceBubbleEnabled(context));
        } finally {
            UniversalControlAccessibilityService.setKeyboardBubbleEnabled(context, originalKeyboard);
            UniversalControlAccessibilityService.setVoiceBubbleEnabled(context, originalVoice);
        }
    }

    @Test
    public void rendersSynchronizedSearchableCommandCatalog() {
        ActivityScenario<VoiceCommandCatalogActivity> scenario =
                ActivityScenario.launch(VoiceCommandCatalogActivity.class);
        try {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertTrue(containsText(root, "語音指令庫"));
                assertTrue(containsText(root, VoiceCommandCatalog.getCommandCount() + " 個動作"));
                assertTrue(containsText(root, VoiceCommandCatalog.getPhraseCount() + " 種可說法"));
                assertTrue(containsText(root, "開啟鍵盤控制"));
                assertTrue(containsText(root, "關閉語音浮動按鈕"));
                assertTrue(containsText(root, "回到首頁"));
                assertTrue(containsText(root, "開啟遊戲庫"));
                assertTrue(containsText(root, "需全域控制"));
                assertTrue(containsText(root, "可直接使用"));
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void floatingBubblePendingIntentHandsOffToVoiceActivity() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        VoiceCommandActivityLauncher.LaunchResult result =
                VoiceCommandActivityLauncher.openFromFloatingBubble(context);

        assertTrue(result.getMessage(), result.isSuccess());

        long deadline = System.currentTimeMillis() + 5000L;
        while (!VoiceCommandActivityLauncher.wasAcknowledged(result.getToken())
                && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(100L);
        }

        assertTrue(
                "VoiceCommandActivity did not acknowledge the floating-bubble launch",
                VoiceCommandActivityLauncher.wasAcknowledged(result.getToken())
        );

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            for (Activity activity : resumed) {
                if (activity instanceof VoiceCommandActivity) activity.finish();
            }
        });
    }

    private View findByContentDescription(View view, String target) {
        CharSequence description = view.getContentDescription();
        if (description != null && target.contentEquals(description)) return view;
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
