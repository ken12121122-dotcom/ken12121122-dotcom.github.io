package com.amin.pocketgba;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Collections;

/** Exact, APK-owned navigation. No command, filesystem or credential bridge. */
final class LivingLibraryPolicy {
    static final String HOST = "appassets.androidplatform.net";
    static final String HOME = "https://" + HOST + "/living-library/index.html";
    static final String REMOTE_HOST = "ken12121122-dotcom.github.io";
    static final String REMOTE_HOME = "https://" + REMOTE_HOST + "/living-library/live/index.html";
    static final Set<String> ASSETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "/living-library/index.html", "/living-library/public-catalog.js",
            "/living-library/gen2-editor.js", "/living-library/book-placement.js", "/living-library/render-capability.js",
            "/living-library/gpt-library-bridge.html", "/packagecanvas/gen2-source.js")));
    static final Map<String, String> ROUTES;
    static {
        Map<String, String> routes = new HashMap<>();
        routes.put("gba", "MainActivity");
        routes.put("canvas", "PackageCanvasActivity");
        routes.put("control", "UniversalControlSetupActivity");
        routes.put("api", "AminControlApiActivity");
        routes.put("keyboard", "PromptKeyboardSetupActivity");
        routes.put("graph", "WikiGraphActivity");
        routes.put("appearance", "AppearanceSettingsActivity");
        routes.put("fox", "FoxPetControlActivity");
        routes.put("voice", "VoiceCommandActivity");
        routes.put("brain", "BrainControlActivity");
        routes.put("permissions", "PermissionCenterActivity");
        routes.put("update", "UpdateHubActivity");
        routes.put("fox-knowledge", "com.fox.app.FoxKnowledgeActivity");
        routes.put("gen2-runs", "Gen2RunsActivity");
        routes.put("gen2-chat", "Gen2ChatActivity");
        routes.put("finance", "FinanceActivity");
        routes.put("voice-ball", "voice-ball");
        ROUTES = Collections.unmodifiableMap(routes);
    }
    static String asset(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getPort() != -1) return null;
            return ASSETS.contains(uri.getRawPath()) ? uri.getRawPath().substring(1) : null;
        } catch (IllegalArgumentException error) { return null; }
    }
    static boolean isRemoteHome(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !REMOTE_HOST.equals(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getPort() != -1
                    || uri.getRawFragment() != null
                    || !"/living-library/live/index.html".equals(uri.getRawPath())) return false;
            String query = uri.getRawQuery();
            return query == null || query.matches("v=[0-9]{1,20}");
        } catch (IllegalArgumentException error) { return false; }
    }
    static boolean isHome(String value) {
        return "living-library/index.html".equals(asset(value)) || isRemoteHome(value);
    }
    static String route(String value) {
        try {
            URI uri = URI.create(value);
            if (!"amin-library".equals(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getPort() != -1 || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) return null;
            return ROUTES.containsKey(uri.getHost()) ? uri.getHost() : null;
        } catch (IllegalArgumentException error) { return null; }
    }
}
