package com.amin.pocketgba;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;

public final class FloatingVoiceLaunchTest {
    @Test
    public void autoStartsOnlyForAccessibilityBubbleFlagCombination() {
        int floatingFlags = Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP;

        assertTrue(VoiceCommandActivity.shouldAutoStartFloatingVoice(floatingFlags, null));
        assertFalse(VoiceCommandActivity.shouldAutoStartFloatingVoice(Intent.FLAG_ACTIVITY_NEW_TASK, null));
        assertFalse(VoiceCommandActivity.shouldAutoStartFloatingVoice(floatingFlags, "amin-voice://open"));
        assertFalse(VoiceCommandActivity.shouldAutoStartFloatingVoice(0, null));
    }
}
