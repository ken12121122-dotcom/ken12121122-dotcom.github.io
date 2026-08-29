package com.amin.pocketgba;

import android.content.Context;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Injects non-rendering graph support only.
 *
 * There is exactly one visual layout authority: amin-wiki-graph/index.html.
 * Scanner/source tooling may review evidence but must not create another canvas or layout engine.
 */
final class GraphArchitectureVisibilityInjector {
    private static final String[] ASSETS = new String[] {
            "amin-wiki-graph/source-review.js"
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
