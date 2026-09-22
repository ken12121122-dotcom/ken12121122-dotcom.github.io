# Traceable Knowledge M0-M2 Contract v1

Status: OWNER-approved implementation candidate

Issue: #143

## Purpose

Create the smallest reusable local knowledge data path before connecting Fox or
any external API/MCP. The implementation stores immutable raw snapshots,
versioned structured records, deterministic chunks, and offline search results
with source traceability.

## Boundary

- Capability ID: `CAP-TRACEABLE-KNOWLEDGE-001`
- This slice implements M0 data contracts, M1 local persistence, and M2 offline
  ingestion/search verification.
- It does not add a second Graph, identity, dedupe, merge engine, or Canvas.
- It does not connect Fox, call a Tool/API/MCP, download national law data,
  perform semantic/vector retrieval, or make a legal-compliance decision.
- All imported knowledge remains `generated` until a later OWNER-governed
  promotion flow approves it.

## Storage ownership

```text
app-private files/traceable-knowledge/raw/<sha256>.bin
  -> immutable raw bytes

SQLite traceable-knowledge.db
  -> sources
  -> documents
  -> document_versions
  -> chunks
  -> chunks_fts
  -> retrieval_logs
  -> verification_records
```

Raw files are content-addressed. Importing identical bytes reuses the existing
snapshot; it never overwrites it. A document version is identified by the
document ID plus the full content hash. New content creates a new version and
updates the document's current-version pointer without deleting history.

## Required contracts

### Source

```yaml
source_id: required stable string
source_type: required string
authority: required string
source_url: optional HTTPS URL
created_at: epoch milliseconds
```

### Document

```yaml
document_id: required stable string
source_id: required existing source
title: required string
current_version_id: nullable
review_status: generated|reviewed|approved|rejected|deprecated
```

### Document version

```yaml
version_id: deterministic document-id/content-hash identity
document_id: required existing document
source_version: required string
raw_hash: SHA-256
raw_path: app-private relative path
retrieved_at: epoch milliseconds
effective_date: optional source-provided date
status: candidate|current|superseded|rejected
content_hash: SHA-256
```

### Chunk

```yaml
chunk_id: deterministic version-id/ordinal identity
version_id: required existing version
ordinal: zero-based integer
section: heading, article number, or generated section label
content: required non-empty text
source_reference: source-local section reference
```

### Retrieval result

```yaml
format: amin-traceable-knowledge-result
version: 1
query_id: stable per-query identifier
query: normalized query text
matched_chunks: []
source_records: []
unresolved_gaps: []
offline: true
```

## Failure and rollback

- Invalid required fields abort before a transaction begins.
- Empty or unchunkable content is rejected and the raw snapshot may remain as
  an unreferenced recoverable content-addressed file.
- Database writes use a transaction. Failure rolls back source, document,
  version, chunk, and FTS mutations together.
- Schema rollback is removal of the new isolated database and code path; no
  existing Graph or production data is migrated in this slice.

## Acceptance

- Contract validation rejects missing source/document/version/chunk identity.
- Five distinct test documents persist across repository reopen.
- Duplicate import reuses the same immutable raw hash and version.
- Offline search returns chunk, document, version, source, and score.
- Tests do not require network, credentials, Fox, Graph mutation, or release.
