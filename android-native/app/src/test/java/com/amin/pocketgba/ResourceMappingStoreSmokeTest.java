package com.amin.pocketgba;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ResourceMappingStoreSmokeTest {
    @Test public void httpsPolicySupportsSheetTabs() {
        assertTrue(ExternalResourcePolicy.isAllowedHttps("https://docs.google.com/spreadsheets/d/example/edit#gid=123"));
    }
}
