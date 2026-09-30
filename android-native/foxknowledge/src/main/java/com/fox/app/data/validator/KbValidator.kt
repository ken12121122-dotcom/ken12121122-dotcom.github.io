package com.fox.app.data.validator

import com.fox.app.data.parser.ParsedDocument

/**
 * Checks a parsed document against FOX_SCHEMA's enums and value ranges.
 * KbParser already turns a missing required field into `ParsedDocument.gaps`
 * and a null `node`; this validator adds schema-shape checks on top of a
 * document that *did* parse, so a syntactically-valid-but-schema-violating
 * document is still flagged rather than silently accepted.
 */
object KbValidator {

    private val VALID_DOCUMENT_TYPES = setOf("source", "knowledge", "node", "skill", "output", "governance", "memory_candidate", "folder_index")
    private val VALID_STATUSES = setOf("draft", "active", "superseded", "archived")
    private val VALID_REVIEW_STATUSES = setOf("generated", "reviewed", "approved", "rejected", "deprecated")

    fun validate(doc: ParsedDocument): KbValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (doc.node == null) {
            errors += doc.gaps.ifEmpty { listOf("文件無法解析為 Node（原因不明）") }
            return KbValidationResult(errors, warnings)
        }

        val node = doc.node

        if (node.documentType !in VALID_DOCUMENT_TYPES) {
            warnings += "document_type '${node.documentType}' 不在 FOX_SCHEMA 已知清單內: $VALID_DOCUMENT_TYPES"
        }
        if (node.status !in VALID_STATUSES) {
            warnings += "status '${node.status}' 不在 FOX_SCHEMA 已知清單內: $VALID_STATUSES"
        }
        if (node.reviewStatus !in VALID_REVIEW_STATUSES) {
            warnings += "review_status '${node.reviewStatus}' 不在 FOX_SCHEMA 已知清單內: $VALID_REVIEW_STATUSES"
        }
        node.confidence?.let { c ->
            if (c < 0.0 || c > 1.0) warnings += "confidence $c 超出 0.00-1.00 範圍"
        }
        if (node.reviewStatus == "approved" && node.sourceStatus == null) {
            warnings += "review_status=approved 但缺少 source_status，無法確認來源可信度"
        }

        // Body-level structural gaps carried over from the parser (e.g. edge lines it
        // could recognize a verb for but no bracketed target, malformed section, etc.)
        // are non-blocking: the node itself is still usable.
        warnings += doc.gaps

        return KbValidationResult(errors, warnings)
    }
}
