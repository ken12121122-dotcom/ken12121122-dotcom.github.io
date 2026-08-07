package com.amin.pocketgba;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ExternalResourcePolicyTest {
    @Test public void allowsHttpsResources() {
        assertTrue(ExternalResourcePolicy.isAllowedHttps("https://docs.google.com/spreadsheets/d/example/edit"));
        assertTrue(ExternalResourcePolicy.isAllowedHttps("https://github.com/example/repo"));
    }

    @Test public void blocksNonHttpsAndOpaqueSchemes() {
        assertFalse(ExternalResourcePolicy.isAllowedHttps("http://example.com"));
        assertFalse(ExternalResourcePolicy.isAllowedHttps("file:///sdcard/test"));
        assertFalse(ExternalResourcePolicy.isAllowedHttps("javascript:alert(1)"));
        assertFalse(ExternalResourcePolicy.isAllowedHttps("intent://example"));
        assertFalse(ExternalResourcePolicy.isAllowedHttps("not a url"));
        assertFalse(ExternalResourcePolicy.isAllowedHttps(null));
    }
}
