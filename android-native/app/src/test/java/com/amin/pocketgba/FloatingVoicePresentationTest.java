package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FloatingVoicePresentationTest {
    @Test
    public void listeningStateUsesThreeDots() {
        assertEquals(
                "...",
                FloatingVoicePresentation.bubbleText(
                        FloatingVoicePresentation.Phase.LISTENING
                )
        );
    }

    @Test
    public void transcriptAndResultAreClearlyLabeled() {
        assertEquals("聽到：回到首頁", FloatingVoicePresentation.heardText(" 回到首頁 "));
        assertEquals(
                "執行結果：已回到首頁",
                FloatingVoicePresentation.resultText("已回到首頁")
        );
    }

    @Test
    public void idleAndFinishedStatesHaveDistinctIcons() {
        assertEquals(
                "🎤",
                FloatingVoicePresentation.bubbleText(FloatingVoicePresentation.Phase.IDLE)
        );
        assertEquals(
                "✓",
                FloatingVoicePresentation.bubbleText(FloatingVoicePresentation.Phase.SUCCESS)
        );
        assertEquals(
                "!",
                FloatingVoicePresentation.bubbleText(FloatingVoicePresentation.Phase.ERROR)
        );
    }
}
