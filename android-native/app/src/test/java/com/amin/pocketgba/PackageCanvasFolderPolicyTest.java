package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PackageCanvasFolderPolicyTest {
    @Test
    public void onlyThePackageCanvasPageIsTrusted() {
        assertTrue(PackageCanvasFolderPolicy.isTrustedPage("https://ken12121122-dotcom.github.io/packagecanvas/"));
        assertTrue(PackageCanvasFolderPolicy.isTrustedPage("https://ken12121122-dotcom.github.io/packagecanvas/index.html?_pcbuild=1"));
        assertTrue(PackageCanvasFolderPolicy.isTrustedPage("https://KEN12121122-DOTCOM.github.io/packagecanvas"));

        assertFalse(PackageCanvasFolderPolicy.isTrustedPage(null));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage(""));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("http://ken12121122-dotcom.github.io/packagecanvas/"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("https://ken12121122-dotcom.github.io/amin-vault/gba.html"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("https://ken12121122-dotcom.github.io/packagecanvas-evil/"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("https://ken12121122-dotcom.github.io/packagecanvas/../amin-vault/"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("https://evil.example/packagecanvas/"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("https://ken12121122-dotcom.github.io.evil.example/packagecanvas/"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("https://user@ken12121122-dotcom.github.io/packagecanvas/"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("https://ken12121122-dotcom.github.io:8443/packagecanvas/"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("file:///android_asset/packagecanvas/index.html"));
        assertFalse(PackageCanvasFolderPolicy.isTrustedPage("javascript:alert(1)"));
    }

    @Test
    public void onlySafeMarkdownNamesAndVisibleFoldersAreRead() {
        assertTrue(PackageCanvasFolderPolicy.isMarkdownName("KB_REGISTRY.md"));
        assertTrue(PackageCanvasFolderPolicy.isMarkdownName("WF-TIME-001.MD"));
        assertFalse(PackageCanvasFolderPolicy.isMarkdownName("sheet.xlsx"));
        assertFalse(PackageCanvasFolderPolicy.isMarkdownName("../x.md"));
        assertFalse(PackageCanvasFolderPolicy.isMarkdownName(null));

        assertTrue(PackageCanvasFolderPolicy.shouldWalkDirectory("02_KNOWLEDGE_BASES"));
        assertTrue(PackageCanvasFolderPolicy.shouldWalkDirectory("09_待刪除"));
        assertFalse(PackageCanvasFolderPolicy.shouldWalkDirectory(".obsidian"));
        assertFalse(PackageCanvasFolderPolicy.shouldWalkDirectory(".."));
        assertFalse(PackageCanvasFolderPolicy.shouldWalkDirectory("a/b"));

        assertEquals("README.md", PackageCanvasFolderPolicy.childPath("", "README.md"));
        assertEquals("00_ENTRY/README.md", PackageCanvasFolderPolicy.childPath("00_ENTRY", "README.md"));
    }

    @Test
    public void bridgeStaysReadOnlyAndSeparateFromKnowledgeProfiles() throws Exception {
        Path source = Paths.get("src/main/java/com/amin/pocketgba/PackageCanvasActivity.java");
        String activity = new String(Files.readAllBytes(source), "UTF-8");
        assertTrue(activity.contains("PackageCanvasFolderPolicy.START_URL"));
        assertTrue(activity.contains("FLAG_GRANT_READ_URI_PERMISSION"));
        assertFalse(activity.contains("FLAG_GRANT_WRITE_URI_PERMISSION"));
        assertFalse(activity.contains("openOutputStream"));
        assertFalse(activity.contains("deleteDocument"));
        assertFalse(activity.contains("createDocument"));
        assertFalse(activity.contains("releasePersistableUriPermission"));
        assertFalse(activity.contains("KnowledgeProfileStore"));
        assertFalse(activity.contains("GraphProfileStore"));
        assertFalse(activity.contains("FoxDependencies"));

        String manifest = new String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")), "UTF-8");
        assertTrue(manifest.contains("android:name=\".PackageCanvasActivity\""));
        assertTrue(manifest.matches("(?s).*android:name=\"\\.PackageCanvasActivity\"[^>]*android:exported=\"false\".*"));
    }
}
