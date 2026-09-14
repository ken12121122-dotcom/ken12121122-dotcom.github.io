package com.fox.app.data.parser

/** Frontmatter fields needed to build a NodeEntity. Null when a required field is missing. */
data class ParsedNode(
    val nodeId: String,
    val title: String,
    val documentType: String,
    val status: String,
    val reviewStatus: String,
    val version: String?,
    val tags: List<String>,
    val owner: String?,
    val confidence: Double?,
    val sourceStatus: String?,
)

/** One `relation:: [[target]]` line from the document body. */
data class ParsedEdge(
    val relation: String,
    val target: String,
)

/** One `## heading` block from the document body, in source order. */
data class ParsedContentSection(
    val section: String,
    val content: String,
    val order: Int,
)

/**
 * Result of parsing one `.md` file. `node` is null when required frontmatter fields
 * are missing — per FOX_SCHEMA rule 3 ("缺少必要資料時列入 unresolved_gaps，不得自行補造"),
 * KBValidator surfaces `gaps` rather than the parser inventing placeholder values.
 */
data class ParsedDocument(
    val node: ParsedNode?,
    val edges: List<ParsedEdge>,
    val contents: List<ParsedContentSection>,
    val gaps: List<String>,
)
