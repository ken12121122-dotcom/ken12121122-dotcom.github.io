package com.amin.pocketgba;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LivingLibraryFileChooserPolicyTest {
    @Test public void actualPhotoInputAndBackupAcceptValuesWork() {
        assertArrayEquals(new String[]{"image/png", "image/jpeg", "image/webp"},
                LivingLibraryFileChooserPolicy.mimeTypes(new String[]{"image/png,image/jpeg,image/webp"}));
        assertArrayEquals(new String[]{"image/jpeg", "image/png", "image/webp"},
                LivingLibraryFileChooserPolicy.mimeTypes(new String[]{"image/*"}));
        assertArrayEquals(new String[]{"application/json"},
                LivingLibraryFileChooserPolicy.mimeTypes(new String[]{".json,application/json"}));
    }
    @Test public void broadExecutableAndMixedTypesAreRejected() {
        for (String accept : new String[]{"*/*", "", "text/html", "image/svg+xml", "image/png,application/json", "image/png,", "application/octet-stream"})
            assertEquals(accept, 0, LivingLibraryFileChooserPolicy.mimeTypes(new String[]{accept}).length);
        assertEquals(0, LivingLibraryFileChooserPolicy.mimeTypes(null).length);
        assertEquals(0, LivingLibraryFileChooserPolicy.mimeTypes(new String[]{null}).length);
    }
    @Test public void onlyTheUnchangedCommittedLibraryHomeCanRequestAFile() {
        String page = LivingLibraryPolicy.REMOTE_HOME + "?v=123";
        assertTrue(LivingLibraryFileChooserPolicy.allowedPage(page, page));
        assertTrue(LivingLibraryFileChooserPolicy.allowedPage(LivingLibraryPolicy.HOME, LivingLibraryPolicy.HOME));
        assertFalse(LivingLibraryFileChooserPolicy.allowedPage(page, LivingLibraryPolicy.REMOTE_HOME + "?v=124"));
        assertFalse(LivingLibraryFileChooserPolicy.allowedPage("https://evil.example", "https://evil.example"));
        assertFalse(LivingLibraryFileChooserPolicy.allowedPage(null, page));
        assertFalse(LivingLibraryFileChooserPolicy.allowedPage("", page));
    }
}
