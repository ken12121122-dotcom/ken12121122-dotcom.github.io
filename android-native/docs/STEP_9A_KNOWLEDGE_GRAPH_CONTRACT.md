# Step 9A Knowledge Graph Contract

Status: Draft

## Purpose

The Knowledge Graph stores durable Brain knowledge and Memory promotion
history. Provider observations are evidence, not canonical truth.

## Node types

### KNOWLEDGE

Required fields:

- `knowledge_id`: stable Brain identity;
- `knowledge_kind`: concept, rule, pattern, decision, or constraint;
- `canonical_statement`;
- `lifecycle_status`: candidate_review, active, deprecated, or rejected;
- `provenance_refs`;
- `created_at`, `updated_at`;
- sync metadata defined by Step 9 Core.

Canonical identity must not be derived only from mutable text. Renaming or
editing a statement preserves `knowledge_id`; splitting or combining concepts
creates explicit replacement relations in a later registered contract.

### MEMORY_CANDIDATE

Required fields:

- `memory_candidate_id`: stable identity from the generating event;
- `candidate_kind`;
- `statement`;
- `confidence` and supporting evidence references;
- `lifecycle_status`: proposed, reviewing, promoted, rejected, or expired;
- `created_at`, `updated_at`;
- sync metadata.

Promotion never mutates a Memory Candidate into a Knowledge node. It creates or
updates a `KNOWLEDGE` node and records
`MEMORY_CANDIDATE --promoted_to--> KNOWLEDGE` in Bridge Graph.

## Ownership

Brain-authored Knowledge uses a Brain-owned partition. Imported observations
use provider-specific owners and partitions. One owner cannot stale another
owner's canonical Knowledge.

Example:

```yaml
graph_scope: knowledge
sync_owner:
  provider: amin_brain
  authority: canonical_memory
sync_partition: knowledge_records
```

## Lifecycle and sync isolation

`lifecycle_status` is domain state. `sync_status` is observation state. A stale
provider observation cannot automatically deprecate canonical Knowledge.

Knowledge stale-on-missing is allowed only for a complete snapshot with the
same Knowledge ownership key. Promotion, deprecation, and rejection are Brain
operations and cannot be inferred by an Adapter.

## Validation gates

- mutable statement changes preserve stable identity;
- provider sync cannot stale Brain-owned Knowledge;
- Memory Candidate promotion preserves both node identities;
- stale observation does not equal deprecated Knowledge;
- missing or invalid provenance rejects the write;
- incomplete snapshots cannot apply stale-on-missing.
