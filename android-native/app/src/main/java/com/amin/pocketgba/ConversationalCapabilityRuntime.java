package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONObject;

/** Read-only facade used before legacy Node/Command execution paths. */
final class ConversationalCapabilityRuntime {
    static final class Result {
        private final boolean handled;
        private final String answer;
        private final String spokenAnswer;
        private final JSONObject resolution;

        private Result(boolean handled, String answer, String spokenAnswer, JSONObject resolution) {
            this.handled = handled;
            this.answer = answer;
            this.spokenAnswer = spokenAnswer;
            this.resolution = resolution;
        }

        boolean isHandled() { return handled; }
        String getAnswer() { return answer; }
        String getSpokenAnswer() { return spokenAnswer; }
        JSONObject getResolution() { return resolution; }
    }

    private ConversationalCapabilityRuntime() { }

    static Result resolve(Context context, NodeMetadataStore nodeStore, String query) {
        return resolve(context, nodeStore, query, null);
    }

    /**
     * @param capabilityQuestionOverride when non-null, replaces the deterministic
     *        {@link CapabilityResolver#isCapabilityQuestion(String)} keyword gate with this decision
     *        (Phase 12 Semantic Router). Pass null to keep today's unchanged keyword-only behavior.
     */
    static Result resolve(Context context, NodeMetadataStore nodeStore, String query, Boolean capabilityQuestionOverride) {
        boolean isCapabilityQuestion = capabilityQuestionOverride != null
                ? capabilityQuestionOverride
                : CapabilityResolver.isCapabilityQuestion(query);
        if (!isCapabilityQuestion) {
            return new Result(false, "", "", new JSONObject());
        }
        JSONObject resolution = CapabilityResolver.resolve(query,
                ReadOnlyCapabilityContextBuilder.build(context, nodeStore));
        String fallback = "能力盤點暫時無法讀取，沒有執行任何動作。";
        return new Result(true, resolution.optString("answer", fallback),
                resolution.optString("spoken_answer", fallback), resolution);
    }
}
