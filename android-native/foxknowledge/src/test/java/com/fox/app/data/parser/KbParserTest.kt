package com.fox.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KbParserTest {

    private val sample = """
        ---
        knowledge_id: KB-APP-001
        document_type: knowledge
        title: 知識庫對App資料契約
        status: active
        review_status: approved
        version: "1.0"
        owner: 林哲民
        confidence: 0.85
        source_status: partial
        tags: [App, 資料契約, 知識庫]
        ---

        # 知識庫對App資料契約

        ## 正式知識

        App 需要從 Google Drive 讀取三類資料。

        ## 關係

        - belongs_to:: [[工作治理_INDEX]]
        - depends_on:: [[FOX_SCHEMA]]

        ## 未解決缺口

        - App 尚未開發，此契約為先行規格，未經實際串接驗證。
    """.trimIndent()

    @Test
    fun `parses frontmatter into a node`() {
        val result = KbParser.parse(sample)

        assertNotNull(result.node)
        assertTrue(result.gaps.isEmpty())
        assertEquals("KB-APP-001", result.node!!.nodeId)
        assertEquals("知識庫對App資料契約", result.node.title)
        assertEquals("knowledge", result.node.documentType)
        assertEquals("active", result.node.status)
        assertEquals("approved", result.node.reviewStatus)
        assertEquals(listOf("App", "資料契約", "知識庫"), result.node.tags)
        assertEquals(0.85, result.node.confidence!!, 0.0001)
    }

    @Test
    fun `parses relation lines into edges`() {
        val result = KbParser.parse(sample)

        assertEquals(2, result.edges.size)
        assertEquals(ParsedEdge("belongs_to", "工作治理_INDEX"), result.edges[0])
        assertEquals(ParsedEdge("depends_on", "FOX_SCHEMA"), result.edges[1])
    }

    @Test
    fun `parses heading sections into content blocks in order`() {
        val result = KbParser.parse(sample)

        assertEquals(3, result.contents.size)
        assertEquals("正式知識", result.contents[0].section)
        assertEquals("關係", result.contents[1].section)
        assertEquals("未解決缺口", result.contents[2].section)
        assertTrue(result.contents[0].content.contains("Google Drive"))
    }

    @Test
    fun `missing required frontmatter fields surface as gaps instead of a guessed node`() {
        val broken = """
            ---
            document_type: knowledge
            status: active
            ---
            # 標題缺失
        """.trimIndent()

        val result = KbParser.parse(broken)

        assertNull(result.node)
        assertTrue(result.gaps.any { it.contains("document_id") })
        assertTrue(result.gaps.any { it.contains("review_status") })
        assertTrue(result.gaps.any { it.contains("title") })
    }

    @Test
    fun `document without frontmatter delimiters is reported as a gap`() {
        val result = KbParser.parse("# 沒有 frontmatter\n內容")

        assertNull(result.node)
        assertEquals(1, result.gaps.size)
        assertTrue(result.gaps[0].contains("frontmatter"))
    }
}
