package com.amin.pocketgba;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

    interface ActionTarget {
        boolean openGba();
        boolean openControllerSettings();
        boolean openOverlay();
        boolean closeOverlay();
        boolean openVoiceBubble();
        boolean closeVoiceBubble();
        boolean setControlMode(String mode);
        boolean executeSharedAction(AminAction action);
    }

    private AminActionDispatcher() {}

    public static DispatchResult dispatch(Activity activity, AminAction action) {
        if (activity == null || action == null) return DispatchResult.fail("缺少可執行的指令");
        ExecutionResult result = AminInputGateway.get(activity).executeBlocking(action, 6000L);
        return result.isSuccess()
                ? DispatchResult.ok(result.getMessage())
                : DispatchResult.fail(result.getMessage());
    }

    public static DispatchResult dispatch(Context context, AminAction action) {
        if (context == null || action == null) return DispatchResult.fail("缺少可執行的指令");
        return dispatch(action, new AndroidActionTarget(context));
    }

    static DispatchResult dispatch(AminAction action, ActionTarget target) {
        if (action == null || target == null) return DispatchResult.fail("缺少可執行的指令");
        switch (action.getAction()) {
            case "OPEN_GBA":
                return result(target.openGba(), "已開啟 GBA 遊戲庫", "無法開啟 GBA 遊戲庫");
            case "OPEN_CONTROLLER_SETTINGS":
                return result(target.openControllerSettings(), "已開啟控制器設定", "無法開啟控制器設定");
            case "OVERLAY_OPEN":
                return result(target.openOverlay(), "已開啟鍵盤浮動按鈕與控制盤", "請先啟用 Amin 全域控制服務");
            case "OVERLAY_CLOSE":
                return result(target.closeOverlay(), "已關閉控制盤與鍵盤浮動按鈕", "無法關閉鍵盤控制");
            case "VOICE_BUBBLE_OPEN":
                return result(target.openVoiceBubble(), "已開啟語音浮動按鈕", "請先啟用 Amin 全域控制服務");
            case "VOICE_BUBBLE_CLOSE":
                boolean closed = target instanceof AndroidActionTarget
                        && "floating_voice".equals(action.getSource())
                        ? ((AndroidActionTarget) target).closeVoiceBubbleAfterFeedback()
                        : target.closeVoiceBubble();
                return result(closed, "已關閉語音浮動按鈕", "無法關閉語音浮動按鈕");
            case "CONTROL_MODE_SET":
                String mode = action.getParameters().optString("mode", UniversalControlAccessibilityService.MODE_CURSOR);
                return result(
                        target.setControlMode(mode),
                        UniversalControlAccessibilityService.MODE_SCROLL.equals(mode)
                                ? "已切換捲動模式" : "已切換游標模式",
                        "無法切換控制模式"
                );
            case "SYSTEM_BACK":
                return result(target.executeSharedAction(action), "已執行全域返回", "請先啟用 Amin 全域控制服務");
            case "SYSTEM_HOME":
                return result(target.executeSharedAction(action), "已回到首頁", "無法回到首頁");
            case "CURSOR_TAP":
                return result(target.executeSharedAction(action), "已點擊游標位置", "請先啟用 Amin 全域控制服務");
            case "CURSOR_LONG_PRESS":
                return result(target.executeSharedAction(action), "已長按游標位置", "請先啟用 Amin 全域控制服務");
            case "DIRECTION_UP":
                return result(target.executeSharedAction(action), "已向上執行", "請先啟用 Amin 全域控制服務");
            case "DIRECTION_DOWN":
                return result(target.executeSharedAction(action), "已向下執行", "請先啟用 Amin 全域控制服務");
            case "DIRECTION_LEFT":
                return result(target.executeSharedAction(action), "已向左執行", "請先啟用 Amin 全域控制服務");
            case "DIRECTION_RIGHT":
                return result(target.executeSharedAction(action), "已向右執行", "請先啟用 Amin 全域控制服務");
            case "VOICE_STOP":
                return DispatchResult.ok("已停止聆聽");
            default:
                return DispatchResult.fail("尚未支援這個動作");
        }
    }

    private static DispatchResult result(boolean success, String successMessage, String failureMessage) {
        return success ? DispatchResult.ok(successMessage) : DispatchResult.fail(failureMessage);
    }

    private static final class AndroidActionTarget implements ActionTarget {
        private final Context context;

        private AndroidActionTarget(Context context) {
            this.context = context;
        }

        @Override
        public boolean openGba() {
            return start(new Intent(context, MainActivity.class));
        }

        @Override
        public boolean openControllerSettings() {
            return start(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://ken12121122-dotcom.github.io/amin-vault/gba-controller.html"
            )));
        }

        @Override
        public boolean openOverlay() {
            UniversalControlAccessibilityService.setKeyboardBubbleEnabled(context, true);
            if (!isAccessibilityEnabled(context)) {
                start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return false;
            }
            UniversalControlAccessibilityService.showKeyboardControls(context);
            return true;
        }

        @Override
        public boolean closeOverlay() {
            UniversalControlAccessibilityService.setKeyboardBubbleEnabled(context, false);
            return true;
        }

        @Override
        public boolean openVoiceBubble() {
            UniversalControlAccessibilityService.setVoiceBubbleEnabled(context, true);
            if (!isAccessibilityEnabled(context)) {
                start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return false;
            }
            return true;
        }

        @Override
        public boolean closeVoiceBubble() {
            UniversalControlAccessibilityService.setVoiceBubbleEnabled(context, false);
            return true;
        }

        private boolean closeVoiceBubbleAfterFeedback() {
            UniversalControlAccessibilityService.setVoiceBubbleEnabledAfterDelay(
                    context,
                    false,
                    1200L
            );
            return true;
        }

        @Override
        public boolean setControlMode(String mode) {
            UniversalControlAccessibilityService.setControlMode(context, mode);
            return true;
        }

        @Override
        public boolean executeSharedAction(AminAction action) {
            if (UniversalControlAccessibilityService.executeAminAction(action)) return true;
            if ("SYSTEM_HOME".equals(action.getAction())) {
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                return start(home);
            }
            return false;
        }

        private boolean start(Intent intent) {
            try {
                if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (RuntimeException error) {
                return false;
            }
        }
    }

    static boolean isAccessibilityEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return enabled != null && enabled.toLowerCase().contains(context.getPackageName().toLowerCase());
    }
}
