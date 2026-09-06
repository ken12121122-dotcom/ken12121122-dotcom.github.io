package com.amin.pocketgba;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/** Content-addressed app-private raw store. Existing snapshots are never overwritten. */
final class ImmutableKnowledgeSourceStore {
    static final class Snapshot {
        final String sha256;
        final String relativePath;
        final boolean created;

        Snapshot(String sha256, String relativePath, boolean created) {
            this.sha256 = sha256;
            this.relativePath = relativePath;
            this.created = created;
        }
    }

    private final File root;
    private final String relativeRoot;

    ImmutableKnowledgeSourceStore(Context context, String namespace) {
        String safe = namespace == null ? "traceable-knowledge" : namespace.replaceAll("[^A-Za-z0-9._-]", "_");
        relativeRoot = safe + "/raw";
        root = new File(context.getFilesDir(), relativeRoot);
    }

    Snapshot put(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("raw content required");
        String hash = sha256(bytes);
        if ((!root.exists() && !root.mkdirs()) || !root.isDirectory()) {
            throw new IllegalStateException("raw store unavailable");
        }
        File target = new File(root, hash + ".bin");
        if (target.isFile()) {
            byte[] existing = read(target);
            if (!MessageDigest.isEqual(existing, bytes)) throw new IllegalStateException("hash collision");
            return new Snapshot(hash, relativeRoot + "/" + target.getName(), false);
        }
        File temporary = new File(root, hash + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            if (!target.isFile()) throw new IllegalStateException("raw snapshot commit failed");
        }
        return new Snapshot(hash, relativeRoot + "/" + target.getName(), true);
    }

    private static byte[] read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder out = new StringBuilder(64);
        for (byte item : digest) out.append(String.format("%02x", item & 0xff));
        return out.toString();
    }
}
