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

    /** What the OWNER may post: plain words, never a /gen2 run command. */
    static String message(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("沒有內容。 ");
        if (value.length() > MAX_TEXT) throw new IllegalArgumentException("太長了（上限 " + MAX_TEXT + " 字）。 ");
        if (value.startsWith("/gen2")) throw new IllegalArgumentException("聊天裡不能下 /gen2 指令。 ");
        return value;
    }
}
