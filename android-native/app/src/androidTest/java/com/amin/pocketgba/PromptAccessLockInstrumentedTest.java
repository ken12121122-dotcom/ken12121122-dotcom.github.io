package com.amin.pocketgba;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PromptAccessLockInstrumentedTest {
    private Context context;
    private PromptAccessLock lock;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("prompt_access_lock_v1", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        lock = new PromptAccessLock(context);
    }

    @After
    public void tearDown() {
        context.getSharedPreferences("prompt_access_lock_v1", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void correctPinUnlocksAndWrongPinProgressivelyDelays() throws Exception {
        char[] correct = "246810".toCharArray();
        lock.configure(correct);
        PromptAccessLock.clear(correct);

        PromptAccessLock.VerifyResult first = lock.verify("000000".toCharArray(), 1_000L);
        assertFalse(first.success);
        assertTrue(first.retryAfterMillis >= 5_000L);

        PromptAccessLock.VerifyResult blocked = lock.verify("246810".toCharArray(), 2_000L);
        assertTrue(blocked.blocked);

        PromptAccessLock.VerifyResult second = lock.verify("000000".toCharArray(), 7_000L);
        assertFalse(second.success);
        assertTrue(second.retryAfterMillis > first.retryAfterMillis);

        PromptAccessLock.VerifyResult success = lock.verify("246810".toCharArray(), 22_000L);
        assertTrue(success.success);
    }

    @Test
    public void verifierDoesNotStorePlainPin() throws Exception {
        char[] pin = "135790".toCharArray();
        lock.configure(pin);
        PromptAccessLock.clear(pin);

        String prefs = context.getSharedPreferences("prompt_access_lock_v1", Context.MODE_PRIVATE)
                .getAll()
                .toString();
        assertFalse(prefs.contains("135790"));
    }
}
