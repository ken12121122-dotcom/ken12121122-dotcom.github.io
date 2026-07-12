package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class PermissionCenterActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 7201;

    private Switch notificationSwitch;
    private Switch installSwitch;
    private TextView notificationStatus;
    private TextView installStatus;
    private TextView batteryStatus;
    private TextView summaryView;
    private boolean updatingUi;
    private boolean continueRecommendedFlow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
    }

    private void buildUi() {
        int padding = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        TextView eyebrow = text("AMIN NATIVE SECURITY", 12f, true);
        content.addView(eyebrow, fullWidth());

        TextView title = text("權限控制中心", 26f, true);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = dp(6);
        content.addView(title, titleParams);

        TextView intro = text(
                "Android 不允許 App 靜默取得敏感權限。這裡會顯示目前狀態，並把你帶到正確的系統確認畫面。",
                14f,
                false
        );
        LinearLayout.LayoutParams introParams = fullWidth();
        introParams.topMargin = dp(10);
        content.addView(intro, introParams);

        Button recommended = button("啟用建議權限");
        recommended.setOnClickListener(view -> enableRecommendedPermissions());
        LinearLayout.LayoutParams recommendedParams = fullWidth();
        recommendedParams.topMargin = dp(18);
        content.addView(recommended, recommendedParams);

        summaryView = text("正在讀取權限狀態…", 13f, false);
        LinearLayout.LayoutParams summaryParams = fullWidth();
        summaryParams.topMargin = dp(10);
        content.addView(summaryView, summaryParams);

        notificationSwitch = addSwitchCard(
                content,
                "通知",
                "用於更新完成、備份提醒與未來的背景工作通知。Android 13 以上需要你確認。"
        );
        notificationStatus = addStatus(content);
        notificationSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (updatingUi) return;
            if (checked) requestNotifications();
            else openNotificationSettings();
        });

        installSwitch = addSwitchCard(
                content,
                "安裝 App 更新",
                "允許 Amin Pocket GBA 將已驗證簽章的 APK 交給 Android 安裝程式。"
        );
        installStatus = addStatus(content);
        installSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (updatingUi) return;
            openUnknownSourcesSettings();
        });

        addSectionTitle(content, "系統管理入口");

        Button appSettings = button("開啟 App 完整權限頁");
        appSettings.setOnClickListener(view -> openAppDetails());
        content.addView(appSettings, spaced());

        Button notificationSettings = button("開啟通知詳細設定");
        notificationSettings.setOnClickListener(view -> openNotificationSettings());
        content.addView(notificationSettings, spaced());

        Button batterySettings = button("開啟電池最佳化設定");
        batterySettings.setOnClickListener(view -> openBatterySettings());
        content.addView(batterySettings, spaced());

        batteryStatus = text("正在讀取電池限制…", 13f, false);
        content.addView(batteryStatus, statusParams());

        addSectionTitle(content, "已自動取得或不需敏感授權");
        addInfoCard(content, "網路與網路狀態", "安裝時自動授予，用於 Runtime、診斷與簽章更新清單。", "自動");
        addInfoCard(content, "震動回饋", "一般權限，不會讀取私人資料。", "自動");
        addInfoCard(content, "ROM 與備份檔案", "透過 Android 系統選檔器逐檔授權，不需讀取整台手機。", "安全選檔器");
        addInfoCard(content, "USB 控制器", "USB 裝置由 Android 在連接時逐台確認，不存在全域總開關。", "裝置級");
        addInfoCard(content, "藍牙手把", "已配對的遊戲控制器可直接輸入，不需要掃描附近裝置的敏感權限。", "不需額外授權");

        addSectionTitle(content, "刻意不索取");
        addWarningCard(content, "整機檔案管理", "不要求 MANAGE_EXTERNAL_STORAGE。它能讀取大量私人檔案，現有選檔器已足夠。 ");
        addWarningCard(content, "相機、麥克風、位置、聯絡人、電話與簡訊", "目前功能不需要，所以不會索取。未來新增真正需要的功能時，再逐項加入。 ");
        addWarningCard(content, "懸浮視窗與無障礙服務", "權限能力過大，目前沒有合理用途，因此不加入。 ");

        Button close = button("完成並返回");
        close.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams closeParams = fullWidth();
        closeParams.topMargin = dp(22);
        closeParams.bottomMargin = dp(30);
        content.addView(close, closeParams);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private Switch addSwitchCard(LinearLayout parent, String title, String description) {
        LinearLayout card = card();
        Switch control = new Switch(this);
        control.setText(title);
        control.setTextSize(18f);
        control.setTypeface(Typeface.DEFAULT_BOLD);
        control.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(control, fullWidth());

        TextView body = text(description, 13f, false);
        LinearLayout.LayoutParams bodyParams = fullWidth();
        bodyParams.topMargin = dp(7);
        card.addView(body, bodyParams);

        parent.addView(card, cardParams());
        return control;
    }

    private TextView addStatus(LinearLayout parent) {
        TextView status = text("讀取中…", 12f, false);
        parent.addView(status, statusParams());
        return status;
    }

    private void addInfoCard(LinearLayout parent, String title, String description, String badge) {
        LinearLayout card = card();
        TextView heading = text(title + "  ·  " + badge, 15f, true);
        card.addView(heading, fullWidth());
        TextView body = text(description, 13f, false);
        LinearLayout.LayoutParams bodyParams = fullWidth();
        bodyParams.topMargin = dp(6);
        card.addView(body, bodyParams);
        parent.addView(card, cardParams());
    }

    private void addWarningCard(LinearLayout parent, String title, String description) {
        addInfoCard(parent, title, description, "不要求");
    }

    private void addSectionTitle(LinearLayout parent, String value) {
        TextView heading = text(value, 17f, true);
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(24);
        parent.addView(heading, params);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundColor(0x14111111);
        return card;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    private TextView text(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setLineSpacing(0f, 1.3f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(18);
        return params;
    }

    private LinearLayout.LayoutParams statusParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(6);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void refreshStatuses() {
        boolean notifications = notificationsGranted();
        boolean install = installPermissionGranted();
        boolean unrestricted = batteryUnrestricted();

        updatingUi = true;
        notificationSwitch.setChecked(notifications);
        installSwitch.setChecked(install);
        updatingUi = false;

        notificationStatus.setText(notifications
                ? "已開啟。可由 Android 通知設定隨時關閉。"
                : "未開啟。切換開關後由 Android 顯示確認畫面。"
        );
        installStatus.setText(install
                ? "已允許此 App 提交 APK 安裝要求。每次安裝仍需你確認。"
                : "未允許。原生更新中心只能下載與驗證，不能進入安裝。"
        );
        batteryStatus.setText(unrestricted
                ? "電池狀態：此 App 目前不受電池最佳化限制。"
                : "電池狀態：目前受系統最佳化管理。遊戲前景運作不受影響。"
        );

        int enabled = (notifications ? 1 : 0) + (install ? 1 : 0);
        summaryView.setText("建議權限已開啟 " + enabled + " / 2。其餘功能使用一般權限或系統選檔器。 ");
    }

    private boolean notificationsGranted() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean installPermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls();
    }

    private boolean batteryUnrestricted() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void enableRecommendedPermissions() {
        continueRecommendedFlow = true;
        if (!notificationsGranted()) {
            requestNotifications();
            return;
        }
        continueRecommendedFlow = false;
        if (!installPermissionGranted()) openUnknownSourcesSettings();
        else Toast.makeText(this, "建議權限已全部開啟。", Toast.LENGTH_SHORT).show();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.POST_NOTIFICATIONS },
                    REQUEST_NOTIFICATIONS
            );
            return;
        }
        openNotificationSettings();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        refreshStatuses();
        if (continueRecommendedFlow) {
            continueRecommendedFlow = false;
            if (!installPermissionGranted()) openUnknownSourcesSettings();
        }
    }

    private void openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "此 Android 版本不需要個別安裝來源權限。", Toast.LENGTH_SHORT).show();
            return;
        }
        openIntent(new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())
        ));
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        openIntent(intent);
    }

    private void openBatterySettings() {
        openIntent(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    private void openAppDetails() {
        openIntent(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        ));
    }

    private void openIntent(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "此裝置沒有對應的系統設定頁。", Toast.LENGTH_LONG).show();
        }
    }
}
