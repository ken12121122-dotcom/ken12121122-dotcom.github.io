package com.amin.pocketgba;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final String APP_URL =
            "https://ken12121122-dotcom.github.io/amin-vault/gba.html?native=1&v=091";
    private static final String TRUSTED_HOST = "ken12121122-dotcom.github.io";
    private static final String NATIVE_CARTRIDGE_PATH = "/__amin_native__/cartridge";
    private static final String OFFLINE_URL = "file:///android_asset/bootstrap/index.html";
    private static final int FILE_CHOOSER_REQUEST = 4107;
    private static final int OFFLINE_CARTRIDGE_REQUEST = 4108;

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean pageReady;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private JSONObject lastNetworkPayload;
    private PendingCartridge pendingCartridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        handleIncomingIntent(getIntent());
        configureWebView();
        registerNetworkMonitor();
        hideSystemUi();
        webView.loadUrl(APP_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
        injectPendingCartridge();
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString()
                + " AminPocketGBA/" + BuildConfig.VERSION_NAME);

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        webView.setWebViewClient(new PocketWebViewClient());
        webView.setWebChromeClient(new PocketWebChromeClient());
    }

    private final class PocketWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            pageReady = false;
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageReady = true;
            progressBar.setVisibility(View.GONE);
            view.requestFocus();
            injectConnectedControllers();
            injectNativeCapabilities();
            injectNetworkState();
            injectPendingCartridge();
            hideSystemUi();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();

            if ("amin".equalsIgnoreCase(scheme)) {
                handleNativeAction(uri);
                return true;
            }

            String url = uri.toString();
            if ("about".equalsIgnoreCase(scheme)
                    || "blob".equalsIgnoreCase(scheme)
                    || ("file".equalsIgnoreCase(scheme) && url.startsWith(OFFLINE_URL))
                    || ("https".equalsIgnoreCase(scheme)
                    && TRUSTED_HOST.equalsIgnoreCase(uri.getHost()))) {
                return false;
            }

            if (!request.isForMainFrame()) {
                return false;
            }

            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(MainActivity.this, R.string.load_failed, Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && TRUSTED_HOST.equalsIgnoreCase(uri.getHost())
                    && NATIVE_CARTRIDGE_PATH.equals(uri.getPath())) {
                return servePendingCartridge(uri);
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            if (request.isForMainFrame() && !isOfflineBootstrap(request.getUrl())) {
                progressBar.setVisibility(View.GONE);
                showOfflineBootstrap(String.valueOf(error.getDescription()));
            }
        }

        @Override
        public void onReceivedHttpError(
                WebView view,
                WebResourceRequest request,
                WebResourceResponse errorResponse
        ) {
            if (request.isForMainFrame()
                    && errorResponse.getStatusCode() >= 400
                    && !isOfflineBootstrap(request.getUrl())) {
                progressBar.setVisibility(View.GONE);
                showOfflineBootstrap("HTTP " + errorResponse.getStatusCode());
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            root.removeView(view);
            view.destroy();
            recreate();
            return true;
        }
    }

    private final class PocketWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> callback,
                FileChooserParams fileChooserParams
        ) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            return launchCartridgePicker(FILE_CHOOSER_REQUEST, true);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            webView.setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            hideSystemUi();
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return super.onConsoleMessage(consoleMessage);
        }
    }

    private boolean launchCartridgePicker(int requestCode, boolean allowMultiple) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/octet-stream",
                "application/zip",
                "application/x-gba-rom"
        });

        try {
            startActivityForResult(intent, requestCode);
            return true;
        } catch (ActivityNotFoundException error) {
            if (requestCode == FILE_CHOOSER_REQUEST) {
                filePathCallback = null;
            }
            Toast.makeText(this, R.string.file_picker_failed, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            finishWebFileChooser(resultCode, data);
            return;
        }

        if (requestCode == OFFLINE_CARTRIDGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && stagePendingCartridge(uri, data.getFlags())) {
                Toast.makeText(this, "卡匣已準備，連線後會自動匯入。", Toast.LENGTH_LONG).show();
                injectPendingCartridge();
            }
        }
    }

    private void finishWebFileChooser(int resultCode, Intent data) {
        if (filePathCallback == null) {
            return;
        }

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                    Uri uri = data.getClipData().getItemAt(index).getUri();
                    uris.add(uri);
                    persistReadPermission(uri, data.getFlags());
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                uris.add(uri);
                persistReadPermission(uri, data.getFlags());
            }
            if (!uris.isEmpty()) {
                results = uris.toArray(new Uri[0]);
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    private void persistReadPermission(Uri uri, int intentFlags) {
        try {
            int flags = intentFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) {
                getContentResolver().takePersistableUriPermission(uri, flags);
            }
        } catch (SecurityException ignored) {
            // Some providers grant only temporary access. Immediate importing still works.
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        Uri uri = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null && !streams.isEmpty()) {
                uri = streams.get(0);
            }
        }

        if (uri != null) {
            stagePendingCartridge(uri, intent.getFlags());
        }
    }

    private boolean stagePendingCartridge(Uri uri, int intentFlags) {
        String name = queryDisplayName(uri);
        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = guessMimeType(name);
        }
        if (!isSupportedCartridge(name, mimeType)) {
            Toast.makeText(this, "目前只支援 .gba、.bin 與 .zip 卡匣。", Toast.LENGTH_LONG).show();
            return false;
        }

        persistReadPermission(uri, intentFlags);
        pendingCartridge = new PendingCartridge(
                UUID.randomUUID().toString(),
                uri,
                name,
                mimeType,
                querySize(uri)
        );
        return true;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[] { OpenableColumns.DISPLAY_NAME },
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String value = cursor.getString(column);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to the URI path.
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null || fallback.isBlank() ? "cartridge.gba" : fallback;
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[] { OpenableColumns.SIZE },
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (column >= 0 && !cursor.isNull(column)) {
                    return cursor.getLong(column);
                }
            }
        } catch (RuntimeException ignored) {
            // Unknown sizes are represented by -1.
        }
        return -1L;
    }

    private boolean isSupportedCartridge(String name, String mimeType) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".gba")
                || lowerName.endsWith(".bin")
                || lowerName.endsWith(".zip")
                || "application/zip".equalsIgnoreCase(mimeType)
                || "application/x-gba-rom".equalsIgnoreCase(mimeType)
                || "application/octet-stream".equalsIgnoreCase(mimeType);
    }

    private String guessMimeType(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    private WebResourceResponse servePendingCartridge(Uri requestUri) {
        PendingCartridge cartridge = pendingCartridge;
        String token = requestUri.getQueryParameter("token");
        if (cartridge == null || token == null || !token.equals(cartridge.token)) {
            return textResponse(404, "Not Found", "Native cartridge token is not available.");
        }

        try {
            InputStream stream = getContentResolver().openInputStream(cartridge.uri);
            if (stream == null) {
                return textResponse(404, "Not Found", "Native cartridge stream is unavailable.");
            }
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            headers.put("Access-Control-Allow-Origin", "https://" + TRUSTED_HOST);
            if (cartridge.size >= 0) {
                headers.put("Content-Length", String.valueOf(cartridge.size));
            }
            return new WebResourceResponse(
                    cartridge.mimeType,
                    null,
                    200,
                    "OK",
                    headers,
                    stream
            );
        } catch (Exception error) {
            return textResponse(500, "Read Failed", "Unable to read the selected cartridge.");
        }
    }

    private WebResourceResponse textResponse(int status, String reason, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse(
                "text/plain",
                "utf-8",
                status,
                reason,
                headers,
                new ByteArrayInputStream(bytes)
        );
    }

    private void handleNativeAction(Uri uri) {
        String action = uri.getHost();
        if ("retry".equalsIgnoreCase(action)) {
            webView.loadUrl(APP_URL + "&retry=" + System.currentTimeMillis());
            return;
        }
        if ("choose-cartridge".equalsIgnoreCase(action)) {
            launchCartridgePicker(OFFLINE_CARTRIDGE_REQUEST, false);
        }
    }

    private boolean isOfflineBootstrap(Uri uri) {
        return uri != null && uri.toString().startsWith(OFFLINE_URL);
    }

    private void showOfflineBootstrap(String reason) {
        String transport = lastNetworkPayload == null
                ? "unknown"
                : lastNetworkPayload.optString("transport", "unknown");
        String url = OFFLINE_URL
                + "?reason=" + Uri.encode(reason == null ? "無法載入線上 Runtime" : reason)
                + "&native=" + Uri.encode(BuildConfig.VERSION_NAME)
                + "&network=" + Uri.encode(transport);
        webView.loadUrl(url);
    }

    private void registerNetworkMonitor() {
        if (connectivityManager == null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                updateNetworkState();
            }

            @Override
            public void onLost(Network network) {
                updateNetworkState();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                updateNetworkState();
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {
            networkCallback = null;
        }
        updateNetworkState();
    }

    private void updateNetworkState() {
        if (connectivityManager == null) {
            return;
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = activeNetwork == null
                ? null
                : connectivityManager.getNetworkCapabilities(activeNetwork);

        JSONObject payload = new JSONObject();
        try {
            boolean connected = capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            boolean validated = capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            payload.put("connected", connected);
            payload.put("validated", validated);
            payload.put("transport", describeTransport(capabilities));
            payload.put("metered", connectivityManager.isActiveNetworkMetered());
            payload.put("downstreamKbps", capabilities == null
                    ? 0 : capabilities.getLinkDownstreamBandwidthKbps());
            payload.put("upstreamKbps", capabilities == null
                    ? 0 : capabilities.getLinkUpstreamBandwidthKbps());
            payload.put("timestamp", System.currentTimeMillis());
        } catch (JSONException ignored) {
            return;
        }
        lastNetworkPayload = payload;
        sendShellJs("receiveNetwork", payload);
    }

    private String describeTransport(NetworkCapabilities capabilities) {
        if (capabilities == null) return "offline";
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "vpn";
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) return "bluetooth";
        return "other";
    }

    private void injectNetworkState() {
        if (lastNetworkPayload == null) {
            updateNetworkState();
            return;
        }
        sendShellJs("receiveNetwork", lastNetworkPayload);
    }

    private void injectNativeCapabilities() {
        JSONObject payload = new JSONObject();
        JSONArray capabilities = new JSONArray();
        capabilities.put("native-gamepad");
        capabilities.put("native-file-picker");
        capabilities.put("network-status");
        capabilities.put("runtime-update-v1");
        capabilities.put("open-cartridge");
        capabilities.put("offline-bootstrap");
        capabilities.put("save-flush");

        try {
            payload.put("appId", BuildConfig.APPLICATION_ID);
            payload.put("nativeVersion", BuildConfig.VERSION_NAME);
            payload.put("nativeVersionCode", BuildConfig.VERSION_CODE);
            payload.put("androidRelease", Build.VERSION.RELEASE);
            payload.put("sdkInt", Build.VERSION.SDK_INT);
            payload.put("manufacturer", Build.MANUFACTURER);
            payload.put("deviceModel", Build.MODEL);
            payload.put("debug", BuildConfig.DEBUG);
            payload.put("capabilities", capabilities);
            PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
            if (webViewPackage != null) {
                payload.put("webViewPackage", webViewPackage.packageName);
                payload.put("webViewVersion", webViewPackage.versionName);
            }
        } catch (JSONException ignored) {
            return;
        }
        sendShellJs("receiveCapabilities", payload);
    }

    private void injectPendingCartridge() {
        PendingCartridge cartridge = pendingCartridge;
        if (cartridge == null) {
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("token", cartridge.token);
            payload.put("name", cartridge.name);
            payload.put("mimeType", cartridge.mimeType);
            payload.put("size", cartridge.size);
            payload.put("downloadUrl", "https://" + TRUSTED_HOST
                    + NATIVE_CARTRIDGE_PATH + "?token=" + Uri.encode(cartridge.token));
        } catch (JSONException ignored) {
            return;
        }
        sendShellJs("receiveCartridge", payload);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isControllerEvent(event)) {
            sendKeyToWeb(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            sendMotionToWeb(event);
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean isControllerEvent(KeyEvent event) {
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_DPAD)
                || isControllerKeyCode(event.getKeyCode());
    }

    private boolean isControllerKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_BUTTON_A && keyCode <= KeyEvent.KEYCODE_BUTTON_MODE) {
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_BUTTON_1 && keyCode <= KeyEvent.KEYCODE_BUTTON_16) {
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    private void sendKeyToWeb(KeyEvent event) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("action", event.getAction() == KeyEvent.ACTION_DOWN ? "down" : "up");
            payload.put("pressed", event.getAction() == KeyEvent.ACTION_DOWN);
            payload.put("keyCode", event.getKeyCode());
            payload.put("keyName", KeyEvent.keyCodeToString(event.getKeyCode()));
            payload.put("scanCode", event.getScanCode());
            payload.put("repeatCount", event.getRepeatCount());
            payload.put("deviceId", event.getDeviceId());
            payload.put("source", event.getSource());
            payload.put("eventTime", event.getEventTime());
            if (event.getDevice() != null) {
                payload.put("deviceName", event.getDevice().getName());
                payload.put("vendorId", event.getDevice().getVendorId());
                payload.put("productId", event.getDevice().getProductId());
            }
        } catch (JSONException ignored) {
            return;
        }
        sendInputJs("receiveKey", payload);
    }

    private void sendMotionToWeb(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) {
            return;
        }

        JSONObject axes = new JSONObject();
        try {
            putAxis(axes, "AXIS_X", centeredAxis(event, device, MotionEvent.AXIS_X));
            putAxis(axes, "AXIS_Y", centeredAxis(event, device, MotionEvent.AXIS_Y));
            putAxis(axes, "AXIS_Z", centeredAxis(event, device, MotionEvent.AXIS_Z));
            putAxis(axes, "AXIS_RZ", centeredAxis(event, device, MotionEvent.AXIS_RZ));
            putAxis(axes, "AXIS_HAT_X", centeredAxis(event, device, MotionEvent.AXIS_HAT_X));
            putAxis(axes, "AXIS_HAT_Y", centeredAxis(event, device, MotionEvent.AXIS_HAT_Y));
            putAxis(axes, "AXIS_LTRIGGER", centeredAxis(event, device, MotionEvent.AXIS_LTRIGGER));
            putAxis(axes, "AXIS_RTRIGGER", centeredAxis(event, device, MotionEvent.AXIS_RTRIGGER));
            putAxis(axes, "AXIS_BRAKE", centeredAxis(event, device, MotionEvent.AXIS_BRAKE));
            putAxis(axes, "AXIS_GAS", centeredAxis(event, device, MotionEvent.AXIS_GAS));

            JSONObject payload = new JSONObject();
            payload.put("deviceId", event.getDeviceId());
            payload.put("deviceName", device.getName());
            payload.put("vendorId", device.getVendorId());
            payload.put("productId", device.getProductId());
            payload.put("source", event.getSource());
            payload.put("eventTime", event.getEventTime());
            payload.put("axes", axes);
            sendInputJs("receiveMotion", payload);
        } catch (JSONException ignored) {
            // A malformed payload should never interrupt gameplay.
        }
    }

    private float centeredAxis(MotionEvent event, InputDevice device, int axis) {
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        float value = event.getAxisValue(axis);
        float flat = range == null ? 0.05f : Math.max(0.05f, range.getFlat());
        return Math.abs(value) <= flat ? 0f : value;
    }

    private void putAxis(JSONObject axes, String name, float value) throws JSONException {
        axes.put(name, Math.round(value * 100000f) / 100000f);
    }

    private void injectConnectedControllers() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null) {
                continue;
            }
            boolean controller = device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                    || device.supportsSource(InputDevice.SOURCE_JOYSTICK)
                    || device.supportsSource(InputDevice.SOURCE_DPAD);
            if (!controller) {
                continue;
            }

            JSONObject payload = new JSONObject();
            try {
                payload.put("deviceId", device.getId());
                payload.put("name", device.getName());
                payload.put("descriptor", device.getDescriptor());
                payload.put("vendorId", device.getVendorId());
                payload.put("productId", device.getProductId());
                payload.put("sources", device.getSources());
                payload.put("nativeBridge", true);
                payload.put("appVersion", BuildConfig.VERSION_NAME);
            } catch (JSONException ignored) {
                continue;
            }
            sendInputJs("receiveDevice", payload);
        }
    }

    private void sendInputJs(String method, JSONObject payload) {
        if (!pageReady || webView == null) {
            return;
        }
        String script = "(function(){if(window.AMIN_NATIVE_INPUT){window.AMIN_NATIVE_INPUT."
                + method + "(" + payload + ");}})();";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private void sendShellJs(String method, JSONObject payload) {
        if (!pageReady || webView == null) {
            return;
        }
        String script = "(function(){if(window.AMIN_NATIVE_SHELL){window.AMIN_NATIVE_SHELL."
                + method + "(" + payload + ");}})();";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        hideSystemUi();
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (webView == null) {
            super.onBackPressed();
            return;
        }

        webView.evaluateJavascript(
                "Boolean(document.body&&document.body.classList.contains('playing'))",
                result -> {
                    if ("true".equals(result)) {
                        webView.evaluateJavascript(
                                "document.getElementById('backToLibrary')&&document.getElementById('backToLibrary').click()",
                                null
                        );
                    } else if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        MainActivity.super.onBackPressed();
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        updateNetworkState();
        if (webView != null) {
            webView.onResume();
            webView.requestFocus();
            injectPendingCartridge();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.evaluateJavascript(
                    "window.AMIN_GBA_SAVE_GUARD&&window.AMIN_GBA_SAVE_GUARD.flush('android-pause')",
                    null
            );
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    protected void onDestroy() {
        if (networkCallback != null && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException ignored) {
                // It may already be unregistered after a process or network reset.
            }
            networkCallback = null;
        }
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private static final class PendingCartridge {
        final String token;
        final Uri uri;
        final String name;
        final String mimeType;
        final long size;

        PendingCartridge(String token, Uri uri, String name, String mimeType, long size) {
            this.token = token;
            this.uri = uri;
            this.name = name;
            this.mimeType = mimeType;
            this.size = size;
        }
    }
}
