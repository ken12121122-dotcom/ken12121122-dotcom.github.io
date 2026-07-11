package com.amin.pocketgba;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String APP_URL =
            "https://ken12121122-dotcom.github.io/amin-vault/gba.html?native=1&v=090";
    private static final String TRUSTED_HOST = "ken12121122-dotcom.github.io";
    private static final int FILE_CHOOSER_REQUEST = 4107;

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean pageReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        configureWebView();
        hideSystemUi();
        webView.loadUrl(APP_URL);
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " AminPocketGBA/0.9.0");

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
            hideSystemUi();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("about".equalsIgnoreCase(scheme)
                    || "blob".equalsIgnoreCase(scheme)
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
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            if (request.isForMainFrame()) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, R.string.load_failed, Toast.LENGTH_LONG).show();
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

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                    "application/octet-stream",
                    "application/zip",
                    "application/x-gba-rom"
            });

            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException error) {
                filePathCallback = null;
                Toast.makeText(MainActivity.this, R.string.file_picker_failed, Toast.LENGTH_LONG).show();
                return false;
            }
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) {
            return;
        }

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                    Uri uri = data.getClipData().getItemAt(index).getUri();
                    uris.add(uri);
                    persistReadPermission(data, uri);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                uris.add(uri);
                persistReadPermission(data, uri);
            }
            if (!uris.isEmpty()) {
                results = uris.toArray(new Uri[0]);
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    private void persistReadPermission(Intent data, Uri uri) {
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
            // The selected provider may not offer persistable access. The WebView can still read it now.
        }
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
        sendJs("receiveKey", payload);
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
            sendJs("receiveMotion", payload);
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
            sendJs("receiveDevice", payload);
        }
    }

    private void sendJs(String method, JSONObject payload) {
        if (!pageReady || webView == null) {
            return;
        }
        String script = "(function(){if(window.AMIN_NATIVE_INPUT){window.AMIN_NATIVE_INPUT."
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
        if (webView != null) {
            webView.onResume();
            webView.requestFocus();
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
}
