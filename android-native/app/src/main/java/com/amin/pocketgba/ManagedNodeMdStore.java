package com.amin.pocketgba;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Creates only OWNER-approved custom Node draft Markdown in app-private storage. */
final class ManagedNodeMdStore {
    private static final String DIRECTORY = "node-context";

    private ManagedNodeMdStore() { }

    static String createDraft(Context context, String nodeId, String title, String description) {
        if (context == null || clean(nodeId).isEmpty()) return "";
        String safeTitle = oneLine(title);
        if (safeTitle.isEmpty()) safeTitle = "新節點";
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) return "";
        String filename = safeName(nodeId) + ".md";
        File target = new File(directory, filename);
        if (target.isFile()) return "file:" + DIRECTORY + "/" + filename;
        File temporary = new File(directory, filename + ".tmp");
        String markdown = "---\nnode_id: " + clean(nodeId) + "\ntitle: " + yaml(safeTitle)
                + "\nversion: 1\nreview_status: generated\nread_only: true\n---\n\n# "
                + safeTitle + "\n\n" + (clean(description).isEmpty()
                ? "此 Node 的 Markdown 尚待 OWNER 補充。"
                : clean(description)) + "\n";
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(markdown.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (Exception error) {
            temporary.delete();
            return "";
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            return "";
        }
        return "file:" + DIRECTORY + "/" + filename;
    }

    private static String safeName(String value) {
        return clean(value).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String yaml(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String oneLine(String value) { return clean(value).replace('\n', ' ').replace('\r', ' '); }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
