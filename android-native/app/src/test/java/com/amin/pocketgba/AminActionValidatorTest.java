package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class AminActionValidatorTest {
    private final AminActionValidator validator = new AminActionValidator();

    @Test
    public void acceptsCatalogAction() {
        AminAction action = new AminAction("SYSTEM_HOME", new JSONObject(), "rest", 1d);
        assertTrue(validator.validate(action).isValid());
    }

    @Test
    public void rejectsUnknownAction() {
        AminAction action = new AminAction("DELETE_EVERYTHING", new JSONObject(), "rest", 1d);
        AminActionValidator.ValidationResult result = validator.validate(action);
        assertFalse(result.isValid());
        assertEquals("ACTION_UNSUPPORTED", result.getCode());
    }

    @Test
    public void validatesControlMode() throws Exception {
        JSONObject parameters = new JSONObject().put("mode", "invalid");
        AminAction action = new AminAction("CONTROL_MODE_SET", parameters, "rest", 1d);
        assertEquals("MODE_INVALID", validator.validate(action).getCode());
    }

    @Test
    public void catalogDefinesSeventeenActions() {
        assertEquals(17, AminActionValidator.getSupportedActions().size());
    }
}
