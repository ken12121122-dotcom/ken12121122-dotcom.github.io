package com.amin.pocketgba;

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

    private final Map<String, VoiceCommandCatalog.Command> aliases = new LinkedHashMap<>();

    public VoiceCommandParser() {
        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            for (String phrase : command.getPhrases()) {
                String normalized = normalize(phrase);
                VoiceCommandCatalog.Command previous = aliases.put(normalized, command);
                if (previous != null && !previous.getId().equals(command.getId())) {
                    throw new IllegalStateException(
                            "Duplicate voice phrase maps to multiple commands: " + phrase
                    );
                }
            }
        }
    }

    public Result parse(String transcript, double recognizerConfidence) {
        String normalized = normalize(transcript);
        if (normalized.isEmpty()) {
            return new Result(Result.Status.NO_MATCH, null, normalized, "沒有聽到可辨識的指令");
        }
        if (recognizerConfidence >= 0d && recognizerConfidence < MIN_CONFIDENCE) {
            return new Result(Result.Status.NO_MATCH, null, normalized, "辨識信心不足，請再說一次");
        }

        VoiceCommandCatalog.Command exact = aliases.get(normalized);
        if (exact != null) {
            double confidence = recognizerConfidence < 0d ? 1d : recognizerConfidence;
            return new Result(Result.Status.MATCHED, exact.createAction(confidence), normalized, "已辨識");
        }

        VoiceCommandCatalog.Command candidate = null;
        String candidateAlias = null;
        for (Map.Entry<String, VoiceCommandCatalog.Command> entry : aliases.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                if (candidate != null && !candidate.getId().equals(entry.getValue().getId())) {
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
            return new Result(
                    Result.Status.MATCHED,
                    candidate.createAction(adjusted),
                    normalized,
                    "依照「" + candidateAlias + "」執行"
            );
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
}
