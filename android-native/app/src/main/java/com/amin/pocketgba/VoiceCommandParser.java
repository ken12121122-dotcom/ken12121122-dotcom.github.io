package com.amin.pocketgba;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class VoiceCommandParser {
    public static final double MIN_CONFIDENCE = 0.45d;

    public static final class Result {
        public enum Status { MATCHED, AMBIGUOUS, NO_MATCH }

        private final Status status;
        private final AminAction action;
        private final String normalizedText;
        private final String message;

        private Result(Status status, AminAction action, String normalizedText, String message) {
            this.status = status;
            this.action = action;
            this.normalizedText = normalizedText;
            this.message = message;
        }

        public Status getStatus() { return status; }
        public AminAction getAction() { return action; }
        public String getNormalizedText() { return normalizedText; }
        public String getMessage() { return message; }
    }

    private interface Factory {
        AminAction create(double confidence);
    }

    private final Map<String, Factory> aliases = new LinkedHashMap<>();

    public VoiceCommandParser() {
        registerAliases(simple("OVERLAY_OPEN"), "開啟控制盤", "打開控制盤", "顯示控制盤");
        registerAliases(simple("OVERLAY_CLOSE"), "關閉控制盤", "收起控制盤", "隱藏控制盤");
        registerAliases(mode("cursor"), "游標模式", "切換游標模式");
        registerAliases(mode("scroll"), "捲動模式", "滾動模式", "切換捲動模式");
        registerAliases(simple("SYSTEM_BACK"), "返回", "回上一頁", "上一頁");
        registerAliases(simple("SYSTEM_HOME"), "回首頁", "回到首頁", "回桌面", "回到桌面");
        registerAliases(simple("CURSOR_TAP"), "點一下", "點擊", "按一下");
        registerAliases(simple("CURSOR_LONG_PRESS"), "長按", "按住");
        registerAliases(simple("DIRECTION_UP"), "往上", "向上");
        registerAliases(simple("DIRECTION_DOWN"), "往下", "向下");
        registerAliases(simple("DIRECTION_LEFT"), "往左", "向左");
        registerAliases(simple("DIRECTION_RIGHT"), "往右", "向右");
        registerAliases(simple("OPEN_GBA"), "開啟遊戲", "打開遊戲", "開啟遊戲庫", "打開遊戲庫");
        registerAliases(simple("OPEN_CONTROLLER_SETTINGS"), "開啟控制器設定", "控制器設定", "打開控制器設定");
        registerAliases(simple("VOICE_STOP"), "停止聆聽", "停止語音");
    }

    public Result parse(String transcript, double recognizerConfidence) {
        String normalized = normalize(transcript);
        if (normalized.isEmpty()) {
            return new Result(Result.Status.NO_MATCH, null, normalized, "沒有聽到可辨識的指令");
        }
        if (recognizerConfidence >= 0d && recognizerConfidence < MIN_CONFIDENCE) {
            return new Result(Result.Status.NO_MATCH, null, normalized, "辨識信心不足，請再說一次");
        }

        Factory exact = aliases.get(normalized);
        if (exact != null) {
            double confidence = recognizerConfidence < 0d ? 1d : recognizerConfidence;
            return new Result(Result.Status.MATCHED, exact.create(confidence), normalized, "已辨識");
        }

        Factory candidate = null;
        String candidateAlias = null;
        for (Map.Entry<String, Factory> entry : aliases.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                if (candidate != null && candidate != entry.getValue()) {
                    return new Result(Result.Status.AMBIGUOUS, null, normalized, "指令可能有多種意思，請再說一次");
                }
                candidate = entry.getValue();
                if (candidateAlias == null || entry.getKey().length() > candidateAlias.length()) {
                    candidateAlias = entry.getKey();
                }
            }
        }

        if (candidate != null) {
            double base = recognizerConfidence < 0d ? 0.82d : recognizerConfidence;
            double adjusted = Math.min(base, 0.88d);
            return new Result(Result.Status.MATCHED, candidate.create(adjusted), normalized,
                    "依照「" + candidateAlias + "」執行");
        }

        return new Result(Result.Status.NO_MATCH, null, normalized, "目前不支援這個指令");
    }

    public static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.TAIWAN)
                .replaceAll("[\\s，。！？、,.!?;；:：\"'「」『』（）()]", "")
                .trim();
    }

    private void registerAliases(Factory factory, String... values) {
        for (String value : values) {
            aliases.put(normalize(value), factory);
        }
    }

    private static Factory simple(String action) {
        return confidence -> new AminAction(action, new JSONObject(), "voice", confidence);
    }

    private static Factory mode(String mode) {
        return confidence -> {
            JSONObject parameters = new JSONObject();
            try {
                parameters.put("mode", mode);
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
            return new AminAction("CONTROL_MODE_SET", parameters, "voice", confidence);
        };
    }
}
