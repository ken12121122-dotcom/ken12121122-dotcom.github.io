package com.amin.pocketgba;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VoiceCommandParser {
    public static final double MIN_CONFIDENCE = 0.45d;

    public static final class Result {
        public enum Status { MATCHED, AMBIGUOUS, NO_MATCH }

        private final Status status;
        private final AminAction action;
        private final VoiceCommandCatalog.Command command;
        private final String normalizedText;
        private final String message;

        private Result(Status status, AminAction action, VoiceCommandCatalog.Command command, String normalizedText, String message) {
            this.status = status;
            this.action = action;
            this.command = command;
            this.normalizedText = normalizedText;
            this.message = message;
        }

        public Status getStatus() { return status; }
        public AminAction getAction() { return action; }
        public VoiceCommandCatalog.Command getCommand() { return command; }
        public String getNormalizedText() { return normalizedText; }
        public String getMessage() { return message; }
    }

    public static final class ScanResult {
        private final Result result;
        private final List<String> candidates;
        private ScanResult(Result result, List<String> candidates) {
            this.result = result;
            this.candidates = candidates;
        }
        public Result getResult() { return result; }
        public List<String> getCandidates() { return candidates; }
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
        return scan(transcript, recognizerConfidence).getResult();
    }

    public ScanResult scan(String transcript, double recognizerConfidence) {
        String normalized = normalize(transcript);
        List<String> scanned = new ArrayList<>();
        if (normalized.isEmpty()) {
            return new ScanResult(new Result(Result.Status.NO_MATCH, null, null, normalized, "沒有聽到可辨識的指令"), scanned);
        }
        if (recognizerConfidence >= 0d && recognizerConfidence < MIN_CONFIDENCE) {
            return new ScanResult(new Result(Result.Status.NO_MATCH, null, null, normalized, "辨識信心不足，請再說一次"), scanned);
        }

        VoiceCommandCatalog.Command exact = aliases.get(normalized);
        if (exact != null) {
            scanned.add(exact.getTitle() + " · " + exact.getPrimaryPhrase());
            double confidence = recognizerConfidence < 0d ? 1d : recognizerConfidence;
            return new ScanResult(new Result(Result.Status.MATCHED, exact.createAction(confidence), exact, normalized, "已辨識"), scanned);
        }

        VoiceCommandCatalog.Command candidate = null;
        String candidateAlias = null;
        for (Map.Entry<String, VoiceCommandCatalog.Command> entry : aliases.entrySet()) {
            VoiceCommandCatalog.Command current = entry.getValue();
            scanned.add(current.getTitle() + " · " + entry.getKey());
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                if (candidate != null && !candidate.getId().equals(current.getId())) {
                    return new ScanResult(new Result(Result.Status.AMBIGUOUS, null, null, normalized, "指令可能有多種意思，請再說一次"), scanned);
                }
                candidate = current;
                if (candidateAlias == null || entry.getKey().length() > candidateAlias.length()) {
                    candidateAlias = entry.getKey();
                }
            }
        }

        if (candidate != null) {
            double base = recognizerConfidence < 0d ? 0.82d : recognizerConfidence;
            double adjusted = Math.min(base, 0.88d);
            return new ScanResult(new Result(
                    Result.Status.MATCHED,
                    candidate.createAction(adjusted),
                    candidate,
                    normalized,
                    "依照「" + candidateAlias + "」執行"
            ), scanned);
        }

        return new ScanResult(new Result(Result.Status.NO_MATCH, null, null, normalized, "目前不支援這個指令"), scanned);
    }

    public static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.TAIWAN)
                .replaceAll("[\\s，。！？、,.!?;；:：\"'「」『』（）()]", "")
                .trim();
    }
}
