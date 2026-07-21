package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class AminActionDispatcherTest {
    @Test
    public void routesCursorAndDirectionActionsToSharedCore() {
        FakeTarget target = new FakeTarget();

        AminActionDispatcher.DispatchResult tap = AminActionDispatcher.dispatch(
                new AminAction("CURSOR_TAP", new JSONObject(), "voice", 0.9d),
                target
        );
        assertTrue(tap.isSuccess());
        assertEquals("CURSOR_TAP", target.lastSharedAction);

        AminActionDispatcher.DispatchResult left = AminActionDispatcher.dispatch(
                new AminAction("DIRECTION_LEFT", new JSONObject(), "voice", 0.9d),
                target
        );
        assertTrue(left.isSuccess());
        assertEquals("DIRECTION_LEFT", target.lastSharedAction);
    }

    @Test
    public void routesIndependentFloatingControls() {
        FakeTarget target = new FakeTarget();

        AminActionDispatcher.DispatchResult closeKeyboard = AminActionDispatcher.dispatch(
                new AminAction("OVERLAY_CLOSE", new JSONObject(), "voice", 0.9d),
                target
        );
        assertTrue(closeKeyboard.isSuccess());
        assertEquals("keyboard_close", target.lastFloatingAction);

        AminActionDispatcher.DispatchResult closeVoice = AminActionDispatcher.dispatch(
                new AminAction("VOICE_BUBBLE_CLOSE", new JSONObject(), "voice", 0.9d),
                target
        );
        assertTrue(closeVoice.isSuccess());
        assertEquals("voice_close", target.lastFloatingAction);
    }

    @Test
    public void routesModeParameterWithoutLosingValue() throws Exception {
        FakeTarget target = new FakeTarget();
        JSONObject parameters = new JSONObject();
        parameters.put("mode", UniversalControlAccessibilityService.MODE_SCROLL);

        AminActionDispatcher.DispatchResult result = AminActionDispatcher.dispatch(
                new AminAction("CONTROL_MODE_SET", parameters, "voice", 0.9d),
                target
        );

        assertTrue(result.isSuccess());
        assertEquals(UniversalControlAccessibilityService.MODE_SCROLL, target.lastMode);
    }

    @Test
    public void reportsAccessibilityFailureInsteadOfPretendingSuccess() {
        FakeTarget target = new FakeTarget();
        target.sharedActionResult = false;

        AminActionDispatcher.DispatchResult result = AminActionDispatcher.dispatch(
                new AminAction("SYSTEM_BACK", new JSONObject(), "voice", 0.9d),
                target
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("全域控制服務"));
    }

    @Test
    public void rejectsUnsupportedAction() {
        FakeTarget target = new FakeTarget();
        AminActionDispatcher.DispatchResult result = AminActionDispatcher.dispatch(
                new AminAction("UNKNOWN", new JSONObject(), "voice", 0.9d),
                target
        );
        assertFalse(result.isSuccess());
    }

    private static final class FakeTarget implements AminActionDispatcher.ActionTarget {
        private String lastMode;
        private String lastSharedAction;
        private String lastFloatingAction;
        private boolean sharedActionResult = true;

        @Override public boolean openGba() { return true; }
        @Override public boolean openControllerSettings() { return true; }
        @Override public boolean openOverlay() { lastFloatingAction = "keyboard_open"; return true; }
        @Override public boolean closeOverlay() { lastFloatingAction = "keyboard_close"; return true; }
        @Override public boolean openVoiceBubble() { lastFloatingAction = "voice_open"; return true; }
        @Override public boolean closeVoiceBubble() { lastFloatingAction = "voice_close"; return true; }

        @Override
        public boolean setControlMode(String mode) {
            lastMode = mode;
            return true;
        }

        @Override
        public boolean executeSharedAction(AminAction action) {
            lastSharedAction = action.getAction();
            return sharedActionResult;
        }
    }
}
