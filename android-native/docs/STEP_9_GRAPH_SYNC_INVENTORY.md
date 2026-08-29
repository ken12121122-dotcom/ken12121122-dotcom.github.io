# Step 9 Graph Sync Inventory

Status: Draft extraction analysis

Source under review: PR #111, branch
`feat/evidence-only-scanner-backbone-v1`

This document inventories verified Step 8.5 behavior. It does not implement a
shared kernel or authorize a GitHub Work Observer.

## 1. Source components

| Component | Current responsibility | Step 9 disposition |
| --- | --- | --- |
| `GraphSyncEngine` | Merges complete canonical Evidence snapshots | Extract selected mechanics only |
| `GraphSyncEngineTest` | Verifies Evidence sync identity and lifecycle | Preserve as compatibility baseline |
| `CanonicalEvidenceAdapter` | Converts scanner/indexer results into canonical Evidence | Keep Evidence-specific |
| `GraphEvidenceSyncStore` | Persists one Evidence sync state in SharedPreferences | Keep behind Evidence boundary |
| `UnifiedGraphProvider` | Projects synchronized Evidence into the single Canvas read model | Keep Canvas projection separate |
| Graph playback store/controller | Replays visual growth and stale/recovery transitions | Keep outside shared sync kernel |

## 2. Verified reusable mechanics

The existing tests verify these behaviors:

1. `entityId` and `relationId` are stable merge keys.
2. Repeated input does not append duplicate records.
3. Duplicate incoming IDs collapse to the first indexed record.
4. Existing `firstSeen` is preserved.
5. Active observations advance `lastSeen`.
6. Missing records become stale instead of being deleted.
7. `missingSince` is set on the first missing observation and preserved while
   the record remains stale.
8. A recovered record reuses the same identity and returns to active.
9. Evidence payload changes update a hash without changing identity.
10. Entity and relation added/kept/missing counts are reported.

These are candidates for the Shared Graph Sync Kernel only after they are
expressed without Evidence schema assumptions and protected by ownership.

## 3. Evidence-specific behavior that must not enter the kernel

The following current fields and operations belong to the Evidence Adapter or
Evidence projection:

- format `amin-synced-graph-evidence`;
- `revision`, `anchorId`, and `sourceStatus`;
- `evidenceRevision`;
- `verification` and Evidence gap interpretation;
- Evidence hashing over `sourceKey`, `from`, `to`, `type`, `verification`, and
  `evidence`;
- source artifact `kind`, `parentId`, and scanner labels;
- Evidence-specific `change` display values;
- Evidence SharedPreferences storage key;
- visual mapping from stale Evidence to pending Canvas state;
- Canvas gates, summaries, layout, playback, and coordinates.

The generic kernel needs a contract-provided content fingerprint or comparison
strategy. It must not contain `hashEvidence`.

## 4. Blocking gaps before extraction

### 4.1 Global stale domain

`GraphSyncEngine.sync` compares every prior entity/relation with one incoming
snapshot. Anything missing becomes stale. There is no `graph_scope`,
`sync_owner`, or `sync_partition`.

This is correct only for the current single Evidence snapshot. Reusing it for
Work or Knowledge would allow a PR snapshot to stale unrelated Branch, CI,
manual Task, Knowledge, or Bridge records.

### 4.2 Snapshot completeness is implicit

The method treats every invocation as a complete authoritative snapshot.
`null` input becomes an empty canonical Evidence snapshot and can stale all
prior records. There is no committed/complete batch gate.

The shared input contract must require an explicit completeness assertion.
Failed, partial, cancelled, or pagination-incomplete batches cannot run
stale-on-missing.

### 4.3 Sync lifecycle overwrites domain status

The engine writes `status=active|stale`. Step 9 Work Graph also needs domain
status such as `suggested`, `approved`, `queued`, and `running`.

The shared representation must keep synchronization lifecycle separate from
domain lifecycle, for example:

```text
lifecycle_status = suggested|approved|queued|running|...
sync_status = active|stale
```

The exact field names belong to 9A/9B/9C contracts, but one field cannot serve
both meanings.

### 4.4 Relation semantics are unchecked

The engine accepts any relation `type`, direction, and endpoints. It cannot
verify the Bridge registry or reject reversed semantics.

Contract validation must occur before a relation reaches the kernel.

### 4.5 Failure collapses to empty state

Broad exception handling returns `empty()`. A shared kernel must return an
explicit failure and preserve the last committed state. Parsing or validation
failure cannot masquerade as a successful empty snapshot.

### 4.6 Identity fallback is permissive

Indexing accepts `entityId`/`relationId` with fallback to `id`, skips blank IDs,
and silently keeps the first duplicate. Step 9 contracts must decide whether
fallback and first-wins remain valid per schema version. Invalid records need
observable rejection rather than silent disappearance.

## 5. Proposed extraction boundary

The shared kernel may own:

- indexing by a contract-validated stable ID;
- deterministic dedupe behavior;
- incremental merge of validated payloads;
- `firstSeen`, `lastSeen`, and `missingSince` sync metadata;
- active/stale synchronization lifecycle;
- per-batch statistics;
- stale-on-missing constrained to one ownership key.

The kernel input must already contain:

- validated node or relation kind;
- stable identity;
- `graph_scope`;
- canonical `sync_owner`;
- `sync_partition`;
- provider provenance and revision;
- content fingerprint or comparison result;
- complete-snapshot proof;
- contract schema version.

The kernel must not own:

- provider API calls;
- Evidence parsing;
- Graph schema invention;
- Bridge relation semantics;
- Task approval or state transitions;
- storage technology;
- Canvas projection;
- Agent dispatch.

## 6. Ownership-safe merge model

One batch operates on exactly one key:

```text
ownership_key = graph_scope + sync_owner + sync_partition
```

Merge processing is limited to records matching that key. Missing detection is
permitted only when the batch is explicitly complete and committed.

Bridge records use `graph_scope=bridge` and their own owner/partition. They are
not owned by either endpoint's sync batch. Endpoint stale state may affect UI
presentation but cannot mutate Bridge lifecycle.

## 7. Compatibility requirements for PR #111

Extraction cannot change Step 8.5 behavior. The existing Evidence Adapter must
continue to produce the same synchronized Evidence and single Canvas read
model for equivalent input.

Before implementation, compatibility fixtures must cover:

- repeated identical Evidence;
- duplicate incoming IDs;
- missing Evidence becoming stale;
- stale Evidence recovery;
- changed Evidence hash with stable identity;
- relation lifecycle;
- growth playback input;
- malformed input preserving the last committed state.

The last item is a required hardening beyond the current implementation.

## 8. Next specification work

The next documents must define, in order:

1. 9A Knowledge Graph node, identity, lifecycle, ownership, and Memory
   promotion contract.
2. 9B Work Graph Goal/Task/Run/Agent and GitHub work-view contract, including
   stable Task identity across owner approval.
3. 9C Bridge Graph relation registry, direction, identity, provenance,
   ownership, and independent stale lifecycle.

No GitHub Work Observer implementation begins until all three contracts are
reviewed and accepted.
