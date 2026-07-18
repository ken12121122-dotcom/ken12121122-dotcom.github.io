package com.amin.pocketgba;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class NativeSaveBridgeTest {
    private Context context;
    private NativeSaveBridge bridge;
    private String key;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        bridge = new NativeSaveBridge(context);
        key = "test_" + UUID.randomUUID().toString().replace("-", "");
    }

    @After
    public void cleanUp() {
        File directory = new File(context.getFilesDir(), "gba-saves");
        new File(directory, key + ".sav").delete();
        new File(directory, key + ".bak").delete();
        new File(directory, key + ".json").delete();
        new File(directory, key + ".tmp").delete();
    }

    @Test
    public void storesReadsKeepsPreviousGenerationAndSkipsDuplicates() throws Exception {
        byte[] first = "first-save-generation".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second-save-generation".getBytes(StandardCharsets.UTF_8);

        JSONObject firstResult = new JSONObject(bridge.putSave(
                key,
                Base64.encodeToString(first, Base64.NO_WRAP)
        ));
        assertTrue(firstResult.optBoolean("ok"));
        assertFalse(firstResult.optBoolean("unchanged"));
        assertArrayEquals(first, Base64.decode(bridge.getSave(key), Base64.DEFAULT));

        JSONObject secondResult = new JSONObject(bridge.putSave(
                key,
                Base64.encodeToString(second, Base64.NO_WRAP)
        ));
        assertTrue(secondResult.optBoolean("ok"));
        assertFalse(secondResult.optBoolean("unchanged"));
        assertTrue(secondResult.optBoolean("hasBackup"));
        assertArrayEquals(second, Base64.decode(bridge.getSave(key), Base64.DEFAULT));

        JSONObject duplicateResult = new JSONObject(bridge.putSave(
                key,
                Base64.encodeToString(second, Base64.NO_WRAP)
        ));
        assertTrue(duplicateResult.optBoolean("ok"));
        assertTrue(duplicateResult.optBoolean("unchanged"));
        assertTrue(duplicateResult.optBoolean("hasBackup"));

        JSONObject info = new JSONObject(bridge.getSaveInfo(key));
        assertTrue(info.optBoolean("ok"));
        assertTrue(info.optBoolean("exists"));
        assertTrue(info.optBoolean("hasBackup"));
        assertTrue(info.optLong("updatedAt") > 0L);
        assertTrue(info.optString("sha256").matches("[a-f0-9]{64}"));
    }

    @Test
    public void rejectsUnsafeKeysAndOversizedInput() throws Exception {
        JSONObject unsafe = new JSONObject(bridge.putSave(
                "../../outside",
                Base64.encodeToString(new byte[]{1, 2, 3}, Base64.NO_WRAP)
        ));
        assertFalse(unsafe.optBoolean("ok"));

        String oversized = "A".repeat(3 * 1024 * 1024 + 1);
        JSONObject tooLarge = new JSONObject(bridge.putSave(key, oversized));
        assertFalse(tooLarge.optBoolean("ok"));
    }
}
