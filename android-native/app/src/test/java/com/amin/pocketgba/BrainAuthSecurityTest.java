package com.amin.pocketgba;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrainAuthSecurityTest {
    @Test
    public void apkConfigurationContainsOnlyPublicClientIdAndNoSecretField() throws Exception {
        String gradle = read("build.gradle");
        String vault = read("src/main/java/com/amin/pocketgba/BrainTokenVault.java");
        assertTrue(gradle.contains("AMIN_GITHUB_APP_CLIENT_ID"));
        assertFalse(gradle.contains("GITHUB_APP_CLIENT_SECRET"));
        assertFalse(gradle.contains("GITHUB_APP_PRIVATE_KEY"));
        assertTrue(vault.contains("AndroidKeyStore"));
        assertTrue(vault.contains("AES/GCM/NoPadding"));
    }

    @Test
    public void backupRulesDoNotIncludeBrainAuthenticationPreferences() throws Exception {
        assertFalse(read("src/main/res/xml/backup_rules.xml").contains("amin_brain_auth"));
        assertFalse(read("src/main/res/xml/data_extraction_rules.xml").contains("amin_brain_auth"));
    }

    @Test
    public void ciAndReleaseInjectPublicClientIdAndReleaseRejectsEmptyConfiguration() throws Exception {
        String ci = read("../../.github/workflows/android-ci.yml");
        String release = read("../../.github/workflows/android-release.yml");
        assertTrue(ci.contains("vars.AMIN_GITHUB_APP_CLIENT_ID"));
        assertTrue(ci.contains("-PAMIN_GITHUB_APP_CLIENT_ID=\"$AMIN_GITHUB_APP_CLIENT_ID\""));
        assertTrue(release.contains("vars.AMIN_GITHUB_APP_CLIENT_ID"));
        assertTrue(release.contains("test -n \"$AMIN_GITHUB_APP_CLIENT_ID\""));
        assertFalse(ci.contains("GITHUB_APP_CLIENT_SECRET"));
        assertFalse(release.contains("GITHUB_APP_PRIVATE_KEY"));
    }

    private static String read(String relative) throws Exception {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path directApp = current.resolve("app");
            Path repositoryApp = current.resolve("android-native/app");
            if (Files.isRegularFile(directApp.resolve("build.gradle"))) {
                return new String(Files.readAllBytes(directApp.resolve(relative)), StandardCharsets.UTF_8);
            }
            if (Files.isRegularFile(repositoryApp.resolve("build.gradle"))) {
                return new String(Files.readAllBytes(repositoryApp.resolve(relative)), StandardCharsets.UTF_8);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate android-native/app from user.dir");
    }
}
