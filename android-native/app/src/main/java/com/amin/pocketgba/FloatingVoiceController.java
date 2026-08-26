package com.amin.pocketgba;

import android.content.Intent;
import android.view.WindowManager;

/**
 * Compatibility entry retained for the existing voice-bubble setting.
 * The primary voice-chat experience now lives in VoiceOrbHomeActivity.
 */
final class FloatingVoiceController {
    private final UniversalControlAccessibilityService service;
    private boolean launchRequested;

    FloatingVoiceController(UniversalControlAccessibilityService service, WindowManager windowManager) {
        this.service = service;
    }

    void show() {
        if (launchRequested) return;
        launchRequested = true;
        Intent intent = new Intent(service, VoiceOrbHomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            service.startActivity(intent);
        } finally {
            service.getSharedPreferences("amin_universal_control", UniversalControlAccessibilityService.MODE_PRIVATE)
                    .edit().putBoolean("voice_bubble_enabled", false).apply();
            launchRequested = false;
        }
    }

    boolean isVisible() { return false; }
    void hide() { launchRequested = false; }
    void destroy() { launchRequested = false; }
    void onConfigurationChanged() { }
}
