package com.amin.pocketgba;

final class FloatingVoicePresentation {
    enum Phase {
        IDLE,
        LISTENING,
        PROCESSING,
        SUCCESS,
        ERROR
    }

    private FloatingVoicePresentation() { }

    static String bubbleText(Phase phase) {
        if (phase == null) return "🎤";
        switch (phase) {
            case LISTENING:
                return "...";
            case PROCESSING:
                return "…";
            case SUCCESS:
                return "✓";
            case ERROR:
                return "!";
            case IDLE:
            default:
                return "🎤";
        }
    }

    static String heardText(String transcript) {
        String safe = transcript == null ? "" : transcript.trim();
        return safe.isEmpty() ? "尚未聽到文字" : "聽到：" + safe;
    }

    static String resultText(String result) {
        String safe = result == null ? "" : result.trim();
        return safe.isEmpty() ? "等待指令結果" : "執行結果：" + safe;
    }
}
