package com.amin.pocketgba;

final class PromptText {
    private PromptText() {}

    static String requireContent(CharSequence value) {
        if (value == null) throw new IllegalArgumentException("沒有收到選取文字");
        String content = value.toString();
        if (content.trim().isEmpty()) throw new IllegalArgumentException("選取文字不可為空白");
        return content;
    }

    static String preview(String content, int maximumLength) {
        String compact = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maximumLength) return compact;
        return compact.substring(0, Math.max(0, maximumLength - 1)) + "…";
    }
}
