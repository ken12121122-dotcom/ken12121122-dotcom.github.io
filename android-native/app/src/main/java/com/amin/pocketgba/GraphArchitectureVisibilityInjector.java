package com.amin.pocketgba;

import android.content.Context;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Loads the optional Steps 5-8 architecture visibility overlay without changing Registry/runtime authority. */
final class GraphArchitectureVisibilityInjector {
    private static final String ASSET = "amin-wiki-graph/architecture-visibility.js";

    private GraphArchitectureVisibilityInjector() { }

    static void inject(Context context, WebView webView) {
        if (context == null || webView == null) return;
        try (InputStream input = context.getAssets().open(ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            webView.evaluateJavascript(output.toString("UTF-8"), null);
        } catch (Exception ignored) { }
    }
}
