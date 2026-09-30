package com.amin.pocketgba;

import java.net.URI;
import java.util.Locale;

/**
 * Pure rules for the PackageCanvas folder bridge. No Android types so the
 * trust boundary and path handling are covered by JVM unit tests.
 */
final class PackageCanvasFolderPolicy {
    static final String TRUSTED_HOST = "ken12121122-dotcom.github.io";
    static final String TRUSTED_PATH_PREFIX = "/packagecanvas/";
    static final String START_URL = "https://" + TRUSTED_HOST + TRUSTED_PATH_PREFIX;
    static final int MAX_FILES = 3000;
    static final int MAX_DEPTH = 16;
    static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;

    private PackageCanvasFolderPolicy() { }

    /** Only the PackageCanvas page itself may use the folder bridge or stay inside the WebView. */
    static boolean isTrustedPage(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = new URI(url);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && TRUSTED_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1
                    && uri.getRawUserInfo() == null
                    && (path.equals("/packagecanvas") || path.startsWith(TRUSTED_PATH_PREFIX))
                    && !path.contains("/../");
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isMarkdownName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".md") && isSafeName(name);
    }

    /** Hidden folders (.obsidian, .trash, .git) are never walked. */
    static boolean shouldWalkDirectory(String name) {
        return isSafeName(name) && !name.startsWith(".");
    }

    static boolean isSafeName(String name) {
        return name != null
                && !name.isEmpty()
                && !name.equals(".")
                && !name.equals("..")
                && name.indexOf('/') < 0
                && name.indexOf('\\') < 0
                && name.indexOf('\0') < 0;
    }

    static String childPath(String parent, String name) {
        return parent == null || parent.isEmpty() ? name : parent + "/" + name;
    }
}
