package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversation rules for handling GEN2 runs by voice (no Android types, unit tested).
 *
 * The LLM only talks and suggests. It may propose one action as JSON; the app
 * turns that into a /gen2 command with {@link Gen2Command} and reads it back.
 * Nothing is posted until the OWNER confirms with their own words, and that
 * check lives here, not in the model.
 */
final class Gen2VoiceBrain {
    static final int MAX_REVIEW_CHARS = 3000;

    enum Local { CONFIRM, CANCEL, NEXT, END, OPEN_PAGE, LIST, NONE }

    private static final Pattern NEGATED = Pattern.compile("(不|別|沒|未)\\s*(要|確定|確認|對|好|送)");
    private static final Pattern CONFIRM = Pattern.compile("^(確定|確認|送出|送吧|對|是的|是|好|可以|沒問題|就這樣|ok|okay|yes)");
    private static final Pattern CANCEL = Pattern.compile("(取消|不要|等一下|等等|先不要|不對|錯了|算了|重來|改一下)");
    private static final Pattern NEXT = Pattern.compile("^(下一筆|下一個|跳過|下一則|換下一個)");
    private static final Pattern END = Pattern.compile("^(結束|先這樣|關閉|沒事了|掰掰|拜拜|離開)");
    private static final Pattern OPEN_PAGE = Pattern.compile("(用按的|打開核准頁|開核准頁|用手按|核准畫面)");
    private static final Pattern LIST = Pattern.compile("(還有哪些|有什麼要我處理|有哪些待辦|待辦清單|全部列出)");

    private Gen2VoiceBrain() { }

    /** Words the app handles itself. CONFIRM/CANCEL only count while a proposal waits. */
    static Local local(String spoken, boolean proposalWaiting) {
        String text = normalize(spoken);
        if (text.isEmpty()) return Local.NONE;
        if (proposalWaiting) {
            if (CANCEL.matcher(text).find() || NEGATED.matcher(text).find()) return Local.CANCEL;
            if (text.length() <= 10 && CONFIRM.matcher(text).find()) return Local.CONFIRM;
        }
        if (OPEN_PAGE.matcher(text).find()) return Local.OPEN_PAGE;
        if (NEXT.matcher(text).find()) return Local.NEXT;
        if (END.matcher(text).find()) return Local.END;
        if (LIST.matcher(text).find()) return Local.LIST;
        return Local.NONE;
    }

    static String normalize(String spoken) {
        if (spoken == null) return "";
        return spoken.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。！？、,.!?~～…]+", "")
                .replaceAll("^(狐\\s*狸)", "");
    }

    private static final Pattern ASKS_FOR_WORK = Pattern.compile("(待核准|要我處理|我的待辦|gen2待辦|gen2流程|有什麼待辦|有沒有待辦)");

    /** "狐狸，有什麼要我處理的？" and similar: hand the turn to the GEN2 voice screen. */
    static boolean asksForPendingWork(String spoken) {
        return ASKS_FOR_WORK.matcher(normalize(spoken)).find();
    }

    /** Runs the OWNER must act on, the target issue first. */
    static List<Gen2RunView> queue(List<Gen2RunView> runs, int firstIssue) {
        List<Gen2RunView> waiting = new ArrayList<>();
        if (runs == null) return waiting;
        for (Gen2RunView run : runs) if (run.needsOwner() && run.number == firstIssue) waiting.add(run);
        for (Gen2RunView run : runs) if (run.needsOwner() && run.number != firstIssue) waiting.add(run);
        return waiting;
    }

    /** First sentence, spoken before any model call. */
    static String briefing(List<Gen2RunView> queue) {
        if (queue == null || queue.isEmpty()) return "目前沒有等你處理的 GEN2 流程。";
        Gen2RunView run = queue.get(0);
        return (queue.size() == 1 ? "有一筆等你處理。" : "有 " + queue.size() + " 筆等你處理，先看第一筆。")
                + describe(run);
    }

    static String describe(Gen2RunView run) {
        StringBuilder text = new StringBuilder();
        text.append(run.number).append(" 號，").append(run.workflowId);
        if (!run.workflowTitle.isEmpty()) text.append(" ").append(run.workflowTitle);
        if (run.pending != null) {
            text.append("，卡在 ").append(run.pending.step).append(" ").append(run.pending.title);
            text.append(run.pending.isGate() ? "，要你核准。" : "，要你提供資料。");
            if (run.pending.isGate()) text.append("選項有：").append(optionList(run.pending)).append("。");
            else if (!run.pending.prompt.isEmpty()) text.append(run.pending.prompt).append("。");
        }
        return text.toString();
    }

    private static String optionList(Gen2RunView.Pending pending) {
        StringBuilder text = new StringBuilder();
        for (Gen2RunView.Choice choice : pending.options) {
            if (text.length() > 0) text.append("、");
            text.append(choice.label);
        }
        return text.toString();
    }

    /** System prompt for the model: the current run, the rest of the queue, and the reply format. */
    static String systemPrompt(Gen2RunView current, List<Gen2RunView> queue) {
        StringBuilder text = new StringBuilder();
        text.append("你是 OWNER 的語音助理「狐狸」，用繁體中文、口語、簡短（每次 1 到 3 句）跟 OWNER 聊 GEN2 流程。\n")
            .append("你的工作：說明目前這一筆在等什麼、摘要待審內容、依內容給建議與理由，回答 OWNER 的追問。\n")
            .append("規則：\n")
            .append("1. 只根據下面提供的資料回答，資料裡沒有的就說不知道，不要編造。\n")
            .append("2. 你不能自己做決定。OWNER 明確說出要選哪個選項（或要提供什麼資料）時，才在 action 填入；App 會念給他確認後才送出。\n")
            .append("3. 選項標示 comment=required 時，comment 必須填 OWNER 說的理由；OWNER 沒講理由就先問他。\n")
            .append("4. 只輸出一個 JSON 物件，不要其他文字：\n")
            .append("{\"say\":\"要念給 OWNER 聽的話\",\"action\":null}\n")
            .append("action 可用：{\"type\":\"decide\",\"option\":\"選項id\",\"comment\":\"說明\"}、")
            .append("{\"type\":\"input\",\"text\":\"OWNER 口述的資料\"}、{\"type\":\"cancel\",\"reason\":\"原因\"}、")
            .append("{\"type\":\"next\"}（換下一筆）、{\"type\":\"end\"}（結束對話）。\n\n");
        if (current == null) {
            text.append("目前沒有等 OWNER 處理的流程。\n");
            return text.toString();
        }
        text.append("目前這一筆：\n").append(runJson(current).toString()).append("\n\n");
        if (queue != null && queue.size() > 1) {
            text.append("其他也在等 OWNER 的：\n");
            for (Gen2RunView run : queue) {
                if (run.number == current.number) continue;
                text.append("- #").append(run.number).append(" ").append(run.workflowId).append(" ")
                    .append(run.pending == null ? "" : run.pending.step + " " + run.pending.title).append("\n");
            }
        }
        return text.toString();
    }

    static JSONObject runJson(Gen2RunView run) {
        JSONObject json = new JSONObject();
        try {
            json.put("issue", run.number).put("workflow", run.workflowId + " " + run.workflowTitle)
                .put("status", run.statusText());
            Gen2RunView.Pending pending = run.pending;
            if (pending != null) {
                JSONObject p = new JSONObject().put("step", pending.step).put("type", pending.type)
                        .put("title", pending.title).put("attempt", pending.attempt);
                if (!pending.prompt.isEmpty()) p.put("prompt", pending.prompt);
                if (pending.isGate()) {
                    JSONArray options = new JSONArray();
                    for (Gen2RunView.Choice choice : pending.options) {
                        options.put(new JSONObject().put("id", choice.id).put("label", choice.label)
                                .put("comment", choice.commentRequired ? "required" : choice.commentOptional ? "optional" : "none"));
                    }
                    p.put("options", options);
                    if (!pending.reviewText.isEmpty()) {
                        p.put("review_title", pending.reviewTitle);
                        p.put("review", clip(pending.reviewText, MAX_REVIEW_CHARS));
                    }
                }
                json.put("pending", p);
            }
            JSONArray steps = new JSONArray();
            for (Gen2RunView.Step step : run.steps) {
                steps.put(new JSONObject().put("id", step.id).put("title", step.title).put("status", step.status)
                        .put("result", step.result));
            }
            json.put("steps", steps);
        } catch (Exception ignored) { }
        return json;
    }

    /** What the model said, and the one action it proposed (may be null). */
    static final class Reply {
        final String say;
        final JSONObject action;
        Reply(String say, JSONObject action) { this.say = say; this.action = action; }
    }

    static Reply parseReply(String raw) {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JSONObject json = new JSONObject(text.substring(start, end + 1));
                String say = json.optString("say", "").trim();
                JSONObject action = json.optJSONObject("action");
                return new Reply(say.isEmpty() && action == null ? text : say, action);
            } catch (Exception ignored) { }
        }
        return new Reply(text.replaceAll("```[a-z]*", "").trim(), null);
    }

    /** A command built from the model's action, waiting for the OWNER to say yes. */
    static final class Proposal {
        final Gen2RunView run;
        final String command;
        final String label;
        final String readBack;
        Proposal(Gen2RunView run, String command, String label, String readBack) {
            this.run = run;
            this.command = command;
            this.label = label;
            this.readBack = readBack;
        }
    }

    /**
     * Turns a model action into a proposal. Throws with a speakable message when
     * the action does not fit the run (unknown option, missing reason, ...).
     * Returns null for actions that are not commands (next/end).
     */
    static Proposal propose(Gen2RunView run, JSONObject action) {
        if (action == null) return null;
        String type = action.optString("type", "");
        switch (type) {
            case "decide": {
                String option = action.optString("option", "").trim();
                String comment = action.isNull("comment") ? "" : action.optString("comment", "").trim();
                String command = Gen2Command.decide(run, option, comment);
                Gen2RunView.Choice choice = run.pending.option(option);
                return new Proposal(run, command, choice.label,
                        "我要送出：" + run.number + " 號 " + run.pending.step + "，選「" + choice.label + "」"
                                + (comment.isEmpty() ? "" : "，說明是：" + comment) + "。確定嗎？");
            }
            case "input": {
                String material = action.optString("text", "").trim();
                String command = Gen2Command.input(run, material);
                return new Proposal(run, command, "送出資料",
                        "我要把這段資料送給 " + run.number + " 號 " + run.pending.step + "：" + clip(material, 200) + "。確定嗎？");
            }
            case "cancel": {
                String reason = action.optString("reason", "").trim();
                String command = Gen2Command.cancel(run, reason);
                return new Proposal(run, command, "停止這次流程",
                        "我要停止 " + run.number + " 號這次流程" + (reason.isEmpty() ? "" : "，原因：" + reason) + "。確定嗎？");
            }
            default:
                return null;
        }
    }

    /**
     * Without a model: match an option by its label or id, and use the whole
     * utterance as the reason; for an input step the utterance is the material.
     */
    static JSONObject fallbackAction(Gen2RunView run, String spoken) {
        if (run == null || run.pending == null) return null;
        String text = spoken == null ? "" : spoken.trim();
        String flat = normalize(text);
        try {
            if (run.pending.isInput()) return text.isEmpty() ? null : new JSONObject().put("type", "input").put("text", text);
            if (!run.pending.isGate()) return null;
            Gen2RunView.Choice best = null;
            int bestLength = 0;
            for (Gen2RunView.Choice choice : run.pending.options) {
                String label = normalize(choice.label);
                if (!label.isEmpty() && flat.contains(label) && label.length() > bestLength) { best = choice; bestLength = label.length(); }
            }
            if (best == null) {
                for (Gen2RunView.Choice choice : run.pending.options) {
                    String word = keyword(choice);
                    if (!word.isEmpty() && flat.contains(word)) { best = choice; break; }
                }
            }
            if (best == null) return null;
            return new JSONObject().put("type", "decide").put("option", best.id)
                    .put("comment", best.commentRequired || best.commentOptional ? text : "");
        } catch (Exception error) {
            return null;
        }
    }

    private static String keyword(Gen2RunView.Choice choice) {
        String id = choice.id;
        if (id.startsWith("approve") || id.equals("pass")) return "核准";
        if (id.startsWith("revise")) return "退回";
        if (id.startsWith("stop") || id.startsWith("reject")) return "不寫入";
        return "";
    }

    static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    static int issueFromUri(String scheme, String host, String path) {
        if (!"amin-gen2".equals(scheme) || !"run".equals(host) || path == null) return 0;
        Matcher matcher = Pattern.compile("^/([1-9][0-9]{0,6})/?$").matcher(path);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
