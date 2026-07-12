package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class PermissionCenterActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 7201;
    private static final int COLOR_BG = 0xfff4f7f5;
    private static final int COLOR_SURFACE = 0xffffffff;
    private static final int COLOR_SURFACE_SOFT = 0xffeaf3ee;
    private static final int COLOR_TEXT = 0xff16231b;
    private static final int COLOR_MUTED = 0xff68766e;
    private static final int COLOR_ACCENT = 0xff19794b;
    private static final int COLOR_BORDER = 0xffd9e4de;
    private static final int COLOR_WARNING = 0xff9a5b00;

    private Button notificationButton;
    private Button installButton;
    private Button safetyToggle;
    private TextView notificationStatus;
    private TextView installStatus;
    private TextView batteryStatus;
    private TextView summaryView;
    private LinearLayout safetyDetails;
    private boolean continueRecommendedFlow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(36));
        content.setBackgroundColor(COLOR_BG);
        scroll.addView(content);

        Button back = textButton("← 返回控制台");
        back.setOnClickListener(view -> finish());
        content.addView(back, wrapContent());

        TextView eyebrow = text("PRIVACY & DEVICE", 12f, true, COLOR_ACCENT);
        LinearLayout.LayoutParams eyebrowParams = fullWidth();
        eyebrowParams.topMargin = dp(12);
        content.addView(eyebrow, eyebrowParams);

        TextView title = text("權限與裝置", 28f, true, COLOR_TEXT);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = dp(4);
        content.addView(title, titleParams);

        TextView intro = text(
                "App 不會偷偷取得敏感權限。需要 Android 確認的項目，都會明確帶你到對應的系統畫面。",
                14f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams introParams = fullWidth();
        introParams.topMargin = dp(8);
        content.addView(intro, introParams);

        LinearLayout summaryCard = surfaceCard(COLOR_SURFACE_SOFT);
        TextView summaryTitle = text("建議設定", 16f, true, COLOR_TEXT);
        summaryCard.addView(summaryTitle, fullWidth());
        summaryView = text("正在讀取狀態…", 13f, false, COLOR_MUTED);
        LinearLayout.LayoutParams summaryTextParams = fullWidth();
        summaryTextParams.topMargin = dp(5);
        summaryCard.addView(summaryView, summaryTextParams);
        Button recommended = primaryButton("依序處理建議設定");
        recommended.setOnClickListener(view -> enableRecommendedPermissions());
        LinearLayout.LayoutParams recommendedParams = fullWidth();
        recommendedParams.topMargin = dp(12);
        summaryCard.addView(recommended, recommendedParams);
        content.addView(summaryCard, sectionCardParams());

        addSectionTitle(content, "需要你確認");

        PermissionCard notificationCard = addPermissionCard(
                content,
                "🔔",
                "通知",
                "用於更新完成、備份提醒與未來背景工作通知。",
                "管理通知"
        );
        notificationStatus = notificationCard.status;
        notificationButton = notificationCard.action;
        notificationButton.setOnClickListener(view -> requestNotifications());

        PermissionCard installCard = addPermissionCard(
                content,
                "📦",
                "安裝 App 更新",
                "允許已驗證的 APK 交給 Android 安裝器。每次安裝仍需你確認。",
                "管理安裝來源"
        );
        installStatus = installCard.status;
        installButton = installCard.action;
        installButton.setOnClickListener(view -> openUnknownSourcesSettings());

        addSectionTitle(content, "系統管理");
        content.addView(actionRow(
                "⚙",
                "App 完整設定",
                "查看 Android 提供的所有 App 選項",
                this::openAppDetails
        ), cardParams());
        content.addView(actionRow(
                "🔋",
                "電池管理",
                "調整背景限制與最佳化設定",
                this::openBatterySettings
        ), cardParams());

        batteryStatus = text("正在讀取電池限制…", 12f, false, COLOR_MUTED);
        LinearLayout.LayoutParams batteryParams = fullWidth();
        batteryParams.topMargin = dp(8);
        content.addView(batteryStatus, batteryParams);

        addSectionTitle(content, "安全設計");
        safetyToggle = secondaryButton("顯示安全設計說明");
        safetyToggle.setOnClickListener(view -> toggleSafetyDetails());
        content.addView(safetyToggle, cardParams());

        safetyDetails = surfaceCard(COLOR_SURFACE);
        safetyDetails.setVisibility(View.GONE);
        addInfo(safetyDetails, "ROM 與備份檔", "使用 Android 系統選檔器逐檔授權，不讀取整台手機。", "安全選檔器");
        addInfo(safetyDetails, "USB／藍牙手把", "已配對或連接的遊戲控制器不需要全域位置掃描權限。", "裝置級");
        addInfo(safetyDetails, "刻意不索取", "不要求整機檔案、相機、麥克風、位置、聯絡人、電話、簡訊、懸浮窗或無障礙服務。", "最小權限");
        content.addView(safetyDetails, cardParams());

        TextView footer = text(
                "權限狀態返回 App 後會自動刷新。",
                12f,
                false,
                COLOR_WARNING
        );
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = fullWidth();
        footerParams.topMargin = dp(24);
        content.addView(footer, footerParams);

        setContentView(scroll);
    }

    private PermissionCard addPermissionCard(
            LinearLayout parent,
            String icon,
            String title,
            String description,
            String buttonLabel
    ) {
        LinearLayout card = surfaceCard(COLOR_SURFACE);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, fullWidth());

        TextView iconView = text(icon, 23f, false, COLOR_ACCENT);
        iconView.setGravity(Gravity.CENTER);
        header.addView(iconView, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(10);
        header.addView(copy, copyParams);
        copy.addView(text(title, 17f, true, COLOR_TEXT), fullWidth());
        TextView descriptionView = text(description, 13f, false, COLOR_MUTED);
        LinearLayout.LayoutParams descriptionParams = fullWidth();
        descriptionParams.topMargin = dp(4);
        copy.addView(descriptionView, descriptionParams);

        TextView status = text("讀取中…", 12f, false, COLOR_MUTED);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(10);
        card.addView(status, statusParams);

        Button action = secondaryButton(buttonLabel);
        LinearLayout.LayoutParams actionParams = fullWidth();
        actionParams.topMargin = dp(10);
        card.addView(action, actionParams);

        parent.addView(card, cardParams());
        return new PermissionCard(status, action);
    }

    private LinearLayout actionRow(String icon, String title, String description, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(14), dp(12), dp(14));
        row.setBackground(roundedBackground(COLOR_SURFACE, 18, COLOR_BORDER, 1));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());

        TextView iconView = text(icon, 21f, false, COLOR_ACCENT);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);
        copy.addView(text(title, 15f, true, COLOR_TEXT), fullWidth());
        TextView body = text(description, 12f, false, COLOR_MUTED);
        LinearLayout.LayoutParams bodyParams = fullWidth();
        bodyParams.topMargin = dp(3);
        copy.addView(body, bodyParams);

        row.addView(text("›", 23f, false, COLOR_ACCENT), wrapContent());
        return row;
    }

    private void addInfo(LinearLayout parent, String title, String description, String badge) {
        TextView heading = text(title + " · " + badge, 14f, true, COLOR_TEXT);
        LinearLayout.LayoutParams headingParams = fullWidth();
        headingParams.topMargin = parent.getChildCount() == 0 ? 0 : dp(14);
        parent.addView(heading, headingParams);
        TextView body = text(description, 12f, false, COLOR_MUTED);
        LinearLayout.LayoutParams bodyParams = fullWidth();
        bodyParams.topMargin = dp(4);
        parent.addView(body, bodyParams);
    }

    private void toggleSafetyDetails() {
        boolean show = safetyDetails.getVisibility() != View.VISIBLE;
        safetyDetails.setVisibility(show ? View.VISIBLE : View.GONE);
        safetyToggle.setText(show ? "隱藏安全設計說明" : "顯示安全設計說明");
    }

    private void refreshStatuses() {
        boolean notifications = notificationsGranted();
        boolean install = installPermissionGranted();
        boolean unrestricted = batteryUnrestricted();

        notificationStatus.setText(notifications
                ? "狀態：已開啟"
                : "狀態：未開啟，點下方按鈕由 Android 確認"
        );
        notificationButton.setText(notifications ? "管理通知設定" : "開啟通知");

        installStatus.setText(install
                ? "狀態：已允許提交 APK，安裝時仍會再次確認"
                : "狀態：未允許，更新中心目前只能檢查與驗證"
        );
        installButton.setText(install ? "管理安裝來源" : "允許安裝更新");

        batteryStatus.setText(unrestricted
                ? "電池狀態：不受最佳化限制"
                : "電池狀態：由系統最佳化管理，前景遊戲不受影響"
        );

        int enabled = (notifications ? 1 : 0) + (install ? 1 : 0);
        summaryView.setText("建議設定已完成 " + enabled + " / 2。其他功能使用一般權限或系統選檔器。");
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
        else Toast.makeText(this, "建議設定已全部完成。", Toast.LENGTH_SHORT).show();
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

    private void addSectionTitle(LinearLayout parent, String value) {
        TextView heading = text(value, 18f, true, COLOR_TEXT);
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(26);
        parent.addView(heading, params);
    }

    private LinearLayout surfaceCard(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBackground(color, 18, COLOR_BORDER, 1));
        card.setElevation(dp(1));
        return card;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(50));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_ACCENT));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_ACCENT);
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_SURFACE_SOFT));
        return button;
    }

    private Button textButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_ACCENT);
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, dp(5), dp(10), dp(5));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        return button;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.25f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable roundedBackground(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams sectionCardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(20);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class PermissionCard {
        final TextView status;
        final Button action;

        PermissionCard(TextView status, Button action) {
            this.status = status;
            this.action = action;
        }
    }
}
