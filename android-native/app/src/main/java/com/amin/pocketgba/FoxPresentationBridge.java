package com.amin.pocketgba;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.ref.WeakReference;

/** One-way runtime-to-presentation bridge; fox taps only request the existing listener. */
final class FoxPresentationBridge {
    enum VisualState { ACTIVE, LISTENING, THINKING, TALKING, SITTING, SLEEPING }

    interface RuntimeListener { void onFoxTapped(); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<RuntimeListener> runtime = new WeakReference<>(null);

    private FoxPresentationBridge() { }

    static void attach(RuntimeListener listener) { runtime = new WeakReference<>(listener); }

    static void detach(RuntimeListener listener) {
        if (runtime.get() == listener) runtime.clear();
    }

    static boolean applyDisplayMode(Context context) {
        if (context == null) return false;
        boolean fox = FoxPetPreferences.MODE_FOX.equals(FoxPetPreferences.getDisplayMode(context));
        boolean permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
        Intent intent = new Intent(context, FoxPetOverlayService.class)
                .setAction(fox && permitted ? FoxPetOverlayService.ACTION_START : FoxPetOverlayService.ACTION_STOP);
        try {
            if (fox && permitted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else if (fox && permitted) context.startService(intent);
            else context.stopService(intent);
        } catch (RuntimeException ignored) { return false; }
        return !fox || permitted;
    }

    static void present(Context context, VisualState state, String message, boolean speak) {
        if (context == null || state == null
                || !FoxPetPreferences.MODE_FOX.equals(FoxPetPreferences.getDisplayMode(context))) return;
        Intent intent = new Intent(context, FoxPetOverlayService.class)
                .setAction(FoxPetOverlayService.ACTION_UPDATE)
                .putExtra(FoxPetOverlayService.EXTRA_VISUAL_STATE, state.name())
                .putExtra(FoxPetOverlayService.EXTRA_MESSAGE, message == null ? "" : message)
                .putExtra(FoxPetOverlayService.EXTRA_SPEAK, speak);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException ignored) { }
    }

    static void requestListening() {
        MAIN.post(() -> {
            RuntimeListener listener = runtime.get();
            if (listener != null) listener.onFoxTapped();
        });
    }
}
