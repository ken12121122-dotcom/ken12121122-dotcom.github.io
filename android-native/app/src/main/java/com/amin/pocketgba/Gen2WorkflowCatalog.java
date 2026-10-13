package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflows the fox can start: GEN2 Workflow files on gen2-knowledge main
 * that carry a ```gen2-run block. Only what the conversation needs is kept
 * (id, title, purpose, first step); the run engine re-reads and re-validates
 * the approved definition when the run starts.
 */
final class Gen2WorkflowCatalog {
    static final int MAX_WORKFLOWS = 30;
    private static final Pattern WORKFLOW_PATH = Pattern.compile("^kb/(?!.*_KB_TEMPLATE/).*/04_WORKFLOWS/WF-[A-Z0-9-]+[^/]*\\.md$");
    static final Pattern WORKFLOW_ID = Pattern.compile("WF-[A-Z0-9]+(?:-[A-Z0-9]+)*");
    private static final Pattern FRONTMATTER = Pattern.compile("\\A---\\r?\\n([\\s\\S]*?)\\r?\\n---");
    private static final Pattern RUN_BLOCK = Pattern.compile("```gen2-run\\s*\\n([\\s\\S]*?)\\n```");

    private Gen2WorkflowCatalog() { }

    static final class Workflow {
        final String id;
        final String title;
        final String purpose;
        final String firstType;
        final String firstPrompt;

        Workflow(String id, String title, String purpose, String firstType, String firstPrompt) {
            this.id = id;
            this.title = title;
            this.purpose = purpose;
            this.firstType = firstType;
            this.firstPrompt = firstPrompt;
        }

        boolean startsWithInput() { return "input".equals(firstType); }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id).put("title", title).put("purpose", purpose)
                    .put("first_step", new JSONObject().put("type", firstType).put("prompt", firstPrompt));
            } catch (Exception ignored) { }
            return json;
        }

        static Workflow fromJson(JSONObject json) {
            JSONObject first = json.optJSONObject("first_step");
            return new Workflow(json.optString("id"), json.optString("title"), json.optString("purpose"),
                    first == null ? "" : first.optString("type"), first == null ? "" : first.optString("prompt"));
        }
    }

    /** Workflow file paths in a git tree listing (GET git/trees/main?recursive=1). */
    static List<String> workflowPaths(JSONObject tree) {
        List<String> paths = new ArrayList<>();
        JSONArray entries = tree == null ? null : tree.optJSONArray("tree");
        if (entries == null) return paths;
        for (int i = 0; i < entries.length() && paths.size() < MAX_WORKFLOWS; i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null || !"blob".equals(entry.optString("type"))) continue;
            String path = entry.optString("path");
            if (WORKFLOW_PATH.matcher(path).matches() && !path.contains("..")) paths.add(path);
        }
        Collections.sort(paths);
        return paths;
    }

    /** One Workflow Markdown file, or null when it has no valid gen2-run block. */
    static Workflow parse(String markdown) {
        if (markdown == null) return null;
        String text = markdown.replace("\r\n", "\n");
        Matcher front = FRONTMATTER.matcher(text);
        if (!front.find()) return null;
        String id = field(front.group(1), "workflow_id");
        if (!WORKFLOW_ID.matcher(id).matches()) return null;
        if (!"workflow".equals(field(front.group(1), "document_type"))) return null;
        Matcher block = RUN_BLOCK.matcher(text);
        if (!block.find()) return null;
        JSONObject spec;
        try { spec = new JSONObject(block.group(1)); } catch (Exception error) { return null; }
        JSONArray steps = spec.optJSONArray("steps");
        if (steps == null || steps.length() == 0) return null;
        String start = spec.optString("start", "");
        JSONObject first = steps.optJSONObject(0);
        for (int i = 0; i < steps.length() && !start.isEmpty(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null && start.equals(step.optString("id"))) { first = step; break; }
        }
        if (first == null) return null;
        String body = text.substring(front.end());
        String title = heading(body, id);
        return new Workflow(id, title, Gen2VoiceBrain.clip(section(body, "目的"), 300),
                first.optString("type", ""), Gen2VoiceBrain.clip(first.optString("prompt", ""), 300));
    }

    static JSONArray toJson(List<Workflow> workflows) {
        JSONArray array = new JSONArray();
        for (Workflow workflow : workflows) array.put(workflow.toJson());
        return array;
    }

    static List<Workflow> fromJson(JSONArray array) {
        List<Workflow> workflows = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            Workflow workflow = Workflow.fromJson(json);
            if (WORKFLOW_ID.matcher(workflow.id).matches()) workflows.add(workflow);
        }
        return workflows;
    }

    static Workflow find(List<Workflow> workflows, String id) {
        if (workflows == null || id == null) return null;
        for (Workflow workflow : workflows) if (workflow.id.equals(id.trim())) return workflow;
        return null;
    }

    private static String field(String frontmatter, String name) {
        Matcher m = Pattern.compile("(?m)^" + name + ":\\s*(.*?)\\s*$").matcher(frontmatter);
        return m.find() ? m.group(1).replaceAll("^[\"']|[\"']$", "").trim() : "";
    }

    private static String heading(String body, String id) {
        Matcher m = Pattern.compile("(?m)^#\\s+(.+)$").matcher(body);
        if (!m.find()) return id;
        return m.group(1).trim().replaceFirst("^" + Pattern.quote(id) + "\\s*[｜|]\\s*", "");
    }

    private static String section(String body, String name) {
        Matcher m = Pattern.compile("(?m)^##\\s+" + Pattern.quote(name) + "\\s*\\n([\\s\\S]*?)(?=^##\\s|\\z)").matcher(body);
        return m.find() ? m.group(1).trim().replaceAll("\\s+", " ") : "";
    }
}
