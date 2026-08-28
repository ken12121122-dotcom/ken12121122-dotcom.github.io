package com.amin.pocketgba;

import android.content.Context;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Loads optional graph visualization overlays without changing Registry/runtime authority. */
final class GraphArchitectureVisibilityInjector {
    private static final String[] ASSETS = new String[] {
            "amin-wiki-graph/architecture-visibility.js",
            "amin-wiki-graph/capability-semantic-zoom.js"
    };

    private GraphArchitectureVisibilityInjector() { }

    static void inject(Context context, WebView webView) {
        if (context == null || webView == null) return;
        for (String asset : ASSETS) injectAsset(context, webView, asset);
    }

    private static void injectAsset(Context context, WebView webView, String asset) {
        try (InputStream input = context.getAssets().open(asset);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            webView.evaluateJavascript(output.toString("UTF-8"), null);
        } catch (Exception ignored) { }
    }
}
