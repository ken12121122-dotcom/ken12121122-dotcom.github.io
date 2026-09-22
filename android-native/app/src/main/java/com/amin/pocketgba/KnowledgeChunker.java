package com.amin.pocketgba;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic paragraph/article chunker. It does not summarize or invent content. */
final class KnowledgeChunker {
    static final int MAX_CHARS = 1800;
    private static final Pattern ARTICLE = Pattern.compile("^(第[^\\s]{1,16}條(?:之[^\\s]{1,8})?)\\s*(.*)$");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");

    static final class Chunk {
        final String section;
        final String content;

        Chunk(String section, String content) {
            this.section = section;
            this.content = content;
        }
    }

    private KnowledgeChunker() { }

    static List<Chunk> split(String raw) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) return Collections.emptyList();
        List<Chunk> out = new ArrayList<>();
        String currentSection = "本文";
        StringBuilder current = new StringBuilder();
        for (String line : normalized.split("\\n")) {
            String value = line.trim();
            if (value.isEmpty()) {
                flushParagraph(currentSection, current, out);
                continue;
            }
            Matcher article = ARTICLE.matcher(value);
            Matcher heading = HEADING.matcher(value);
            if (article.matches() || heading.matches()) {
                flushParagraph(currentSection, current, out);
                currentSection = article.matches() ? article.group(1) : heading.group(1).trim();
                current.append(value);
            } else {
                if (current.length() > 0) current.append('\n');
                current.append(value);
            }
            if (current.length() >= MAX_CHARS) flushParagraph(currentSection, current, out);
        }
        flushParagraph(currentSection, current, out);
        return out;
    }

    private static void flushParagraph(String section, StringBuilder value, List<Chunk> out) {
        String text = value.toString().trim();
        value.setLength(0);
        if (text.isEmpty()) return;
        int offset = 0;
        int part = 1;
        while (offset < text.length()) {
            int end = Math.min(text.length(), offset + MAX_CHARS);
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('。', end - 1), text.lastIndexOf('\n', end - 1));
                if (boundary > offset + MAX_CHARS / 2) end = boundary + 1;
            }
            String suffix = text.length() > MAX_CHARS ? "（" + part++ + "）" : "";
            out.add(new Chunk(section + suffix, text.substring(offset, end).trim()));
            offset = end;
        }
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
