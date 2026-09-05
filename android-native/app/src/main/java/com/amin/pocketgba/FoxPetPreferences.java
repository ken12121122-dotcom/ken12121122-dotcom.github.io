package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

final class FoxPetPreferences {
    static final String MODE_VOICE_BALL = "voice_ball";
    static final String MODE_FOX = "fox";
    static final String MODE_HIDDEN = "hidden";

    private static final String PREFS = "amin_fox_pet";
    private static final String KEY_DISPLAY_MODE = "display_mode";
    private static final String KEY_DRAGGABLE = "draggable";
    private static final String KEY_CHAT_BUBBLE = "chat_bubble";
    private static final String KEY_AUTO_SPEAK = "auto_speak";
    private static final String KEY_SPEECH_RATE = "speech_rate";
    private static final String KEY_PITCH = "pitch";
    private static final String KEY_VOLUME = "volume";

    private FoxPetPreferences() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getDisplayMode(Context context) {
        String value = prefs(context).getString(KEY_DISPLAY_MODE, MODE_VOICE_BALL);
        if (MODE_FOX.equals(value) || MODE_HIDDEN.equals(value)) return value;
        return MODE_VOICE_BALL;
    }

    static void setDisplayMode(Context context, String mode) {
        String safe = MODE_FOX.equals(mode) ? MODE_FOX
                : MODE_HIDDEN.equals(mode) ? MODE_HIDDEN
                : MODE_VOICE_BALL;
        prefs(context).edit().putString(KEY_DISPLAY_MODE, safe).apply();
    }

    static boolean isDraggable(Context context) {
        return prefs(context).getBoolean(KEY_DRAGGABLE, true);
    }

    static void setDraggable(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_DRAGGABLE, value).apply();
    }

    static boolean isChatBubbleEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CHAT_BUBBLE, true);
    }

    static void setChatBubbleEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_CHAT_BUBBLE, value).apply();
    }

    static boolean isAutoSpeakEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_SPEAK, true);
    }

    static void setAutoSpeakEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_SPEAK, value).apply();
    }

    static float getSpeechRate(Context context) {
        return clamp(prefs(context).getFloat(KEY_SPEECH_RATE, 1.0f), 0.6f, 1.6f);
    }

    static void setSpeechRate(Context context, float value) {
        prefs(context).edit().putFloat(KEY_SPEECH_RATE, clamp(value, 0.6f, 1.6f)).apply();
    }

    static float getPitch(Context context) {
        return clamp(prefs(context).getFloat(KEY_PITCH, 1.08f), 0.6f, 1.6f);
    }

    static void setPitch(Context context, float value) {
        prefs(context).edit().putFloat(KEY_PITCH, clamp(value, 0.6f, 1.6f)).apply();
    }

    static float getVolume(Context context) {
        return clamp(prefs(context).getFloat(KEY_VOLUME, 1.0f), 0.0f, 1.0f);
    }

    static void setVolume(Context context, float value) {
        prefs(context).edit().putFloat(KEY_VOLUME, clamp(value, 0.0f, 1.0f)).apply();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
