package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only model of a GEN2 run Issue in ken12121122-dotcom/gen2-knowledge.
 *
 * The run engine writes a display copy of the run (format gen2-run-view v1) into
 * the Issue body. The app only uses it to show what is waiting; every decision
 * is posted as a /gen2 comment and re-validated by the engine, so a stale or
 * edited view can never approve anything by itself.
 */
final class Gen2RunView {
    static final String ENGINE_LOGIN = "github-actions[bot]";
    private static final Pattern VIEW = Pattern.compile("<!--\\s*gen2-run-view:([A-Za-z0-9+/=]+)\\s*-->");
    static final Pattern STEP_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,31}");
    static final Pattern CHOICE_ID = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private static final int MAX_TEXT = 4000;

    final int number;
    final String htmlUrl;
    final String issueTitle;
    final String runId;
    final String workflowId;
    final String workflowTitle;
    final String status;
    final String current;
    final int seq;
    final Pending pending;
    final List<Step> steps;

    private Gen2RunView(int number, String htmlUrl, String issueTitle, String runId, String workflowId,
                        String workflowTitle, String status, String current, int seq, Pending pending,
                        List<Step> steps) {
        this.number = number;
        this.htmlUrl = htmlUrl;
        this.issueTitle = issueTitle;
        this.runId = runId;
        this.workflowId = workflowId;
        this.workflowTitle = workflowTitle;
        this.status = status;
        this.current = current;
        this.seq = seq;
        this.pending = pending;
        this.steps = steps;
    }

    /** True when the run is waiting for the OWNER (a gate decision or input material). */
    boolean needsOwner() {
        return pending != null && ("waiting_owner".equals(status) || "waiting_input".equals(status));
    }

    /** Stable key for "this step of this run is waiting", used to notify only once per wait. */
    String waitKey() {
        return runId + "|" + current + "|" + (pending == null ? 0 : pending.attempt) + "|" + status;
    }

    String statusText() {
        switch (status) {
            case "waiting_owner": return "等待你核准";
            case "waiting_input": return "等待你提供資料";
            case "waiting_skill": return "等待 Agent 執行";
            case "done": return "已完成";
            case "stopped": return "已停止";
            default: return status;
        }
    }

    /** Parses the GitHub issues list; skips anything that is not a run opened by the engine. */
    static List<Gen2RunView> fromIssues(JSONArray issues) {
        List<Gen2RunView> runs = new ArrayList<>();
        if (issues == null) return runs;
        for (int index = 0; index < issues.length(); index++) {
            Gen2RunView run = fromIssue(issues.optJSONObject(index));
            if (run != null) runs.add(run);
        }
        Collections.sort(runs, (a, b) -> {
            if (a.needsOwner() != b.needsOwner()) return a.needsOwner() ? -1 : 1;
            return Integer.compare(b.number, a.number);
        });
        return runs;
    }

    static Gen2RunView fromIssue(JSONObject issue) {
        if (issue == null || issue.has("pull_request")) return null;
        JSONObject user = issue.optJSONObject("user");
        if (user == null || !ENGINE_LOGIN.equals(user.optString("login", ""))) return null;
        if (!hasLabel(issue, "gen2-run")) return null;
        JSONObject view = decodeView(issue.optString("body", ""));
        if (view == null) return null;
        int number = issue.optInt("number", 0);
        if (number <= 0) return null;
        JSONObject workflow = view.optJSONObject("workflow");
        String workflowId = workflow == null ? "" : workflow.optString("id", "");
        String status = view.optString("status", "");
        if (status.isEmpty()) return null;
        Pending pending = Pending.from(view.optJSONObject("pending"));
        String current = view.isNull("current") ? "" : view.optString("current", "");
        if (pending != null && !pending.step.equals(current)) pending = null;
        List<Step> steps = new ArrayList<>();
        JSONArray rawSteps = view.optJSONArray("steps");
        if (rawSteps != null) {
            for (int index = 0; index < rawSteps.length() && index < 50; index++) {
                JSONObject s = rawSteps.optJSONObject(index);
                if (s == null) continue;
                steps.add(new Step(s.optString("id", ""), s.optString("type", ""), clip(s.optString("title", "")),
                        s.optString("status", ""), s.isNull("result") ? "" : clip(s.optString("result", ""))));
            }
        }
        return new Gen2RunView(number, safeUrl(issue.optString("html_url", "")), clip(issue.optString("title", "")),
                view.optString("runId", ""), workflowId,
                workflow == null ? "" : clip(workflow.optString("title", "")), status, current,
                view.optInt("seq", 0), pending, Collections.unmodifiableList(steps));
    }

    static JSONObject decodeView(String body) {
        Matcher matcher = VIEW.matcher(body == null ? "" : body);
        if (!matcher.find()) return null;
        try {
            JSONObject view = new JSONObject(new String(Base64.getDecoder().decode(matcher.group(1)),
                    StandardCharsets.UTF_8));
            if (!"gen2-run-view".equals(view.optString("format", "")) || view.optInt("version", -1) != 1) return null;
            return view;
        } catch (Exception error) {
            return null;
        }
    }

    private static boolean hasLabel(JSONObject issue, String name) {
        JSONArray labels = issue.optJSONArray("labels");
        if (labels == null) return false;
        for (int index = 0; index < labels.length(); index++) {
            JSONObject label = labels.optJSONObject(index);
            if (label != null && name.equals(label.optString("name", ""))) return true;
        }
        return false;
    }

    private static String clip(String value) {
        if (value == null) return "";
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) + "…" : value;
    }

    private static String safeUrl(String url) {
        return url != null && url.startsWith("https://github.com/") ? url : "";
    }

    static final class Pending {
        final String step;
        final String type;
        final String title;
        final String prompt;
        final String skill;
        final int attempt;
        final List<Choice> options;
        final String reviewTitle;
        final String reviewText;
        final String reviewUrl;

        private Pending(String step, String type, String title, String prompt, String skill, int attempt,
                        List<Choice> options, String reviewTitle, String reviewText, String reviewUrl) {
            this.step = step;
            this.type = type;
            this.title = title;
            this.prompt = prompt;
            this.skill = skill;
            this.attempt = attempt;
            this.options = options;
            this.reviewTitle = reviewTitle;
            this.reviewText = reviewText;
            this.reviewUrl = reviewUrl;
        }

        boolean isGate() { return "gate".equals(type); }
        boolean isInput() { return "input".equals(type); }

        Choice option(String id) {
            for (Choice choice : options) if (choice.id.equals(id)) return choice;
            return null;
        }

        static Pending from(JSONObject value) {
            if (value == null) return null;
            String step = value.optString("step", "");
            String type = value.optString("type", "");
            if (!STEP_ID.matcher(step).matches()) return null;
            if (!("gate".equals(type) || "input".equals(type) || "skill".equals(type))) return null;
            List<Choice> options = new ArrayList<>();
            JSONArray raw = value.optJSONArray("options");
            if ("gate".equals(type)) {
                if (raw == null) return null;
                for (int index = 0; index < raw.length() && index < 8; index++) {
                    JSONObject o = raw.optJSONObject(index);
                    if (o == null || !CHOICE_ID.matcher(o.optString("id", "")).matches()) continue;
                    String rule = o.isNull("comment") ? "" : o.optString("comment", "");
                    options.add(new Choice(o.optString("id"), clip(o.optString("label", o.optString("id"))),
                            "required".equals(rule), "optional".equals(rule)));
                }
                if (options.size() < 2) return null;
            }
            String reviewTitle = "", reviewText = "", reviewUrl = "";
            JSONObject review = value.optJSONObject("review");
            if (review != null) {
                reviewTitle = clip(review.optString("step", "") + "｜" + review.optString("title", ""));
                JSONObject output = review.optJSONObject("output");
                if (output != null) {
                    reviewText = clip(output.optString("text", ""));
                    reviewUrl = safeUrl(output.optString("url", ""));
                }
            }
            return new Pending(step, type, clip(value.optString("title", step)), clip(value.optString("prompt", "")),
                    value.optString("skill", ""), Math.max(1, value.optInt("attempt", 1)),
                    Collections.unmodifiableList(options), reviewTitle, reviewText, reviewUrl);
        }
    }

    static final class Choice {
        final String id;
        final String label;
        final boolean commentRequired;
        final boolean commentOptional;

        Choice(String id, String label, boolean commentRequired, boolean commentOptional) {
            this.id = id;
            this.label = label;
            this.commentRequired = commentRequired;
            this.commentOptional = commentOptional;
        }
    }

    static final class Step {
        final String id;
        final String type;
        final String title;
        final String status;
        final String result;

        Step(String id, String type, String title, String status, String result) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.status = status;
            this.result = result;
        }
    }
}
