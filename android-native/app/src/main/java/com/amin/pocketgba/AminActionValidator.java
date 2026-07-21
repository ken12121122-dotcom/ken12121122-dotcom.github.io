package com.amin.pocketgba;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AminActionValidator {
    public static final class ValidationResult {
        private final boolean valid;
        private final String code;
        private final String message;

        private ValidationResult(boolean valid, String code, String message) {
            this.valid = valid;
            this.code = code;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getCode() { return code; }
        public String getMessage() { return message; }

        public static ValidationResult ok() {
            return new ValidationResult(true, "OK", "Action accepted");
        }

        public static ValidationResult fail(String code, String message) {
            return new ValidationResult(false, code, message);
        }
    }

    private static final Set<String> SUPPORTED_ACTIONS;

    static {
        Set<String> actions = new HashSet<>();
        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            actions.add(command.getAction());
        }
        SUPPORTED_ACTIONS = Collections.unmodifiableSet(actions);
    }

    public ValidationResult validate(AminAction action) {
        if (action == null) {
            return ValidationResult.fail("ACTION_REQUIRED", "Missing AminAction");
        }
        String name = action.getAction();
        if (name == null || name.isBlank()) {
            return ValidationResult.fail("ACTION_REQUIRED", "Action name is required");
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ACTIONS.contains(normalized)) {
            return ValidationResult.fail("ACTION_UNSUPPORTED", "Unsupported action: " + name);
        }
        String source = action.getSource();
        if (source == null || source.isBlank() || source.length() > 64) {
            return ValidationResult.fail("SOURCE_INVALID", "Source must contain 1 to 64 characters");
        }
        if (Double.isNaN(action.getConfidence()) || Double.isInfinite(action.getConfidence())) {
            return ValidationResult.fail("CONFIDENCE_INVALID", "Confidence must be a finite number");
        }
        if ("CONTROL_MODE_SET".equals(normalized)) {
            String mode = action.getParameters().optString("mode", "");
            if (!UniversalControlAccessibilityService.MODE_CURSOR.equals(mode)
                    && !UniversalControlAccessibilityService.MODE_SCROLL.equals(mode)) {
                return ValidationResult.fail("MODE_INVALID", "mode must be cursor or scroll");
            }
        }
        if (action.getParameters().toString().length() > 16384) {
            return ValidationResult.fail("PARAMETERS_TOO_LARGE", "Parameters exceed 16 KB");
        }
        return ValidationResult.ok();
    }

    public static Set<String> getSupportedActions() {
        return SUPPORTED_ACTIONS;
    }
}
