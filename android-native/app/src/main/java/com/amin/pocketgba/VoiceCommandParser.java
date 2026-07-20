package com.amin.pocketgba;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class VoiceCommandParser {
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
        register("開啟控制盤", simple("OVERLAY_OPEN"));
        register("打開控制盤", simple("OVERLAY_OPEN"));
        register("關閉控制盤", simple("OVERLAY_CLOSE"));
        register("收起控制盤", simple("OVERLAY_CLOSE"));
        register("游標模式", mode("cursor"));
        register("切換游標模式", mode("cursor"));
        register("捲動模式", mode("scroll"));
        register("滾動模式", mode("scroll"));
        register("切換捲動模式", mode("scroll"));
        register("返回", simple("SYSTEM_BACK"));
        register("回上一頁", simple("SYSTEM_BACK"));
        register("回首頁", simple("SYSTEM_HOME"));
        register("回到首頁", simple("SYSTEM_HOME"));
        register("點一下", simple("CURSOR_TAP"));
        register("點擊", simple("CURSOR_TAP"));
        register("長按", simple("CURSOR_LONG_PRESS"));
        register("往上", simple("DIRECTION_UP"));
        register("向上", simple("DIRECTION_UP"));
        register("往下", simple("DIRECTION_DOWN"));
        register("向下", simple("DIRECTION_DOWN"));
        register("往左", simple("DIRECTION_LEFT"));
        register("向左", simple("DIRECTION_LEFT"));
        register("往右", simple("DIRECTION_RIGHT"));
        register("向右", simple("DIRECTION_RIGHT"));
        register("開啟遊戲", simple("OPEN_GBA"));
        register("打開遊戲", simple("OPEN_GBA"));
        register("開啟遊戲庫", simple("OPEN_GBA"));
        register("開啟控制器設定", simple("OPEN_CONTROLLER_SETTINGS"));
        register("控制器設定", simple("OPEN_CONTROLLER_SETTINGS"));
        register("停止聆聽", simple("VOICE_STOP"));
        register("停止語音", simple("VOICE_STOP"));
    }

    public Result parse(String transcript, double recognizerConfidence) {
        String normalized = normalize(transcript);
        if (normalized.isEmpty()) {
            return new Result(Result.Status.NO_MATCH, null, normalized, "沒有聽到可辨識的指令");
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
                candidateAlias = entry.getKey();
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
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.TAIWAN)
                .replaceAll("[\\s，。！？、,.!?;；:：\"'「」『』（）()]", "")
                .trim();
        return value;
    }

    private void register(String alias, Factory factory) {
        aliases.put(normalize(alias), factory);
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
