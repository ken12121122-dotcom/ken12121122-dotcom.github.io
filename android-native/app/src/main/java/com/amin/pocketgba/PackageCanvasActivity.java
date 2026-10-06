package com.amin.pocketgba;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PackageCanvas inside Amin Pocket GBA: loads the live GitHub Pages page (web
 * changes need no APK update) and exposes one read-only folder bridge.
 * Separate from AMIN WIKI / FOX KnowledgeProfile; it keeps its own folder list.
 */
public final class PackageCanvasActivity extends Activity {
    static final String BRIDGE_NAME = "AminPackageCanvasFiles";
    static final String GEN2_BRIDGE_NAME = "AminGen2";
    private static final String PREFS = "amin_packagecanvas_folders";
    private static final String KEY_FOLDERS = "folders";
    private static final int REQUEST_FOLDER = 5101;
    private static final int REQUEST_FILE_CHOOSER = 5102;

    private WebView webView;
    volatile String committedUrl = "";
    private ValueCallback<Uri[]> fileCallback;
    private final Map<String, Map<String, String>> documentIndex = new ConcurrentHashMap<>();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AminTheme.Palette palette = AminTheme.palette(this);
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        webView = new WebView(this);
        webView.setBackgroundColor(palette.background);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " AminPocketGBA/" + BuildConfig.VERSION_NAME);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (PackageCanvasFolderPolicy.isTrustedPage(uri.toString())) return false;
                if (!request.isForMainFrame()) return false;
                openExternally(uri);
                return true;
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                committedUrl = url == null ? "" : url;
            }

            @Override public void onPageFinished(WebView view, String url) {
                committedUrl = url == null ? "" : url;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                       FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQUEST_FILE_CHOOSER);
                    return true;
                } catch (Exception error) {
                    fileCallback = null;
                    Toast.makeText(PackageCanvasActivity.this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        webView.addJavascriptInterface(new FolderBridge(), BRIDGE_NAME);
        webView.addJavascriptInterface(new Gen2Bridge(), GEN2_BRIDGE_NAME);

        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        if (bundle == null) webView.loadUrl(PackageCanvasFolderPolicy.START_URL);
        else webView.restoreState(bundle);
    }

    String startUrl() {
        return PackageCanvasFolderPolicy.START_URL;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface(BRIDGE_NAME);
            webView.removeJavascriptInterface(GEN2_BRIDGE_NAME);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_CHOOSER) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        if (requestCode != REQUEST_FOLDER) return;
        JSONObject result = new JSONObject();
        try {
            Uri tree = data == null ? null : data.getData();
            if (resultCode != RESULT_OK || tree == null) {
                result.put("ok", false).put("cancelled", true);
            } else {
                getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                JSONObject folder = rememberFolder(tree);
                result.put("ok", true)
                        .put("folderId", folder.getString("folderId"))
                        .put("name", folder.getString("name"));
            }
        } catch (Exception error) {
            try {
                result = new JSONObject().put("ok", false).put("error", "無法取得資料夾權限：" + error.getMessage());
            } catch (Exception ignored) { }
        }
        deliverPickResult(result.toString());
    }

    private void deliverPickResult(String json) {
        WebView current = webView;
        if (current == null || !PackageCanvasFolderPolicy.isTrustedPage(committedUrl)) return;
        current.evaluateJavascript(
                "window.__pcGen2FolderPicked&&window.__pcGen2FolderPicked(" + JSONObject.quote(json) + ")", null);
    }

    private void openExternally(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "沒有可開啟此連結的 App", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- folder list (own store, not FOX KnowledgeProfile) ----------
    private synchronized JSONArray storedFolders() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            return new JSONArray(prefs.getString(KEY_FOLDERS, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private synchronized void saveFolders(JSONArray folders) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDERS, folders.toString()).apply();
    }

    private synchronized JSONObject rememberFolder(Uri tree) throws Exception {
        JSONArray folders = storedFolders();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject f = folders.getJSONObject(i);
            if (tree.toString().equals(f.optString("uri"))) {
                f.put("grantedAt", System.currentTimeMillis());
                saveFolders(folders);
                return f;
            }
        }
        JSONObject folder = new JSONObject()
                .put("folderId", UUID.randomUUID().toString())
                .put("uri", tree.toString())
                .put("name", folderLabel(tree))
                .put("grantedAt", System.currentTimeMillis());
        folders.put(folder);
        saveFolders(folders);
        return folder;
    }

    private JSONObject findFolder(String folderId) {
        JSONArray folders = storedFolders();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject f = folders.optJSONObject(i);
            if (f != null && f.optString("folderId").equals(folderId)) return f;
        }
        return null;
    }

    private boolean hasReadPermission(Uri tree) {
        for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (permission.isReadPermission() && permission.getUri().equals(tree)) return true;
        }
        return false;
    }

    private String folderLabel(Uri tree) {
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            String path = id.substring(id.lastIndexOf(':') + 1);
            String label = path.substring(path.lastIndexOf('/') + 1).trim();
            if (!label.isEmpty()) return label;
        } catch (Exception ignored) { }
        return "知識庫資料夾";
    }

    // ---------- SAF walking ----------
    private static final String[] CHILD_COLUMNS = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private void walk(Uri tree, String documentId, String relPath, int depth,
                      JSONArray files, JSONArray dirs, Map<String, String> index, boolean[] truncated) throws Exception {
        if (depth > PackageCanvasFolderPolicy.MAX_DEPTH) {
            truncated[0] = true;
            return;
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
        ContentResolver resolver = getContentResolver();
        List<String[]> subdirs = new ArrayList<>();
        try (Cursor cursor = resolver.query(children, CHILD_COLUMNS, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (PackageCanvasFolderPolicy.shouldWalkDirectory(name)) subdirs.add(new String[]{childId, name});
                } else if (PackageCanvasFolderPolicy.isMarkdownName(name)) {
                    if (files.length() >= PackageCanvasFolderPolicy.MAX_FILES) {
                        truncated[0] = true;
                        return;
                    }
                    String path = PackageCanvasFolderPolicy.childPath(relPath, name);
                    index.put(path, childId);
                    files.put(new JSONObject()
                            .put("path", path)
                            .put("size", cursor.isNull(3) ? 0 : cursor.getLong(3))
                            .put("modified", cursor.isNull(4) ? 0 : cursor.getLong(4)));
                }
            }
        }
        for (String[] dir : subdirs) {
            String path = PackageCanvasFolderPolicy.childPath(relPath, dir[1]);
            dirs.put(path);
            walk(tree, dir[0], path, depth + 1, files, dirs, index, truncated);
        }
    }

    private String readDocument(Uri tree, String documentId) throws Exception {
        Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, documentId);
        try (InputStream input = getContentResolver().openInputStream(doc)) {
            if (input == null) throw new IllegalStateException("無法開啟檔案");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > PackageCanvasFolderPolicy.MAX_FILE_BYTES) throw new IllegalStateException("檔案超過 2 MB");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String error(String message) {
        try {
            return new JSONObject().put("ok", false).put("error", message).toString();
        } catch (Exception ignored) {
            return "{\"ok\":false}";
        }
    }

    /**
     * GEN2 runs: the canvas can only ask the app to open a run in the approval
     * page or the voice screen. It gets no token and posts nothing; any decision
     * is made and confirmed in those native screens.
     */
    final class Gen2Bridge {
        @JavascriptInterface public boolean isAvailable() {
            return PackageCanvasFolderPolicy.isTrustedPage(committedUrl);
        }

        @JavascriptInterface public boolean openRun(int issue, String mode) {
            if (!PackageCanvasFolderPolicy.isTrustedPage(committedUrl)) return false;
            if (issue <= 0 || issue > 9_999_999) return false;
            Class<?> target = "voice".equals(mode) ? Gen2VoiceActivity.class : Gen2RunsActivity.class;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                startActivity(new Intent(PackageCanvasActivity.this, target).putExtra(Gen2RunsActivity.EXTRA_ISSUE, issue));
            });
            return true;
        }

        /** Opens the fox chat, optionally about one run (0 = just talk). Opening only; the page sends nothing. */
        @JavascriptInterface public boolean openChat(int issue) {
            if (!PackageCanvasFolderPolicy.isTrustedPage(committedUrl)) return false;
            if (issue < 0 || issue > 9_999_999) return false;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                startActivity(Gen2ChatActivity.intent(PackageCanvasActivity.this, issue));
            });
            return true;
        }
    }

    /** Read-only bridge. Every call re-checks that the committed page is PackageCanvas. */
    final class FolderBridge {
        private boolean trusted() {
            return PackageCanvasFolderPolicy.isTrustedPage(committedUrl);
        }

        @JavascriptInterface public boolean isAvailable() {
            return trusted();
        }

        @JavascriptInterface public String listFolders() {
            if (!trusted()) return error("untrusted page");
            try {
                JSONArray out = new JSONArray();
                JSONArray folders = storedFolders();
                for (int i = 0; i < folders.length(); i++) {
                    JSONObject f = folders.getJSONObject(i);
                    if (!hasReadPermission(Uri.parse(f.getString("uri")))) continue;
                    out.put(new JSONObject()
                            .put("folderId", f.getString("folderId"))
                            .put("name", f.optString("name"))
                            .put("grantedAt", f.optLong("grantedAt")));
                }
                return new JSONObject().put("ok", true).put("folders", out).toString();
            } catch (Exception e) {
                return error(e.getMessage());
            }
        }

        @JavascriptInterface public boolean pickFolder() {
            if (!trusted()) return false;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                try {
                    startActivityForResult(intent, REQUEST_FOLDER);
                } catch (ActivityNotFoundException e) {
                    deliverPickResult(error("此裝置沒有資料夾選擇器"));
                }
            });
            return true;
        }

        /** Removes the folder from PackageCanvas only; the OS grant may still be shared with other features. */
        @JavascriptInterface public boolean forgetFolder(String folderId) {
            if (!trusted()) return false;
            synchronized (PackageCanvasActivity.this) {
                JSONArray folders = storedFolders(), kept = new JSONArray();
                boolean removed = false;
                for (int i = 0; i < folders.length(); i++) {
                    JSONObject f = folders.optJSONObject(i);
                    if (f != null && f.optString("folderId").equals(folderId)) removed = true;
                    else if (f != null) kept.put(f);
                }
                saveFolders(kept);
                documentIndex.remove(folderId);
                return removed;
            }
        }

        @JavascriptInterface public String listMarkdown(String folderId) {
            if (!trusted()) return error("untrusted page");
            JSONObject folder = findFolder(folderId);
            if (folder == null) return error("找不到這個資料夾，請重新選擇");
            try {
                Uri tree = Uri.parse(folder.getString("uri"));
                if (!hasReadPermission(tree)) return error("資料夾授權已失效，請重新選擇資料夾");
                JSONArray files = new JSONArray(), dirs = new JSONArray();
                Map<String, String> index = new HashMap<>();
                boolean[] truncated = {false};
                walk(tree, DocumentsContract.getTreeDocumentId(tree), "", 0, files, dirs, index, truncated);
                documentIndex.put(folderId, index);
                return new JSONObject()
                        .put("ok", true)
                        .put("rootName", folder.optString("name"))
                        .put("files", files)
                        .put("dirs", dirs)
                        .put("truncated", truncated[0])
                        .toString();
            } catch (Exception e) {
                return error("讀取資料夾失敗：" + e.getMessage());
            }
        }

        @JavascriptInterface public String readText(String folderId, String path) {
            if (!trusted()) return error("untrusted page");
            JSONObject folder = findFolder(folderId);
            Map<String, String> index = documentIndex.get(folderId);
            if (folder == null || index == null) return error("請先列出資料夾內容");
            String documentId = index.get(path);
            if (documentId == null) return error("檔案不在資料夾清單中");
            try {
                Uri tree = Uri.parse(folder.getString("uri"));
                return new JSONObject().put("ok", true).put("text", readDocument(tree, documentId)).toString();
            } catch (Exception e) {
                return error("讀取檔案失敗：" + e.getMessage());
            }
        }
    }
}
