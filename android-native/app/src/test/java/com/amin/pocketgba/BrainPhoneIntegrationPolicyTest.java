package com.amin.pocketgba;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrainPhoneIntegrationPolicyTest {
    @Test
    public void manifestAndControlCenterExposeIndependentBrainEntry() throws Exception {
        String manifest = read("src/main/AndroidManifest.xml");
        String controlCenter = read("src/main/java/com/amin/pocketgba/ControlCenterActivity.java");
        assertTrue(manifest.contains(".BrainControlActivity"));
        assertTrue(manifest.contains("amin-brain"));
        assertTrue(manifest.contains(".BrainFeedJobService"));
        assertTrue(controlCenter.contains("AMIN Brain 任務中心"));
        assertTrue(controlCenter.contains("BrainControlActivity.class"));
    }

    @Test
    public void phoneBrainDoesNotReuseLanControlOrGainPublicRepoMutation() throws Exception {
        String activity = read("src/main/java/com/amin/pocketgba/BrainControlActivity.java");
        String api = read("src/main/java/com/amin/pocketgba/GitHubBrainApi.java");
        assertFalse(activity.contains("AminControlApi"));
        assertFalse(activity.contains("localhost"));
        assertFalse(activity.contains("Supabase"));
        assertFalse(api.contains("ken12121122-dotcom.github.io/issues"));
        assertFalse(api.contains("/actions"));
        assertFalse(api.contains("/pulls"));
        assertFalse(api.contains("/secrets"));
        assertTrue(api.contains("amin-agent-control"));
        assertTrue(api.contains("cancelIssue"));
        assertTrue(activity.contains("取消上次任務"));
        assertTrue(activity.contains("capabilitiesInput.setText(\"android:ui\")"));
    }

    @Test
    public void backgroundPollingUsesEtagAndMinimumAndroidPeriod() throws Exception {
        String repository = read("src/main/java/com/amin/pocketgba/BrainFeedRepository.java");
        String job = read("src/main/java/com/amin/pocketgba/BrainFeedJobService.java");
        assertTrue(repository.contains("ETAG"));
        assertTrue(repository.contains("seen_notification_ids"));
        assertTrue(job.contains("15L * 60L * 1000L"));
        assertTrue(job.contains("NETWORK_TYPE_ANY"));
    }

    private static String read(String relative) throws Exception {
        Path working = Path.of(System.getProperty("user.dir"));
        Path app = Files.isRegularFile(working.resolve("app/build.gradle"))
                ? working.resolve("app") : working.resolve("android-native/app");
        return Files.readString(app.resolve(relative), StandardCharsets.UTF_8);
    }
}
