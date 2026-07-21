package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public final class VoiceCommandCatalogTest {
    @Test
    public void exposesCurrentCountsFromOneSource() {
        assertEquals(17, VoiceCommandCatalog.getCommandCount());
        assertEquals(54, VoiceCommandCatalog.getPhraseCount());
        assertTrue(VoiceCommandCatalog.getQuickExamples(5).contains("開啟控制盤"));
    }

    @Test
    public void everyCatalogPhraseIsRecognizedByParser() {
        VoiceCommandParser parser = new VoiceCommandParser();
        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            for (String phrase : command.getPhrases()) {
                VoiceCommandParser.Result result = parser.parse(phrase, 0.91d);
                assertEquals(
                        "Phrase must stay synchronized with parser: " + phrase,
                        VoiceCommandParser.Result.Status.MATCHED,
                        result.getStatus()
                );
                assertNotNull(result.getAction());
                assertEquals(command.getAction(), result.getAction().getAction());
                if (command.getMode() != null) {
                    assertEquals(
                            command.getMode(),
                            result.getAction().getParameters().optString("mode")
                    );
                }
            }
        }
    }

    @Test
    public void commandIdsAndSpokenPhrasesRemainUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> phrases = new HashSet<>();
        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            assertTrue("Duplicate command id: " + command.getId(), ids.add(command.getId()));
            for (String phrase : command.getPhrases()) {
                String normalized = VoiceCommandParser.normalize(phrase);
                assertTrue("Duplicate spoken phrase: " + phrase, phrases.add(normalized));
            }
        }
    }
}
