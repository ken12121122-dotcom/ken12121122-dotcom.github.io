package com.amin.pocketgba;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class PromptTextTest {
    @Test
    public void requireContent_preservesSelectedTextExactly() {
        assertEquals("  第一行\n第二行  ", PromptText.requireContent("  第一行\n第二行  "));
    }

    @Test
    public void requireContent_rejectsBlankSelection() {
        assertThrows(IllegalArgumentException.class, () -> PromptText.requireContent("  \n "));
    }

    @Test
    public void preview_compactsWhitespaceAndTruncates() {
        assertEquals("第一行 第…", PromptText.preview("第一行\n第二行", 6));
    }
}
