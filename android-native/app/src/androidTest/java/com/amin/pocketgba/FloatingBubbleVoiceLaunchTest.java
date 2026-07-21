package com.amin.pocketgba;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;

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
public final class FloatingBubbleVoiceLaunchTest {
    @Test
    public void pendingIntentLaunchFromApplicationContextOpensVoiceActivity() throws Exception {
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
}
