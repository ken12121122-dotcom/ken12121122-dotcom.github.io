package com.amin.pocketgba;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Persistent ROM storage owned by the Android app.
 *
 * JavaScript streams an existing IndexedDB ROM in bounded chunks. The bridge writes to
 * a temporary file, verifies both byte length and SHA-256, fsyncs it, then atomically
 * replaces the current cartridge. ROM bytes never leave the device.
 */
public final class NativeCartridgeVaultBridge {
    static final String ROM_DIRECTORY = "gba-roms";
    static final String ROM_PATH_PREFIX = "/__amin_native__/vault-rom/";
    static final String ENGINE_PATH_PREFIX = "/__amin_native__/emulator/";

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final int MAX_ROM_BYTES = 64 * 1024 * 1024;
    private static final int MAX_CHUNK_BYTES = 512 * 1024;

    private final File directory;
    private final String trustedOrigin;
    private final Map<String, ImportSession> sessions = new HashMap<>();

    NativeCartridgeVaultBridge(Context context) {
        directory = new File(context.getFilesDir(), ROM_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Unable to create native cartridge vault");
        }
        Uri appUri = Uri.parse(BuildConfig.APP_WEB_URL);
        String host = appUri.getHost();
        trustedOrigin = host == null ? "" : "https://" + host;
        removeStaleTemporaryFiles();
    }

    @JavascriptInterface
    public synchronized boolean hasRom(String key) {
        try {
            File file = romFile(requireKey(key));
            return file.isFile() && file.length() > 0 && file.length() <= MAX_ROM_BYTES;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @JavascriptInterface
    public synchronized String getRomInfo(String key) {
        JSONObject result = new JSONObject();
        try {
            String safeKey = requireKey(key);
            File file = romFile(safeKey);
            result.put("key", safeKey);
            result.put("exists", file.isFile());
            if (file.isFile()) {
                result.put("byteLength", file.length());
                result.put("updatedAt", file.lastModified());
                result.put("url", romUrl(safeKey));
            }
            return result.toString();
        } catch (Exception error) {
            return errorJson(error);
        }
    }

    @JavascriptInterface
    public synchronized String getRomUrl(String key) {
        try {
            String safeKey = requireKey(key);
            return romFile(safeKey).isFile() ? romUrl(safeKey) : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    @JavascriptInterface
    public String getEngineBaseUrl() {
        return trustedOrigin.isEmpty() ? "" : trustedOrigin + ENGINE_PATH_PREFIX;
    }

    @JavascriptInterface
    public synchronized String beginImport(
            String key,
            String displayName,
            long expectedSize
    ) {
        JSONObject result = new JSONObject();
        try {
            String safeKey = requireKey(key);
            if (expectedSize <= 0 || expectedSize > MAX_ROM_BYTES) {
                throw new SecurityException("ROM size is outside the allowed range");
            }

            cancelImportsForKey(safeKey);
            String sessionId = UUID.randomUUID().toString();
            File temporary = new File(directory, safeKey + "." + sessionId + ".tmp");
            FileOutputStream fileOutput = new FileOutputStream(temporary, false);
            ImportSession session = new ImportSession(
                    sessionId,
                    safeKey,
                    sanitizeName(displayName),
                    expectedSize,
                    temporary,
                    fileOutput,
                    new BufferedOutputStream(fileOutput),
                    MessageDigest.getInstance("SHA-256")
            );
            sessions.put(sessionId, session);

            result.put("ok", true);
            result.put("sessionId", sessionId);
            result.put("key", safeKey);
            result.put("expectedSize", expectedSize);
            return result.toString();
        } catch (Exception error) {
            return errorJson(error);
        }
    }

    @JavascriptInterface
    public synchronized String appendImport(String sessionId, String base64Chunk) {
        JSONObject result = new JSONObject();
        try {
            ImportSession session = requireSession(sessionId);
            if (base64Chunk == null || base64Chunk.isEmpty()) {
                throw new IllegalArgumentException("ROM chunk is empty");
            }
            byte[] bytes = Base64.decode(base64Chunk, Base64.DEFAULT);
            if (bytes.length <= 0 || bytes.length > MAX_CHUNK_BYTES) {
                throw new SecurityException("ROM chunk exceeds the allowed size");
            }
            if (session.written + bytes.length > session.expectedSize) {
                throw new SecurityException("ROM stream exceeds the declared byte length");
            }

            session.output.write(bytes);
            session.digest.update(bytes);
            session.written += bytes.length;

            result.put("ok", true);
            result.put("written", session.written);
            result.put("expectedSize", session.expectedSize);
            return result.toString();
        } catch (Exception error) {
            abortQuietly(sessionId);
            return errorJson(error);
        }
    }

    @JavascriptInterface
    public synchronized String finishImport(String sessionId) {
        JSONObject result = new JSONObject();
        ImportSession session = null;
        try {
            session = requireSession(sessionId);
            sessions.remove(sessionId);
            session.output.flush();
            session.fileOutput.getFD().sync();
            session.output.close();

            if (session.written != session.expectedSize) {
                throw new SecurityException("ROM byte length does not match the declaration");
            }
            String digest = toHex(session.digest.digest());
            if (!digest.equals(session.key)) {
                throw new SecurityException("ROM SHA-256 verification failed");
            }

            File destination = romFile(session.key);
            moveReplace(session.temporary, destination);
            long updatedAt = System.currentTimeMillis();
            if (!destination.setLastModified(updatedAt)) updatedAt = destination.lastModified();

            JSONObject metadata = new JSONObject();
            metadata.put("key", session.key);
            metadata.put("displayName", session.displayName);
            metadata.put("byteLength", destination.length());
            metadata.put("sha256", digest);
            metadata.put("updatedAt", updatedAt);
            writeMetadata(session.key, metadata);

            result.put("ok", true);
            result.put("key", session.key);
            result.put("byteLength", destination.length());
            result.put("sha256", digest);
            result.put("updatedAt", updatedAt);
            result.put("url", romUrl(session.key));
            return result.toString();
        } catch (Exception error) {
            if (session != null) closeAndDelete(session);
            return errorJson(error);
        }
    }

    @JavascriptInterface
    public synchronized boolean cancelImport(String sessionId) {
        ImportSession session = sessions.remove(sessionId);
        if (session == null) return false;
        closeAndDelete(session);
        return true;
    }

    @JavascriptInterface
    public synchronized boolean deleteRom(String key) {
        try {
            String safeKey = requireKey(key);
            boolean romDeleted = !romFile(safeKey).exists() || romFile(safeKey).delete();
            File metadata = metadataFile(safeKey);
            boolean metadataDeleted = !metadata.exists() || metadata.delete();
            return romDeleted && metadataDeleted;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static File resolveRomFile(Context context, String key) {
        if (!KEY_PATTERN.matcher(key == null ? "" : key).matches()) return null;
        File directory = new File(context.getFilesDir(), ROM_DIRECTORY);
        File file = new File(directory, key.toLowerCase(Locale.ROOT) + ".gba");
        try {
            String directoryPath = directory.getCanonicalPath() + File.separator;
            String filePath = file.getCanonicalPath();
            return filePath.startsWith(directoryPath) ? file : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String romUrl(String key) {
        return trustedOrigin + ROM_PATH_PREFIX + key + ".gba";
    }

    private String requireKey(String key) {
        String value = key == null ? "" : key.trim();
        if (!KEY_PATTERN.matcher(value).matches()) {
            throw new SecurityException("Invalid ROM identity");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private ImportSession requireSession(String sessionId) {
        ImportSession session = sessions.get(sessionId);
        if (session == null) throw new IllegalStateException("ROM import session is unavailable");
        return session;
    }

    private File romFile(String key) {
        return new File(directory, key + ".gba");
    }

    private File metadataFile(String key) {
        return new File(directory, key + ".json");
    }

    private void writeMetadata(String key, JSONObject metadata) throws Exception {
        File temporary = new File(directory, key + ".json.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write((metadata.toString() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        moveReplace(temporary, metadataFile(key));
    }

    private void moveReplace(File source, File destination) throws Exception {
        try {
            Files.move(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (Exception atomicMoveFailed) {
            Files.move(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void cancelImportsForKey(String key) {
        String[] ids = sessions.values().stream()
                .filter(session -> session.key.equals(key))
                .map(session -> session.id)
                .toArray(String[]::new);
        for (String id : ids) abortQuietly(id);
    }

    private void abortQuietly(String sessionId) {
        ImportSession session = sessions.remove(sessionId);
        if (session != null) closeAndDelete(session);
    }

    private void closeAndDelete(ImportSession session) {
        try {
            session.output.close();
        } catch (Exception ignored) {
            // Best effort cleanup.
        }
        if (session.temporary.exists()) session.temporary.delete();
    }

    private void removeStaleTemporaryFiles() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".tmp"));
        if (files == null) return;
        for (File file : files) file.delete();
    }

    private String sanitizeName(String value) {
        String name = value == null ? "cartridge.gba" : value;
        name = name.replaceAll("[\\u0000-\\u001F\\u007F]", "").trim();
        if (name.length() > 180) name = name.substring(0, 180);
        return name.isEmpty() ? "cartridge.gba" : name;
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(String.format(Locale.ROOT, "%02x", value));
        return builder.toString();
    }

    private String errorJson(Exception error) {
        JSONObject result = new JSONObject();
        try {
            result.put("ok", false);
            result.put("error", error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage());
            return result.toString();
        } catch (JSONException ignored) {
            return "{\"ok\":false,\"error\":\"Native cartridge vault failed\"}";
        }
    }

    private static final class ImportSession {
        final String id;
        final String key;
        final String displayName;
        final long expectedSize;
        final File temporary;
        final FileOutputStream fileOutput;
        final BufferedOutputStream output;
        final MessageDigest digest;
        long written;

        ImportSession(
                String id,
                String key,
                String displayName,
                long expectedSize,
                File temporary,
                FileOutputStream fileOutput,
                BufferedOutputStream output,
                MessageDigest digest
        ) {
            this.id = id;
            this.key = key;
            this.displayName = displayName;
            this.expectedSize = expectedSize;
            this.temporary = temporary;
            this.fileOutput = fileOutput;
            this.output = output;
            this.digest = digest;
        }
    }
}
