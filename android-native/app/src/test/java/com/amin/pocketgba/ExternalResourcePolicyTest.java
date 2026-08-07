package com.amin.pocketgba;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;

public final class ExternalResourcePolicyTest {
    private boolean allowed(String raw) {
        Uri uri = Uri.parse(raw == null ? "" : raw.trim());
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().trim().isEmpty();
    }

    @Test public void allowsHttpsResources() {
        assertTrue(allowed("https://docs.google.com/spreadsheets/d/example/edit"));
        assertTrue(allowed("https://github.com/example/repo"));
    }

    @Test public void blocksNonHttpsAndOpaqueSchemes() {
        assertFalse(allowed("http://example.com"));
        assertFalse(allowed("file:///sdcard/test"));
        assertFalse(allowed("javascript:alert(1)"));
        assertFalse(allowed("intent://example"));
    }
}
