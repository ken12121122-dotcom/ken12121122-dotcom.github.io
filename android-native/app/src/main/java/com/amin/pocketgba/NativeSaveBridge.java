package com.amin.pocketgba;

import android.content.Context;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NativeSaveBridge {
    private static final int MAX_SAVE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_BASE64_CHARS = 3 * 1024 * 1024;
    private static final Pattern SAFE_KEY = Pattern.compile("[a-zA-Z0-9_-]{8,96}");
    private static final String DIRECTORY_NAME = "gba-saves";

    private final File saveDirectory;

    NativeSaveBridge(Context context) {
        saveDirectory = new File(context.getApplicationContext().getFilesDir(), DIRECTORY_NAME);
    }

    @JavascriptInterface
    public String putSave(String key, String base64Data) {
        JSONObject result = new JSONObject();
        try {
            String safeKey = requireKey(key);
            if (base64Data == null || base64Data.length() > MAX_BASE64_CHARS) {
                throw new IllegalArgumentException("存檔資料超過允許大小");
            }
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > MAX_SAVE_BYTES) {
                throw new IllegalArgumentException("存檔資料大小不正確");
            }
            ensureDirectory();

            File current = saveFile(safeKey);
            File backup = backupFile(safeKey);
            String hash = sha256(bytes);
            JSONObject previousMetadata = readMetadata(safeKey);
            boolean unchanged = current.exists()
                    && current.length() == bytes.length
                    && hash.equals(previousMetadata.optString("sha256", ""));

            if (unchanged) {
                result.put("ok", true);
                result.put("unchanged", true);
                result.put("key", safeKey);
                result.put("byteLength", bytes.length);
                result.put("updatedAt", previousMetadata.optLong("updatedAt", current.lastModified()));
                result.put("sha256", hash);
                result.put("hasBackup", backup.exists());
                return result.toString();
            }

            File temporary = new File(saveDirectory, safeKey + ".tmp");
            writeAtomically(temporary, bytes);
            if (backup.exists() && !backup.delete()) {
                throw new IllegalStateException("無法更新上一版存檔");
            }
            if (current.exists() && !current.renameTo(backup)) {
                throw new IllegalStateException("無法保留上一版存檔");
            }
            if (!temporary.renameTo(current)) {
                if (backup.exists()) backup.renameTo(current);
                throw new IllegalStateException("無法啟用新存檔");
            }

            long updatedAt = System.currentTimeMillis();
            current.setLastModified(updatedAt);
            writeMetadata(safeKey, bytes.length, updatedAt, hash);

            result.put("ok", true);
            result.put("unchanged", false);
            result.put("key", safeKey);
            result.put("byteLength", bytes.length);
            result.put("updatedAt", updatedAt);
            result.put("sha256", hash);
            result.put("hasBackup", backup.exists());
        } catch (Exception error) {
            try {
                result.put("ok", false);
                result.put("error", safeMessage(error));
            } catch (JSONException ignored) {}
        }
        return result.toString();
    }

    @JavascriptInterface
    public String getSave(String key) {
        try {
            String safeKey = requireKey(key);
            File current = saveFile(safeKey);
            File selected = current.exists() ? current : backupFile(safeKey);
            if (!selected.exists() || selected.length() <= 0 || selected.length() > MAX_SAVE_BYTES) {
                return "";
            }
            byte[] bytes = readAll(selected);
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }

    @JavascriptInterface
    public String getSaveInfo(String key) {
        JSONObject result = new JSONObject();
        try {
            String safeKey = requireKey(key);
            File current = saveFile(safeKey);
            File backup = backupFile(safeKey);
            JSONObject metadata = readMetadata(safeKey);

            result.put("ok", true);
            result.put("key", safeKey);
            result.put("exists", current.exists() || backup.exists());
            result.put("byteLength", current.exists() ? current.length() : backup.length());
            result.put("updatedAt", metadata.optLong(
                    "updatedAt",
                    current.exists() ? current.lastModified() : backup.lastModified()
            ));
            result.put("sha256", metadata.optString("sha256", ""));
            result.put("hasBackup", backup.exists());
        } catch (Exception error) {
            try {
                result.put("ok", false);
                result.put("error", safeMessage(error));
            } catch (JSONException ignored) {}
        }
        return result.toString();
    }

    @JavascriptInterface
    public String listSaves() {
        JSONArray items = new JSONArray();
        try {
            ensureDirectory();
            File[] files = saveDirectory.listFiles((dir, name) -> name.endsWith(".sav"));
            if (files == null) return items.toString();
            for (File file : files) {
                String key = file.getName().substring(0, file.getName().length() - 4);
                JSONObject metadata = readMetadata(key);
                JSONObject item = new JSONObject();
                item.put("key", key);
                item.put("byteLength", file.length());
                item.put("updatedAt", metadata.optLong("updatedAt", file.lastModified()));
                item.put("sha256", metadata.optString("sha256", ""));
                item.put("hasBackup", backupFile(key).exists());
                items.put(item);
            }
        } catch (Exception ignored) {}
        return items.toString();
    }

    @JavascriptInterface
    public String getBridgeVersion() {
        return "1.0.0";
    }

    private String requireKey(String key) {
        String value = key == null ? "" : key.trim();
        if (!SAFE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("存檔識別碼不合法");
        }
        return value;
    }

    private void ensureDirectory() {
        if (!saveDirectory.exists() && !saveDirectory.mkdirs()) {
            throw new IllegalStateException("無法建立原生存檔目錄");
        }
        if (!saveDirectory.isDirectory()) {
            throw new IllegalStateException("原生存檔路徑不是目錄");
        }
    }

    private File saveFile(String key) {
        return new File(saveDirectory, key + ".sav");
    }

    private File backupFile(String key) {
        return new File(saveDirectory, key + ".bak");
    }

    private File metadataFile(String key) {
        return new File(saveDirectory, key + ".json");
    }

    private void writeAtomically(File target, byte[] bytes) throws Exception {
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("無法清除暫存檔");
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private void writeMetadata(String key, int byteLength, long updatedAt, String hash) throws Exception {
        JSONObject metadata = new JSONObject();
        metadata.put("format", "amin-native-gba-save");
        metadata.put("formatVersion", 1);
        metadata.put("key", key);
        metadata.put("byteLength", byteLength);
        metadata.put("updatedAt", updatedAt);
        metadata.put("sha256", hash);
        writeAtomically(
                metadataFile(key),
                metadata.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private JSONObject readMetadata(String key) {
        File file = metadataFile(key);
        if (!file.exists() || file.length() <= 0 || file.length() > 32 * 1024) {
            return new JSONObject();
        }
        try {
            return new JSONObject(new String(readAll(file), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private byte[] readAll(File file) throws Exception {
        long length = file.length();
        if (length <= 0 || length > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("存檔檔案大小不正確");
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        if (offset != bytes.length) {
            throw new IllegalStateException("存檔讀取不完整");
        }
        return bytes;
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte item : hash) {
            value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
