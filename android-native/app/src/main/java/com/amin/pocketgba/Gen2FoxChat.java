package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the fox chat Issue (label gen2-chat) of gen2-knowledge (pure, no I/O).
 *
 * Bridge 105: the fox's brain runs in GitHub Actions on the OWNER's Claude
 * subscription (gen2-knowledge scripts/gen2-fox.mjs). The app only posts the
 * OWNER's words, shows the fox's replies, and posts "/fox confirm|reject" for
 * a plan card. The fox's long-term memory never reaches the phone.
 */
final class Gen2FoxChat {
    static final String LABEL = "gen2-chat";
    static final String BOT = "github-actions[bot]";
    static final int MAX_TEXT = 6000;
    private static final Pattern MARK = Pattern.compile("<!--\\s*gen2-fox:([A-Za-z0-9+/=]+)\\s*-->");
    static final Pattern PLAN_ID = Pattern.compile("p[0-9a-z]{4,16}");

    private Gen2FoxChat() { }

    static final class Turn {
        final long id;
        final boolean fox;
        final String text;
        final String planId;     // fox: a new proposal
        final String readBack;   // fox: what the proposal will do
        final String resolved;   // fox: a proposal started, dropped or replaced
        final String note;       // fox: system note (started #N, errors)

        Turn(long id, boolean fox, String text, String planId, String readBack, String resolved, String note) {
            this.id = id;
            this.fox = fox;
            this.text = text;
            this.planId = planId;
            this.readBack = readBack;
            this.resolved = resolved;
            this.note = note;
        }
    }

    /** The plan the fox proposed that is still waiting for the OWNER. */
    static final class Pending {
        final String planId;
        final String readBack;
        Pending(String planId, String readBack) { this.planId = planId; this.readBack = readBack; }
    }

    /** OWNER comments (unedited) and fox replies, in order. */
    static List<Turn> turns(JSONArray comments, String owner) {
        List<Turn> out = new ArrayList<>();
        for (int i = 0; comments != null && i < comments.length(); i++) {
            JSONObject c = comments.optJSONObject(i);
            if (c == null) continue;
            String login = c.optJSONObject("user") == null ? "" : c.optJSONObject("user").optString("login", "");
            long id = c.optLong("id", 0L);
            String body = c.optString("body", "");
            if (BOT.equals(login)) {
                JSONObject data = data(body);
                if (data == null) continue;
                JSONObject resolved = data.optJSONObject("resolved");
                out.add(new Turn(id, true, data.optString("say", ""), data.has("plan") ? data.optString("planId", null) : null,
                        data.optString("readBack", ""), resolved == null ? null : resolved.optString("planId", null), data.optString("note", "")));
            } else if (owner.equalsIgnoreCase(login) && c.optString("updated_at", "").equals(c.optString("created_at", ""))) {
                out.add(new Turn(id, false, body.trim(), null, "", null, ""));
            }
        }
        return out;
    }

    static JSONObject data(String body) {
        Matcher m = MARK.matcher(body == null ? "" : body);
        if (!m.find()) return null;
        try {
            JSONObject d = new JSONObject(new String(java.util.Base64.getDecoder().decode(m.group(1)), StandardCharsets.UTF_8));
            return d.optInt("v", 0) == 1 ? d : null;
        } catch (Exception error) {
            return null;
        }
    }

    static Pending pending(List<Turn> turns) {
        java.util.Set<String> resolved = new java.util.HashSet<>();
        for (int i = turns.size() - 1; i >= 0; i--) {
            Turn t = turns.get(i);
            if (!t.fox) continue;
            if (t.resolved != null) resolved.add(t.resolved);
            if (t.planId != null) return resolved.contains(t.planId) ? null : new Pending(t.planId, t.readBack);
        }
        return null;
    }

    /** The fox's reply to the OWNER comment with this id, or null while it is thinking. */
    static Turn replyAfter(List<Turn> turns, long ownerCommentId) {
        for (Turn t : turns) if (t.fox && t.id > ownerCommentId) return t;
        return null;
    }

    static String confirm(String planId) { return "/fox confirm " + checked(planId); }
    static String reject(String planId) { return "/fox reject " + checked(planId); }

    private static String checked(String planId) {
        if (planId == null || !PLAN_ID.matcher(planId).matches()) throw new IllegalArgumentException("計畫編號無效。 ");
        return planId;
    }

    static final Pattern MEMORY_ID = Pattern.compile("m\\d{1,6}");

    static String forget(String memoryId) {
        if (memoryId == null || !MEMORY_ID.matcher(memoryId).matches()) throw new IllegalArgumentException("記憶編號無效。 ");
        return "/fox forget " + memoryId;
    }

    static String notebookTitle(String title) {
        String value = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("請幫筆記本取個名字。 ");
        if (value.length() > 60) throw new IllegalArgumentException("名字太長了（上限 60 字）。 ");
        return value;
    }

    /** A notebook as the app lists it. */
    static final class Notebook {
        final int number;
        final String title;
        Notebook(int number, String title) { this.number = number; this.title = title; }
    }

    static List<Notebook> notebooks(JSONArray issues) {
        List<Notebook> out = new ArrayList<>();
        for (int i = 0; issues != null && i < issues.length(); i++) {
            JSONObject issue = issues.optJSONObject(i);
            if (issue == null || issue.has("pull_request") || issue.optInt("number", 0) <= 0) continue;
            out.add(new Notebook(issue.optInt("number"), issue.optString("title", "").replaceFirst("^🦊\\s*", "")));
        }
        return out;
    }

    /** A memory item the OWNER can see and forget. */
    static final class Memory {
        final String id;
        final String kind;
        final String text;
        final boolean shared;
        Memory(String id, String kind, String text, boolean shared) { this.id = id; this.kind = kind; this.text = text; this.shared = shared; }

        String label() {
            String k;
            switch (kind) {
                case "profile": k = "背景"; break;
                case "preference": k = "偏好"; break;
                case "goal": k = "目標"; break;
                case "thread": k = "待辦"; break;
                default: k = "事實";
            }
            return (shared ? "（共用）" : "") + k + "：" + text;
        }
    }

    /** What the fox remembers in one notebook: shared items and that notebook's own. */
    static List<Memory> memoryFor(JSONObject memory, int notebook) {
        List<Memory> out = new ArrayList<>();
        JSONArray items = memory == null ? null : memory.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !MEMORY_ID.matcher(item.optString("id", "")).matches()) continue;
            String kind = item.optString("kind", "fact");
            boolean shared = !item.has("chat") || "profile".equals(kind) || "preference".equals(kind) || "goal".equals(kind);
            if (shared || item.optInt("chat", 0) == notebook) out.add(new Memory(item.optString("id"), kind, item.optString("text", ""), shared));
        }
        return out;
    }

    static String notebookSummary(JSONObject memory, int notebook) {
        JSONObject books = memory == null ? null : memory.optJSONObject("notebooks");
        JSONObject book = books == null ? null : books.optJSONObject(String.valueOf(notebook));
        return book == null ? "" : book.optString("summary", "");
    }

    /** What the OWNER may post: plain words, never a /gen2 run command. */
    static String message(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("沒有內容。 ");
        if (value.length() > MAX_TEXT) throw new IllegalArgumentException("太長了（上限 " + MAX_TEXT + " 字）。 ");
        if (value.startsWith("/gen2")) throw new IllegalArgumentException("聊天裡不能下 /gen2 指令。 ");
        return value;
    }
}
