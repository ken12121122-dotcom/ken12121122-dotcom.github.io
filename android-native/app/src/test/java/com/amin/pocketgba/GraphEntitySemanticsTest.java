package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public class GraphEntitySemanticsTest {
    @Test public void canonicalLevelsFollowTypeContract() throws Exception {
        assertEquals("large", GraphEntitySemantics.levelForType("SYSTEM"));
        assertEquals("large", GraphEntitySemantics.levelForType("AGENT"));
        assertEquals("medium", GraphEntitySemantics.levelForType("STREAM"));
        assertEquals("medium", GraphEntitySemantics.levelForType("SKILL"));
        assertEquals("medium", GraphEntitySemantics.levelForType("GROUP"));
        assertEquals("small", GraphEntitySemantics.levelForType("NODE"));
        assertEquals("small", GraphEntitySemantics.levelForType("COMMAND"));
        assertEquals("small", GraphEntitySemantics.levelForType("TOOL"));
    }

    @Test public void nodeTypeIsNormalizedIntoCanonicalType() throws Exception {
        JSONObject node = new JSONObject().put("nodeType", "skill");
        assertEquals("SKILL", GraphEntitySemantics.canonicalType(node, "NODE"));
    }

    @Test public void legacyRendererGetsSmallTierFallbackParent() {
        assertEquals("group:nodes", GraphEntitySemantics.legacyParentForLevel("", "small"));
        assertEquals("", GraphEntitySemantics.legacyParentForLevel("", "medium"));
        assertEquals("existing", GraphEntitySemantics.legacyParentForLevel("existing", "small"));
    }
}
