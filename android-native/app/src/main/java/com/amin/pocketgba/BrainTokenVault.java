package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class BrainTokenVault {
    private static final String STORE = "amin_brain_auth";
    private static final String RECORD = "encrypted_session_v1";
    private static final String ALIAS = "amin_brain_github_token_v1";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private final SharedPreferences preferences;

    BrainTokenVault(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    synchronized void save(GitHubDeviceFlowProtocol.Token token) throws Exception {
        if (token == null || token.accessToken().isBlank()) throw new IllegalArgumentException("Token 不可為空。 ");
        JSONObject clear = new JSONObject()
                .put("access_token", token.accessToken())
                .put("refresh_token", token.refreshToken())
                .put("expires_at", token.expiresAtMillis())
                .put("refresh_expires_at", token.refreshExpiresAtMillis());
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(clear.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject record = new JSONObject()
                .put("version", 1)
                .put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        if (!preferences.edit().putString(RECORD, record.toString()).commit()) {
            throw new IllegalStateException("無法儲存加密 GitHub 登入。 ");
        }
    }

    synchronized GitHubDeviceFlowProtocol.Token load() {
        String stored = preferences.getString(RECORD, "");
        if (stored == null || stored.isBlank()) return null;
        try {
            JSONObject record = new JSONObject(stored);
            if (record.optInt("version", -1) != 1) throw new IllegalStateException();
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128,
                    Base64.decode(record.getString("iv"), Base64.NO_WRAP)));
            byte[] clearBytes = cipher.doFinal(Base64.decode(
                    record.getString("ciphertext"), Base64.NO_WRAP));
            JSONObject clear = new JSONObject(new String(clearBytes, StandardCharsets.UTF_8));
            return new GitHubDeviceFlowProtocol.Token(clear.getString("access_token"),
                    clear.optString("refresh_token", ""), clear.getLong("expires_at"),
                    clear.optLong("refresh_expires_at", 0L));
        } catch (Exception error) {
            clear();
            return null;
        }
    }

    synchronized void clear() {
        preferences.edit().remove(RECORD).commit();
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        java.security.Key existing = store.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
