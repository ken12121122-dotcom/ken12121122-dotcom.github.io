package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Local access gate for the prompt keyboard.
 * Stores only a random salt and a PBKDF2 verifier; the PIN itself is never persisted.
 */
public final class PromptAccessLock {
    private static final String PREFS = "prompt_access_lock_v1";
    private static final String KEY_SALT = "salt";
    private static final String KEY_VERIFIER = "verifier";
    private static final String KEY_FAILURES = "failures";
    private static final String KEY_NEXT_ALLOWED_AT = "next_allowed_at";
    private static final int PIN_LENGTH = 6;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final long[] BACKOFF_MS = {
            5_000L,
            15_000L,
            30_000L,
            60_000L,
            5 * 60_000L,
            15 * 60_000L,
            60 * 60_000L
    };

    private final SharedPreferences preferences;
    private final SecureRandom secureRandom = new SecureRandom();

    public PromptAccessLock(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isConfigured() {
        return preferences.contains(KEY_SALT) && preferences.contains(KEY_VERIFIER);
    }

    public int getPinLength() {
        return PIN_LENGTH;
    }

    public void configure(char[] pin) throws GeneralSecurityException {
        validatePin(pin);
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] verifier = derive(pin, salt);
        preferences.edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
                .putInt(KEY_FAILURES, 0)
                .putLong(KEY_NEXT_ALLOWED_AT, 0L)
                .apply();
        clear(salt);
        clear(verifier);
    }

    public VerifyResult verify(char[] pin, long nowMillis) throws GeneralSecurityException {
        long remaining = getRemainingDelayMillis(nowMillis);
        if (remaining > 0L) {
            return VerifyResult.blocked(remaining, preferences.getInt(KEY_FAILURES, 0));
        }
        validatePin(pin);
        byte[] salt = Base64.decode(preferences.getString(KEY_SALT, ""), Base64.NO_WRAP);
        byte[] expected = Base64.decode(preferences.getString(KEY_VERIFIER, ""), Base64.NO_WRAP);
        byte[] actual = derive(pin, salt);
        boolean matches = MessageDigest.isEqual(expected, actual);
        clear(salt);
        clear(expected);
        clear(actual);

        if (matches) {
            preferences.edit()
                    .putInt(KEY_FAILURES, 0)
                    .putLong(KEY_NEXT_ALLOWED_AT, 0L)
                    .apply();
            return VerifyResult.success();
        }

        int failures = preferences.getInt(KEY_FAILURES, 0) + 1;
        long delay = BACKOFF_MS[Math.min(failures - 1, BACKOFF_MS.length - 1)];
        preferences.edit()
                .putInt(KEY_FAILURES, failures)
                .putLong(KEY_NEXT_ALLOWED_AT, nowMillis + delay)
                .apply();
        return VerifyResult.failure(delay, failures);
    }

    public long getRemainingDelayMillis(long nowMillis) {
        return Math.max(0L, preferences.getLong(KEY_NEXT_ALLOWED_AT, 0L) - nowMillis);
    }

    private byte[] derive(char[] pin, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private void validatePin(char[] pin) {
        if (pin == null || pin.length != PIN_LENGTH) {
            throw new IllegalArgumentException("PIN must contain exactly " + PIN_LENGTH + " digits");
        }
        for (char value : pin) {
            if (value < '0' || value > '9') {
                throw new IllegalArgumentException("PIN must contain digits only");
            }
        }
    }

    public static void clear(char[] value) {
        if (value == null) return;
        java.util.Arrays.fill(value, '\0');
    }

    private static void clear(byte[] value) {
        if (value == null) return;
        java.util.Arrays.fill(value, (byte) 0);
    }

    public static final class VerifyResult {
        public final boolean success;
        public final boolean blocked;
        public final long retryAfterMillis;
        public final int failureCount;

        private VerifyResult(boolean success, boolean blocked, long retryAfterMillis, int failureCount) {
            this.success = success;
            this.blocked = blocked;
            this.retryAfterMillis = retryAfterMillis;
            this.failureCount = failureCount;
        }

        static VerifyResult success() {
            return new VerifyResult(true, false, 0L, 0);
        }

        static VerifyResult failure(long retryAfterMillis, int failureCount) {
            return new VerifyResult(false, false, retryAfterMillis, failureCount);
        }

        static VerifyResult blocked(long retryAfterMillis, int failureCount) {
            return new VerifyResult(false, true, retryAfterMillis, failureCount);
        }
    }
}
