package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public final class VoiceCommandParserTest {
    private final VoiceCommandParser parser = new VoiceCommandParser();

    @Test
    public void normalizesTraditionalChinesePunctuation() {
        assertEquals("開啟控制盤", VoiceCommandParser.normalize(" 開啟控制盤！ "));
    }

    @Test
    public void matchesOverlayOpenAlias() {
        VoiceCommandParser.Result result = parser.parse("打開控制盤", 0.91d);
        assertEquals(VoiceCommandParser.Result.Status.MATCHED, result.getStatus());
        assertNotNull(result.getAction());
        assertEquals("OVERLAY_OPEN", result.getAction().getAction());
        assertEquals("voice", result.getAction().getSource());
    }

    @Test
    public void matchesCursorModeWithParameter() {
        VoiceCommandParser.Result result = parser.parse("切換游標模式", 0.96d);
        assertEquals(VoiceCommandParser.Result.Status.MATCHED, result.getStatus());
        assertEquals("CONTROL_MODE_SET", result.getAction().getAction());
        assertEquals("cursor", result.getAction().getParameters().optString("mode"));
    }

    @Test
    public void rejectsUnknownCommand() {
        VoiceCommandParser.Result result = parser.parse("幫我訂一杯咖啡", 0.99d);
        assertEquals(VoiceCommandParser.Result.Status.NO_MATCH, result.getStatus());
    }

    @Test
    public void rejectsEmptyTranscript() {
        VoiceCommandParser.Result result = parser.parse("，。！", -1d);
        assertEquals(VoiceCommandParser.Result.Status.NO_MATCH, result.getStatus());
    }

    @Test
    public void serializesStableActionEnvelope() {
        AminAction action = parser.parse("回首頁", 0.88d).getAction();
        String json = action.toJson();
        org.junit.Assert.assertTrue(json.contains("\"action\":\"SYSTEM_HOME\""));
        org.junit.Assert.assertTrue(json.contains("\"source\":\"voice\""));
        org.junit.Assert.assertTrue(json.contains("\"requestId\""));
        org.junit.Assert.assertTrue(json.contains("\"createdAt\""));
    }
}
