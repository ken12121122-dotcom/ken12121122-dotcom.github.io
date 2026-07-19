package com.amin.pocketgba;

import android.content.Context;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NativeSaveVaultBridge {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final int MAX_SAVE_BYTES = 2 * 1024 * 1024;
    private static final String SAVE_DIRECTORY = "gba-saves";

    private final File directory;

    NativeSaveVaultBridge(Context context) {
        directory = new File(context.getFilesDir(), SAVE_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Unable to create native save vault");
        }
    }

    @JavascriptInterface
    public synchronized String putSave(String key, String base64Data) {
        JSONObject result = new JSONObject();
        try {
            String safeKey = requireKey(key);
            if (base64Data == null || base64Data.length() > MAX_SAVE_BYTES * 2L) {
                throw new SecurityException("Save payload exceeds safe size");
            }

            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > MAX_SAVE_BYTES) {
                throw new SecurityException("Save byte length is invalid");
            }

            File current = currentFile(safeKey);
            File backup = backupFile(safeKey);
            File temporary = temporaryFile(safeKey);

            deleteIfExists(temporary);
            writeFully(temporary, bytes);

            if (current.isFile()) {
                copyReplace(current, backup);
            }
            moveReplace(temporary, current);

            long updatedAt = System.currentTimeMillis();
            if (!current.setLastModified(updatedAt)) {
                updatedAt = current.lastModified();
            }

            result.put("ok", true);
            result.put("key", safeKey);
            result.put("byteLength", bytes.length);
            result.put("sha256", sha256(bytes));
            result.put("updatedAt", updatedAt);
            result.put("hasBackup", backup.isFile());
            return result.toString();
        } catch (Exception error) {
            try {
                result.put("ok", false);
                result.put("error", safeMessage(error));
            } catch (JSONException ignored) {
                return "{\"ok\":false,\"error\":\"Native save write failed\"}";
            }
            return result.toString();
        }
    }

    @JavascriptInterface
    public synchronized String getSave(String key) {
        try {
            String safeKey = requireKey(key);
            byte[] bytes = readBestSave(safeKey);
            return bytes == null ? "" : Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }

    @JavascriptInterface
    public synchronized String getSaveInfo(String key) {
        JSONObject result = new JSONObject();
        try {
            String safeKey = requireKey(key);
            File current = currentFile(safeKey);
            File backup = backupFile(safeKey);
            File selected = current.isFile() ? current : (backup.isFile() ? backup : null);

            result.put("key", safeKey);
            result.put("exists", selected != null);
            result.put("hasCurrent", current.isFile());
            result.put("hasBackup", backup.isFile());
            if (selected != null) {
                byte[] bytes = readFully(selected);
                result.put("byteLength", bytes.length);
                result.put("sha256", sha256(bytes));
                result.put("updatedAt", selected.lastModified());
                result.put("source", selected.equals(current) ? "current" : "backup");
            }
            return result.toString();
        } catch (Exception error) {
            try {
                result.put("exists", false);
                result.put("error", safeMessage(error));
            } catch (JSONException ignored) {
                return "{\"exists\":false}";
            }
            return result.toString();
        }
    }

    private String requireKey(String key) {
        String value = key == null ? "" : key.trim();
        if (!KEY_PATTERN.matcher(value).matches()) {
            throw new SecurityException("Invalid save identity");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private File currentFile(String key) {
        return new File(directory, key + ".sav");
    }

    private File backupFile(String key) {
        return new File(directory, key + ".bak");
    }

    private File temporaryFile(String key) {
        return new File(directory, key + ".tmp");
    }

    private byte[] readBestSave(String key) throws Exception {
        File current = currentFile(key);
        if (current.isFile()) {
            byte[] bytes = readFully(current);
            if (bytes.length > 0 && bytes.length <= MAX_SAVE_BYTES) return bytes;
        }
        File backup = backupFile(key);
        if (backup.isFile()) {
            byte[] bytes = readFully(backup);
            if (bytes.length > 0 && bytes.length <= MAX_SAVE_BYTES) return bytes;
        }
        return null;
    }

    private void writeFully(File target, byte[] bytes) throws Exception {
        try (FileOutputStream fileOutput = new FileOutputStream(target);
             BufferedOutputStream output = new BufferedOutputStream(fileOutput)) {
            output.write(bytes);
            output.flush();
            fileOutput.getFD().sync();
        }
    }

    private byte[] readFully(File source) throws Exception {
        long length = source.length();
        if (length <= 0 || length > MAX_SAVE_BYTES) {
            throw new SecurityException("Stored save size is invalid");
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        if (offset != bytes.length) {
            throw new IllegalStateException("Stored save could not be read completely");
        }
        return bytes;
    }

    private void copyReplace(File source, File target) throws Exception {
        Files.copy(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
        );
    }

    private void moveReplace(File source, File target) throws Exception {
        try {
            Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (Exception atomicMoveFailed) {
            Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void deleteIfExists(File file) throws Exception {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Unable to clear stale temporary save");
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
