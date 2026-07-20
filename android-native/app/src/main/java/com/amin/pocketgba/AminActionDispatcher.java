package com.amin.pocketgba;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class AminActionDispatcher {
    public static final class DispatchResult {
        private final boolean success;
        private final String message;

        private DispatchResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }

        public static DispatchResult ok(String message) { return new DispatchResult(true, message); }
        public static DispatchResult fail(String message) { return new DispatchResult(false, message); }
    }

    private AminActionDispatcher() {}

    public static DispatchResult dispatch(Activity activity, AminAction action) {
        if (activity == null || action == null) return DispatchResult.fail("缺少可執行的指令");

        switch (action.getAction()) {
            case "OPEN_GBA":
                activity.startActivity(new Intent(activity, MainActivity.class));
                return DispatchResult.ok("已開啟 GBA 遊戲庫");
            case "OPEN_CONTROLLER_SETTINGS":
                Intent controller = new Intent(Intent.ACTION_VIEW);
                controller.setData(android.net.Uri.parse(
                        "https://ken12121122-dotcom.github.io/amin-vault/gba-controller.html"
                ));
                activity.startActivity(controller);
                return DispatchResult.ok("已開啟控制器設定");
            case "OVERLAY_OPEN":
                UniversalControlAccessibilityService.setOverlayEnabled(activity, true);
                if (!isAccessibilityEnabled(activity)) {
                    activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    return DispatchResult.fail("請先啟用 Amin 全域控制服務");
                }
                return DispatchResult.ok("已開啟控制盤");
            case "OVERLAY_CLOSE":
                UniversalControlAccessibilityService.setOverlayEnabled(activity, false);
                return DispatchResult.ok("已關閉控制盤");
            case "CONTROL_MODE_SET":
                String mode = action.getParameters().optString("mode", "cursor");
                UniversalControlAccessibilityService.setControlMode(activity, mode);
                return DispatchResult.ok("scroll".equals(mode) ? "已切換捲動模式" : "已切換游標模式");
            case "SYSTEM_BACK":
                activity.onBackPressed();
                return DispatchResult.ok("已返回");
            case "SYSTEM_HOME":
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(home);
                return DispatchResult.ok("已回到首頁");
            case "VOICE_STOP":
                return DispatchResult.ok("已停止聆聽");
            case "CURSOR_TAP":
            case "CURSOR_LONG_PRESS":
            case "DIRECTION_UP":
            case "DIRECTION_DOWN":
            case "DIRECTION_LEFT":
            case "DIRECTION_RIGHT":
                return DispatchResult.fail("此動作等待全域控制服務接入共用 Action Core");
            default:
                return DispatchResult.fail("尚未支援這個動作");
        }
    }

    private static boolean isAccessibilityEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return enabled != null && enabled.toLowerCase().contains(context.getPackageName().toLowerCase());
    }
}
