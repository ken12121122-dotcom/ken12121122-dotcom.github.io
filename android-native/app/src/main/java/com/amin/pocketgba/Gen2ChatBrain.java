package com.amin.pocketgba;

import org.json.JSONObject;

import java.util.List;

/**
 * The fox assistant's conversation rules (pure, no Android).
 *
 * The model reads the OWNER's words and decides what they mean: a question, a
 * new task, an answer to a waiting run, agreement, or a change of mind. There
 * are no keyword lists. Two things stay with the app, not the model:
 *
 * - Anything that changes GitHub is first turned into a Proposal whose
 *   read-back the app writes from the validated action, and is shown to the
 *   OWNER as a card.
 * - A proposal runs only after a later OWNER message that the model judges to
 *   be agreement to that read-back ("confirm":"yes"). A proposal never runs in
 *   the turn that created it.
 *
 * Task intake follows the GEN2 conversational intake rules: answer first, ask
 * at most one natural follow-up at a time, and stop asking once the scope and
 * the material are clear.
 */
final class Gen2ChatBrain {
    static final int MAX_RUNS_IN_PROMPT = 8;
    static final int MAX_SCOPE = 1500;

    private Gen2ChatBrain() { }

    static String systemPrompt(List<Gen2WorkflowCatalog.Workflow> workflows, List<Gen2RunView> runs,
                               Gen2RunView focus, Proposal waiting) {
        StringBuilder text = new StringBuilder();
        text.append("你是 OWNER 的夥伴「狐狸」，在 Amin 知識世界裡用繁體中文、口語、簡短（每次 1 到 3 句）跟 OWNER 聊天。\n")
            .append("你負責：聽懂 OWNER 想做什麼、把任務範圍聊清楚、交給 GEN2 流程去做，以及說明流程現在的狀況。\n\n")
            .append("判斷方式：\n")
            .append("1. 自己理解 OWNER 的意思，不要等特定關鍵字。問問題就回答；描述要做的事就當作任務；回應等待中的流程就處理那一筆。\n")
            .append("2. 任務：從下面的「可以啟動的流程」挑最合適的一個。先確認範圍：要做什麼、做到什麼程度算完成、需要的資料。")
            .append("一次最多問一件事；資訊夠了就不要再問。沒有合適的流程就直說，不要硬套。\n")
            .append("3. 只根據對話與下面的資料回答，不知道就說不知道，不要編造日期、時間或內容。\n")
            .append("4. 範圍與資料都清楚時，在 action 提出動作，並在 say 用一句話說你理解的內容、問 OWNER 要不要開始。\n")
            .append("5. 「等待 OWNER 同意的動作」存在時，判斷 OWNER 最新這句話：明確同意就填 \"confirm\":\"yes\"；")
            .append("拒絕、想修改或還在猶豫就填 \"no\"（想修改就同時提出新的 action）；跟這件事無關就填 null。")
            .append("不確定就填 null 並問清楚，絕不要猜同意。\n")
            .append("6. 你不能替 OWNER 做 Gate 決定；OWNER 明確表達選哪個選項時才提出 decide。選項要求說明時，說明要來自 OWNER 的話。\n\n")
            .append("只輸出一個 JSON 物件，不要其他文字：\n")
            .append("{\"say\":\"對 OWNER 說的話\",\"confirm\":null,\"action\":null}\n")
            .append("action 可用：\n")
            .append("- {\"type\":\"start\",\"workflow\":\"WF-…\",\"scope\":\"一段話說明這次要做什麼、做到哪裡算完成\",\"material\":\"第一步需要的資料（OWNER 給的原文）\"}\n")
            .append("- {\"type\":\"decide\",\"issue\":編號,\"option\":\"選項id\",\"comment\":\"說明\"}\n")
            .append("- {\"type\":\"input\",\"issue\":編號,\"text\":\"OWNER 提供的資料\"}\n")
            .append("- {\"type\":\"cancel\",\"issue\":編號,\"reason\":\"原因\"}\n\n");

        text.append("可以啟動的流程：\n");
        if (workflows == null || workflows.isEmpty()) text.append("（目前讀不到）\n");
        else for (Gen2WorkflowCatalog.Workflow workflow : workflows) text.append(workflow.toJson().toString()).append("\n");

        text.append("\n進行中的流程：\n");
        int shown = 0;
        if (runs != null) for (Gen2RunView run : runs) {
            if (shown++ >= MAX_RUNS_IN_PROMPT) break;
            text.append(Gen2VoiceBrain.runJson(run).toString()).append("\n");
        }
        if (shown == 0) text.append("（沒有）\n");
        if (focus != null) text.append("\nOWNER 是從 #").append(focus.number).append(" 進來的，先談這一筆。\n");
        if (waiting != null) {
            text.append("\n等待 OWNER 同意的動作（App 已顯示並念給他）：").append(waiting.readBack).append("\n");
        }
        return text.toString();
    }

    /** What the model said, whether it judged agreement to the waiting proposal, and a new action. */
    static final class Reply {
        final String say;
        final Boolean confirm;
        final JSONObject action;
        Reply(String say, Boolean confirm, JSONObject action) { this.say = say; this.confirm = confirm; this.action = action; }
    }

    static Reply parseReply(String raw) {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JSONObject json = new JSONObject(text.substring(start, end + 1));
                Object c = json.opt("confirm");
                Boolean confirm = null;
                if ("yes".equals(c) || Boolean.TRUE.equals(c)) confirm = Boolean.TRUE;
                else if ("no".equals(c) || Boolean.FALSE.equals(c)) confirm = Boolean.FALSE;
                JSONObject action = json.optJSONObject("action");
                String say = json.optString("say", "").trim();
                return new Reply(say, confirm, action);
            } catch (Exception ignored) { }
        }
        return new Reply(text.replaceAll("```[a-z]*", "").trim(), null, null);
    }

    /** A validated GitHub change waiting for the OWNER's agreement. */
    static final class Proposal {
        final String type;
        final Gen2RunView run;                      // decide / input / cancel
        final Gen2WorkflowCatalog.Workflow workflow; // start
        final String command;                       // /gen2 comment for decide / input / cancel
        final String scope;                         // start: run note
        final String material;                      // start: first input, may be empty
        final String title;
        final String readBack;

        private Proposal(String type, Gen2RunView run, Gen2WorkflowCatalog.Workflow workflow, String command,
                         String scope, String material, String title, String readBack) {
            this.type = type;
            this.run = run;
            this.workflow = workflow;
            this.command = command;
            this.scope = scope;
            this.material = material;
            this.title = title;
            this.readBack = readBack;
        }

        boolean isStart() { return "start".equals(type); }
    }

    /**
     * Turns a model action into a proposal, or throws with a message the fox
     * can say (unknown workflow, missing material, step not waiting...).
     */
    static Proposal propose(JSONObject action, List<Gen2WorkflowCatalog.Workflow> workflows, List<Gen2RunView> runs) {
        if (action == null) return null;
        String type = action.optString("type", "");
        if ("start".equals(type)) {
            Gen2WorkflowCatalog.Workflow workflow = Gen2WorkflowCatalog.find(workflows, action.optString("workflow", ""));
            if (workflow == null) throw new IllegalArgumentException("我找不到這個流程，可能還沒有可執行的定義。");
            String scope = clean(action.optString("scope", ""));
            if (scope.isEmpty()) throw new IllegalArgumentException("要先說清楚這次的範圍。");
            if (scope.length() > MAX_SCOPE) throw new IllegalArgumentException("範圍說明太長，請精簡一點。");
            String material = clean(action.isNull("material") ? "" : action.optString("material", ""));
            if (material.length() > Gen2Command.MAX_TEXT) throw new IllegalArgumentException("資料太長（上限 " + Gen2Command.MAX_TEXT + " 字）。");
            if (workflow.startsWithInput() && material.isEmpty()) {
                throw new IllegalArgumentException("開始前還需要第一步的資料：" + (workflow.firstPrompt.isEmpty() ? "請提供要處理的內容" : workflow.firstPrompt));
            }
            String readBack = "開始「" + workflow.id + "｜" + workflow.title + "」。範圍：" + Gen2VoiceBrain.clip(scope, 300)
                    + (material.isEmpty() ? "" : "。第一步交給它的資料：" + Gen2VoiceBrain.clip(material, 300));
            return new Proposal("start", null, workflow, null, scope, material, "開始新任務", readBack);
        }
        if (!"decide".equals(type) && !"input".equals(type) && !"cancel".equals(type)) return null;
        Gen2RunView run = findRun(runs, action.optInt("issue", 0));
        if (run == null) throw new IllegalArgumentException("找不到 #" + action.optInt("issue", 0) + " 這一筆進行中的流程。");
        Gen2VoiceBrain.Proposal inner = Gen2VoiceBrain.propose(run, action);
        if (inner == null) return null;
        String readBack = inner.readBack.replaceFirst("。確定嗎？$", "").replaceFirst("^我要", "");
        return new Proposal(type, run, null, inner.command, "", "", inner.label, readBack);
    }

    static Gen2RunView findRun(List<Gen2RunView> runs, int issue) {
        if (runs == null || issue <= 0) return null;
        for (Gen2RunView run : runs) if (run.number == issue) return run;
        return null;
    }

    /** The newest run of this workflow that did not exist before the start (issue numbers only grow). */
    static Gen2RunView startedRun(List<Gen2RunView> runs, String workflowId, int highestBefore) {
        Gen2RunView best = null;
        if (runs != null) for (Gen2RunView run : runs) {
            if (run.number > highestBefore && workflowId.equals(run.workflowId) && (best == null || run.number > best.number)) best = run;
        }
        return best;
    }

    static int highest(List<Gen2RunView> runs) {
        int max = 0;
        if (runs != null) for (Gen2RunView run : runs) max = Math.max(max, run.number);
        return max;
    }

    /** Opening line: what waits for the OWNER, or an invitation to talk. */
    static String greeting(List<Gen2RunView> runs, Gen2RunView focus) {
        if (focus != null) {
            if (focus.needsOwner()) return "主人，我們來看 #" + focus.number + "。" + Gen2VoiceBrain.describe(focus);
            if (focus.pending != null) return "主人，#" + focus.number + " 正在 " + focus.pending.step + "「" + focus.pending.title + "」，Agent 在處理，好了我會來找你。還有別的事嗎？";
            return "主人，#" + focus.number + " 已經結束了。有什麼新的事要交給我嗎？";
        }
        int waiting = 0;
        if (runs != null) for (Gen2RunView run : runs) if (run.needsOwner()) waiting++;
        if (waiting > 0) return "主人，有 " + waiting + " 件事在等你。想先聊哪一件，還是有新的事要交給我？";
        return "主人，有什麼想交給我的嗎？直接說就好，我會先跟你確認範圍再去做。";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").trim();
    }
}
