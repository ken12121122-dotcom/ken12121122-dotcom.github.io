from pathlib import Path

SERVICE_PATH = Path(
    "android-native/app/src/main/java/com/amin/pocketgba/"
    "UniversalControlAccessibilityService.java"
)
CONTROLLER_PATH = Path(
    "android-native/app/src/main/java/com/amin/pocketgba/FloatingVoiceController.java"
)


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


def patch_service() -> None:
    source = SERVICE_PATH.read_text(encoding="utf-8")
    if "KEY_KEYBOARD_BUBBLE_ENABLED" in source:
        return

    source = replace_once(
        source,
        '    private static final String KEY_OVERLAY_ENABLED = "overlay_enabled";\n',
        '    private static final String KEY_OVERLAY_ENABLED_LEGACY = "overlay_enabled";\n'
        '    private static final String KEY_KEYBOARD_BUBBLE_ENABLED = "keyboard_bubble_enabled";\n'
        '    private static final String KEY_VOICE_BUBBLE_ENABLED = "voice_bubble_enabled";\n',
        "preference keys",
    )

    old_api = '''    public static boolean isOverlayEnabled(Context context) {
        return preferences(context).getBoolean(KEY_OVERLAY_ENABLED, true);
    }

    public static void setOverlayEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply();
        UniversalControlAccessibilityService instance = activeInstance;
        if (instance != null) {
            instance.mainHandler.post(() -> instance.applyOverlayEnabled(enabled));
        }
    }
'''
    new_api = '''    private static boolean legacyOverlayDefault(Context context) {
        return preferences(context).getBoolean(KEY_OVERLAY_ENABLED_LEGACY, true);
    }

    public static boolean isKeyboardBubbleEnabled(Context context) {
        return preferences(context).getBoolean(
                KEY_KEYBOARD_BUBBLE_ENABLED,
                legacyOverlayDefault(context)
        );
    }

    public static void setKeyboardBubbleEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_KEYBOARD_BUBBLE_ENABLED, enabled).apply();
        UniversalControlAccessibilityService instance = activeInstance;
        if (instance != null) {
            instance.mainHandler.post(() -> instance.applyKeyboardBubbleEnabled(enabled));
        }
    }

    public static boolean isVoiceBubbleEnabled(Context context) {
        return preferences(context).getBoolean(
                KEY_VOICE_BUBBLE_ENABLED,
                legacyOverlayDefault(context)
        );
    }

    public static void setVoiceBubbleEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_VOICE_BUBBLE_ENABLED, enabled).apply();
        UniversalControlAccessibilityService instance = activeInstance;
        if (instance != null) {
            instance.mainHandler.post(() -> instance.applyVoiceBubbleEnabled(enabled));
        }
    }

    @Deprecated
    public static boolean isOverlayEnabled(Context context) {
        return isKeyboardBubbleEnabled(context);
    }

    @Deprecated
    public static void setOverlayEnabled(Context context, boolean enabled) {
        setKeyboardBubbleEnabled(context, enabled);
    }

    public static boolean showKeyboardControls(Context context) {
        preferences(context).edit().putBoolean(KEY_KEYBOARD_BUBBLE_ENABLED, true).apply();
        UniversalControlAccessibilityService instance = activeInstance;
        if (instance == null) return false;
        instance.mainHandler.post(() -> {
            instance.applyKeyboardBubbleEnabled(true);
            instance.showControls();
        });
        return true;
    }
'''
    source = replace_once(source, old_api, new_api, "public floating preference api")

    source = replace_once(
        source,
        "        applyOverlayEnabled(isOverlayEnabled(this));\n",
        "        applyKeyboardBubbleEnabled(isKeyboardBubbleEnabled(this));\n"
        "        applyVoiceBubbleEnabled(isVoiceBubbleEnabled(this));\n",
        "service startup",
    )

    old_apply = '''    private void applyOverlayEnabled(boolean enabled) {
        if (!enabled) {
            if (floatingVoiceController != null) {
                floatingVoiceController.hide();
            }
            removeOverlays();
            return;
        }
        showBubble();
        if (floatingVoiceController == null && windowManager != null) {
            floatingVoiceController = new FloatingVoiceController(this, windowManager);
        }
        if (floatingVoiceController != null) {
            floatingVoiceController.show();
        }
        hideControls();
    }
'''
    new_apply = '''    private void applyKeyboardBubbleEnabled(boolean enabled) {
        if (!enabled) {
            hideControls();
            removeOverlays();
            return;
        }
        showBubble();
        hideControls();
    }

    private void applyVoiceBubbleEnabled(boolean enabled) {
        if (floatingVoiceController == null && windowManager != null) {
            floatingVoiceController = new FloatingVoiceController(this, windowManager);
        }
        if (floatingVoiceController == null) return;
        if (enabled) {
            floatingVoiceController.show();
        } else {
            floatingVoiceController.hide();
        }
    }
'''
    source = replace_once(source, old_apply, new_apply, "separate overlay lifecycle")

    source = replace_once(
        source,
        '''        if (!isOverlayEnabled(this)) {
            preferences(this).edit().putBoolean(KEY_OVERLAY_ENABLED, true).apply();
        }
''',
        '''        if (!isKeyboardBubbleEnabled(this)) {
            preferences(this).edit().putBoolean(KEY_KEYBOARD_BUBBLE_ENABLED, true).apply();
            applyKeyboardBubbleEnabled(true);
        }
''',
        "external control enable",
    )

    source = replace_once(
        source,
        "        if (!isOverlayEnabled(this)) {\n            return;\n        }\n",
        "        if (!isKeyboardBubbleEnabled(this)) {\n            return;\n        }\n",
        "show controls guard",
    )

    old_voice_bridge = '''    boolean showControlsFromVoice() {
        showControls();
        return controlsVisible;
    }

    boolean hideControlsFromVoice() {
        hideControls();
        return !controlsVisible;
    }
'''
    new_voice_bridge = '''    boolean showControlsFromVoice() {
        preferences(this).edit().putBoolean(KEY_KEYBOARD_BUBBLE_ENABLED, true).apply();
        applyKeyboardBubbleEnabled(true);
        showControls();
        return bubbleOverlay != null && controlsVisible;
    }

    boolean hideControlsFromVoice() {
        preferences(this).edit().putBoolean(KEY_KEYBOARD_BUBBLE_ENABLED, false).apply();
        applyKeyboardBubbleEnabled(false);
        return bubbleOverlay == null && !controlsVisible;
    }

    boolean showVoiceBubbleFromVoice() {
        preferences(this).edit().putBoolean(KEY_VOICE_BUBBLE_ENABLED, true).apply();
        applyVoiceBubbleEnabled(true);
        return floatingVoiceController != null && floatingVoiceController.isVisible();
    }

    boolean hideVoiceBubbleFromVoice() {
        preferences(this).edit().putBoolean(KEY_VOICE_BUBBLE_ENABLED, false).apply();
        mainHandler.postDelayed(() -> applyVoiceBubbleEnabled(false), 850L);
        return true;
    }
'''
    source = replace_once(source, old_voice_bridge, new_voice_bridge, "voice control bridges")
    SERVICE_PATH.write_text(source, encoding="utf-8")


def patch_controller() -> None:
    source = CONTROLLER_PATH.read_text(encoding="utf-8")
    if "case \"VOICE_BUBBLE_CLOSE\"" in source:
        return

    old_show = '''    void show() {
        if (windowManager == null || bubble != null) return;
        refreshScreenBounds();
        createStatusPanel();
        createVoiceBubble();
    }
'''
    new_show = '''    void show() {
        if (windowManager == null || bubble != null) return;
        refreshScreenBounds();
        createStatusPanel();
        createVoiceBubble();
    }

    boolean isVisible() {
        return bubble != null;
    }
'''
    source = replace_once(source, old_show, new_show, "controller visibility")

    old_cases = '''            case "OVERLAY_OPEN":
                return service.showControlsFromVoice()
                        ? ExecutionResult.ok("已開啟鍵盤控制盤")
                        : ExecutionResult.fail("無法開啟鍵盤控制盤");
            case "OVERLAY_CLOSE":
                return service.hideControlsFromVoice()
                        ? ExecutionResult.ok("已收起鍵盤控制盤")
                        : ExecutionResult.fail("無法收起鍵盤控制盤");
            case "CONTROL_MODE_SET":
'''
    new_cases = '''            case "OVERLAY_OPEN":
                return service.showControlsFromVoice()
                        ? ExecutionResult.ok("已開啟鍵盤浮動按鈕與控制盤")
                        : ExecutionResult.fail("無法開啟鍵盤控制");
            case "OVERLAY_CLOSE":
                return service.hideControlsFromVoice()
                        ? ExecutionResult.ok("已關閉控制盤與鍵盤浮動按鈕")
                        : ExecutionResult.fail("無法關閉鍵盤控制");
            case "VOICE_BUBBLE_OPEN":
                return service.showVoiceBubbleFromVoice()
                        ? ExecutionResult.ok("已開啟語音浮動按鈕")
                        : ExecutionResult.fail("無法開啟語音浮動按鈕");
            case "VOICE_BUBBLE_CLOSE":
                return service.hideVoiceBubbleFromVoice()
                        ? ExecutionResult.ok("已關閉語音浮動按鈕")
                        : ExecutionResult.fail("無法關閉語音浮動按鈕");
            case "CONTROL_MODE_SET":
'''
    source = replace_once(source, old_cases, new_cases, "controller voice cases")
    CONTROLLER_PATH.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    patch_service()
    patch_controller()
