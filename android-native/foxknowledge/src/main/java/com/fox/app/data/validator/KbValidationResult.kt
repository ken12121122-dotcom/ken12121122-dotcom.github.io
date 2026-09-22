package com.fox.app.data.validator

/**
 * `errors` block the document from entering KnowledgeDB as a resolved node (it is
 * skipped and the reason is surfaced to the user, per FOX_SCHEMA rule 3 — never
 * fabricate a value to make an invalid document "pass"). `warnings` do not block
 * ingestion but are shown alongside the node (e.g. an out-of-schema enum value).
 */
data class KbValidationResult(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}
