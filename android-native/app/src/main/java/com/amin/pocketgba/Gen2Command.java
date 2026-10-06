package com.amin.pocketgba;

/**
 * Builds the OWNER's /gen2 comment for a waiting run. Only OWNER actions are
 * offered (decide, input, cancel); skill results are posted by agents. The
 * engine re-validates every command, these checks just keep the app from
 * posting something it already knows will be rejected.
 */
final class Gen2Command {
    static final int MAX_TEXT = 20000;

    private Gen2Command() { }

    static String decide(Gen2RunView run, String optionId, String comment) {
        Gen2RunView.Pending pending = requireWaiting(run, "gate");
        Gen2RunView.Choice choice = pending.option(optionId);
        if (choice == null) throw new IllegalArgumentException("這個選項不存在。 ");
        String text = clean(comment);
        if (choice.commentRequired && text.isEmpty()) {
            throw new IllegalArgumentException("「" + choice.label + "」必須寫說明。 ");
        }
        return line("/gen2 decide " + pending.step + " " + choice.id, text);
    }

    static String input(Gen2RunView run, String material) {
        Gen2RunView.Pending pending = requireWaiting(run, "input");
        String text = clean(material);
        if (text.isEmpty()) throw new IllegalArgumentException("請先填寫要提供的資料。 ");
        return line("/gen2 input " + pending.step, text);
    }

    static String cancel(Gen2RunView run, String reason) {
        if (run == null || "done".equals(run.status) || "stopped".equals(run.status)) {
            throw new IllegalStateException("這次執行已經結束。 ");
        }
        return line("/gen2 cancel", clean(reason));
    }

    private static Gen2RunView.Pending requireWaiting(Gen2RunView run, String type) {
        if (run == null || run.pending == null || !type.equals(run.pending.type)) {
            throw new IllegalStateException("這次執行目前不是在等這個動作，請重新整理。 ");
        }
        if (!Gen2RunView.STEP_ID.matcher(run.pending.step).matches()) throw new IllegalStateException("步驟代號無效。 ");
        return run.pending;
    }

    private static String clean(String value) {
        String text = value == null ? "" : value.replace("\r\n", "\n").trim();
        if (text.length() > MAX_TEXT) throw new IllegalArgumentException("內容太長（上限 " + MAX_TEXT + " 字）。 ");
        return text;
    }

    private static String line(String command, String text) {
        return text.isEmpty() ? command : command + "\n" + text;
    }
}
