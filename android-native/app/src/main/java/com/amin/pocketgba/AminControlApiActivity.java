package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public final class AminControlApiActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4110;

    private AminControlApiConfig config;
    private Switch apiSwitch;
    private Switch lanSwitch;
    private Switch automationSwitch;
    private EditText portField;
    private EditText tokenField;
    private EditText allowlistField;
    private EditText rateLimitField;
    private TextView statusView;
    private TextView endpointView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = new AminControlApiConfig(this);
        buildUi();
        loadConfig();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xfff4f7f5);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(22), dp(20), dp(36));
        scroll.addView(content);

        content.addView(text("AMIN CONTROL API", 12f, true, 0xff19794b));
        TextView title = text("手機控制介面", 28f, true, 0xff16231b);
        title.setPadding(0, dp(5), 0, 0);
        content.addView(title);
        TextView intro = text(
                "預設只接受本機 localhost。LAN 模式必須由你主動開啟，並使用權杖與允許清單。",
                14f,
                false,
                0xff68766e
        );
        intro.setPadding(0, dp(8), 0, dp(16));
        content.addView(intro);

        statusView = text("狀態讀取中…", 15f, true, 0xff16231b);
        endpointView = text("", 13f, false, 0xff68766e);
        content.addView(card(statusView));
        content.addView(endpointView);

        apiSwitch = toggle("開啟 Amin Control API");
        lanSwitch = toggle("允許區域網路 LAN 連線");
        automationSwitch = toggle("允許 Broadcast／Intent／Deep Link 自動化");
        content.addView(apiSwitch);
        content.addView(lanSwitch);
        content.addView(automationSwitch);

        portField = field("連接埠", InputType.TYPE_CLASS_NUMBER, false);
        rateLimitField = field("每分鐘請求上限", InputType.TYPE_CLASS_NUMBER, false);
        tokenField = field("LAN 權杖", InputType.TYPE_CLASS_TEXT, false);
        allowlistField = field("LAN 允許清單（CIDR，以逗號分隔）", InputType.TYPE_CLASS_TEXT, true);
        content.addView(portField);
        content.addView(rateLimitField);
        content.addView(tokenField);

        LinearLayout tokenButtons = new LinearLayout(this);
        tokenButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button generate = button("產生新權杖");
        Button copy = button("複製權杖");
        generate.setOnClickListener(view -> tokenField.setText(config.ensureToken()));
        copy.setOnClickListener(view -> copyText("Amin API Token", tokenField.getText().toString()));
        tokenButtons.addView(generate, weight());
        tokenButtons.addView(copy, weight());
        content.addView(tokenButtons);
        content.addView(allowlistField);

        TextView warning = text(
                "LAN 模式會綁定 0.0.0.0。只有符合允許清單且帶有 Bearer Token 的裝置可以呼叫。",
                13f,
                false,
                0xff9a5b00
        );
        warning.setPadding(0, dp(8), 0, dp(12));
        content.addView(warning);

        Button saveStart = button("儲存並套用");
        Button stop = button("停止 API");
        Button copyCurl = button("複製 localhost curl 範例");
        saveStart.setOnClickListener(view -> saveAndApply());
        stop.setOnClickListener(view -> stopApi());
        copyCurl.setOnClickListener(view -> copyCurlExample());
        content.addView(saveStart);
        content.addView(stop);
        content.addView(copyCurl);

        TextView examples = text(
                "REST：GET /v1/actions、POST /v1/actions/{action}、GET /v1/events\n"
                        + "WebSocket：ws://127.0.0.1:8765/v1/events，可接收事件也可送出 action JSON。",
                13f,
                false,
                0xff68766e
        );
        examples.setPadding(0, dp(14), 0, 0);
        content.addView(examples);
        setContentView(scroll);
    }

    private void loadConfig() {
        apiSwitch.setChecked(config.isApiEnabled());
        lanSwitch.setChecked(config.isLanEnabled());
        automationSwitch.setChecked(config.isAutomationEnabled());
        portField.setText(String.valueOf(config.getPort()));
        rateLimitField.setText(String.valueOf(config.getRateLimitPerMinute()));
        tokenField.setText(config.getToken());
        allowlistField.setText(config.getAllowlist());
    }

    private void saveAndApply() {
        int port = parseInt(portField.getText().toString(), AminControlApiConfig.DEFAULT_PORT);
        int rateLimit = parseInt(rateLimitField.getText().toString(), AminControlApiConfig.DEFAULT_RATE_LIMIT);
        String token = tokenField.getText().toString().trim();
        if (lanSwitch.isChecked() && token.length() < 24) {
            token = config.ensureToken();
            tokenField.setText(token);
        }
        config.save(
                apiSwitch.isChecked(),
                lanSwitch.isChecked(),
                automationSwitch.isChecked(),
                port,
                rateLimit,
                token,
                allowlistField.getText().toString()
        );
        if (apiSwitch.isChecked()) {
            requestNotificationPermissionIfNeeded();
            AminControlApiService.reload(this);
            Toast.makeText(this, "API 設定已套用", Toast.LENGTH_SHORT).show();
        } else {
            AminControlApiService.stop(this);
            Toast.makeText(this, "API 已關閉", Toast.LENGTH_SHORT).show();
        }
        statusView.postDelayed(this::refreshStatus, 600L);
    }

    private void stopApi() {
        config.save(
                false,
                lanSwitch.isChecked(),
                automationSwitch.isChecked(),
                parseInt(portField.getText().toString(), AminControlApiConfig.DEFAULT_PORT),
                parseInt(rateLimitField.getText().toString(), AminControlApiConfig.DEFAULT_RATE_LIMIT),
                tokenField.getText().toString(),
                allowlistField.getText().toString()
        );
        apiSwitch.setChecked(false);
        AminControlApiService.stop(this);
        statusView.postDelayed(this::refreshStatus, 400L);
    }

    private void refreshStatus() {
        boolean running = AminControlApiService.isRunning();
        statusView.setText(running ? "● API 執行中" : "○ API 未執行");
        statusView.setTextColor(running ? 0xff19794b : 0xff9a5b00);
        String host = config.isLanEnabled() ? getLanAddress() : "127.0.0.1";
        endpointView.setText(
                "REST：http://" + host + ":" + config.getPort() + "/v1/status\n"
                        + "WebSocket：ws://" + host + ":" + config.getPort() + "/v1/events"
                        + (AminControlApiService.getLastError().isBlank()
                        ? ""
                        : "\n錯誤：" + AminControlApiService.getLastError())
        );
    }

    private void copyCurlExample() {
        String command = "curl http://127.0.0.1:" + config.getPort()
                + "/v1/actions/SYSTEM_HOME -X POST -H \"Content-Type: application/json\" -d \"{}\"";
        copyText("Amin curl", command);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private String getLanAddress() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // Network may change while this screen is rendering.
        }
        return "手機IP";
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "已複製", Toast.LENGTH_SHORT).show();
    }

    private Switch toggle(String label) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextSize(16f);
        view.setTextColor(0xff16231b);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private EditText field(String hint, int inputType, boolean multiline) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setInputType(inputType | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        view.setSingleLine(!multiline);
        view.setTextSize(15f);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15f);
        return button;
    }

    private LinearLayout card(View child) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundColor(Color.WHITE);
        card.addView(child);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);
        return card;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception error) {
            return fallback;
        }
    }
}
