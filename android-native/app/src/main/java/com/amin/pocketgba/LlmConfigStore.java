package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class LlmConfigStore {
    static final String PROVIDER_GEMINI = "gemini";
    static final String PROVIDER_OPENAI = "openai";
    static final String PROVIDER_CLAUDE = "claude";

    private static final String PREFS = "amin_llm_settings";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_MODEL = "model";
    private static final String KEY_SECRET = "api_key_enc";
    private static final String KEY_ALIAS = "amin_llm_api_key_v1";

    private LlmConfigStore() {}

    static String provider(Context context) {
        return prefs(context).getString(KEY_PROVIDER, PROVIDER_GEMINI);
    }

    static String model(Context context) {
        String value = prefs(context).getString(KEY_MODEL, "");
        if (value != null && !value.trim().isEmpty()) return value.trim();
        return defaultModel(provider(context));
    }

    static String defaultModel(String provider) {
        if (PROVIDER_OPENAI.equals(provider)) return "gpt-5.4-mini";
        if (PROVIDER_CLAUDE.equals(provider)) return "claude-sonnet-4-5";
        return "gemini-2.5-flash";
    }

    static void save(Context context, String provider, String model, String apiKey) throws Exception {
        String safeProvider = normalizeProvider(provider);
        String safeModel = model == null || model.trim().isEmpty() ? defaultModel(safeProvider) : model.trim();
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_PROVIDER, safeProvider)
                .putString(KEY_MODEL, safeModel);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            editor.putString(KEY_SECRET, encrypt(apiKey.trim()));
        }
        editor.apply();
    }

    static String apiKey(Context context) {
        String encoded = prefs(context).getString(KEY_SECRET, "");
        if (encoded == null || encoded.isEmpty()) return "";
        try { return decrypt(encoded); } catch (Exception ignored) { return ""; }
    }

    static boolean hasApiKey(Context context) {
        return !apiKey(context).isEmpty();
    }

    static String label(Context context) {
        String provider = provider(context);
        String providerLabel = PROVIDER_OPENAI.equals(provider) ? "OpenAI" : PROVIDER_CLAUDE.equals(provider) ? "Claude" : "Gemini";
        return providerLabel + " · " + model(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalizeProvider(String value) {
        if (PROVIDER_OPENAI.equals(value)) return PROVIDER_OPENAI;
        if (PROVIDER_CLAUDE.equals(value)) return PROVIDER_CLAUDE;
        return PROVIDER_GEMINI;
    }

    private static SecretKey key() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) entry).getSecretKey();

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private static String decrypt(String encoded) throws Exception {
        String[] parts = encoded.split("\\.", 2);
        if (parts.length != 2) return "";
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
