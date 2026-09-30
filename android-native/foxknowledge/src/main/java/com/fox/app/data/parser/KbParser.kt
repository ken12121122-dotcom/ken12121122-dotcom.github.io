package com.fox.app.data.parser

import org.yaml.snakeyaml.Yaml

/**
 * Parses one Drive `.md` document per FOX_SCHEMA / KB-APP-001's three-way split:
 * frontmatter -> Node, `relation:: [[target]]` body lines -> Edge, `## section` blocks -> Content.
 *
 * This never fabricates a missing value. A required frontmatter field that is absent
 * shows up in `ParsedDocument.gaps`, and `node` is null until all required fields are present —
 * KBValidator (not this class) decides what to do about that.
 */
object KbParser {

    private val FRONTMATTER_REGEX = Regex("""\A---\s*\n(.*?)\n---\s*\n?""", RegexOption.DOT_MATCHES_ALL)

    // e.g. "- depends_on:: [[設備巡檢]]" or "depends_on:: [[設備巡檢]]" (leading "- " optional)
    private val EDGE_LINE_REGEX = Regex("""^-?\s*([a-zA-Z_][a-zA-Z0-9_]*)::\s*\[\[([^\]]+)]]\s*$""")

    private val SECTION_HEADING_REGEX = Regex("""^##\s+(.+?)\s*$""")

    private val REQUIRED_ID_KEYS = listOf("document_id", "knowledge_id", "memory_id", "node_id")

    fun parse(raw: String): ParsedDocument {
        val gaps = mutableListOf<String>()
        val match = FRONTMATTER_REGEX.find(raw)

        if (match == null) {
            return ParsedDocument(
                node = null,
                edges = emptyList(),
                contents = emptyList(),
                gaps = listOf("找不到 frontmatter（需以 --- 開頭與結尾）"),
            )
        }

        val frontmatterText = match.groupValues[1]
        val body = raw.substring(match.range.last + 1)

        @Suppress("UNCHECKED_CAST")
        val yamlMap: Map<String, Any?> = try {
            (Yaml().load(frontmatterText) as? Map<String, Any?>) ?: emptyMap()
        } catch (e: Exception) {
            return ParsedDocument(
                node = null,
                edges = emptyList(),
                contents = emptyList(),
                gaps = listOf("frontmatter YAML 解析失敗: ${e.message}"),
            )
        }

        val node = parseNode(yamlMap, gaps)
        val edges = parseEdges(body)
        val contents = parseContents(body)

        return ParsedDocument(node = node, edges = edges, contents = contents, gaps = gaps)
    }

    private fun parseNode(yamlMap: Map<String, Any?>, gaps: MutableList<String>): ParsedNode? {
        val nodeId = REQUIRED_ID_KEYS.firstNotNullOfOrNull { key -> yamlMap[key]?.toString() }
        if (nodeId == null) {
            gaps += "缺少必要欄位: document_id/knowledge_id/memory_id/node_id 皆未提供"
        }

        val documentType = yamlMap["document_type"]?.toString()
        if (documentType == null) gaps += "缺少必要欄位: document_type"

        val status = yamlMap["status"]?.toString()
        if (status == null) gaps += "缺少必要欄位: status"

        val reviewStatus = yamlMap["review_status"]?.toString()
        if (reviewStatus == null) gaps += "缺少必要欄位: review_status"

        val title = yamlMap["title"]?.toString()
        if (title == null) gaps += "缺少必要欄位: title"

        if (nodeId == null || documentType == null || status == null || reviewStatus == null || title == null) {
            return null
        }

        val tags = when (val rawTags = yamlMap["tags"]) {
            is List<*> -> rawTags.mapNotNull { it?.toString() }
            is String -> rawTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }

        val confidence = when (val rawConfidence = yamlMap["confidence"]) {
            is Number -> rawConfidence.toDouble()
            is String -> rawConfidence.toDoubleOrNull()
            else -> null
        }

        return ParsedNode(
            nodeId = nodeId,
            title = title,
            documentType = documentType,
            status = status,
            reviewStatus = reviewStatus,
            version = yamlMap["version"]?.toString(),
            tags = tags,
            owner = yamlMap["owner"]?.toString(),
            confidence = confidence,
            sourceStatus = yamlMap["source_status"]?.toString(),
        )
    }

    private fun parseEdges(body: String): List<ParsedEdge> =
        body.lineSequence()
            .mapNotNull { line -> EDGE_LINE_REGEX.find(line.trim()) }
            .map { m -> ParsedEdge(relation = m.groupValues[1], target = m.groupValues[2].trim()) }
            .toList()

    private fun parseContents(body: String): List<ParsedContentSection> {
        val lines = body.lines()
        val sections = mutableListOf<ParsedContentSection>()
        var currentTitle: String? = null
        val currentBody = StringBuilder()
        var order = 0

        fun flush() {
            val title = currentTitle ?: return
            sections += ParsedContentSection(section = title, content = currentBody.toString().trim(), order = order++)
            currentBody.clear()
        }

        for (line in lines) {
            val heading = SECTION_HEADING_REGEX.find(line)
            if (heading != null) {
                flush()
                currentTitle = heading.groupValues[1]
            } else if (currentTitle != null) {
                currentBody.appendLine(line)
            }
        }
        flush()

        return sections
    }
}
