# Step 9 Core Contract and Kernel Extraction Specification

Status: Draft architecture contract

Target base: `release/android`

Scope: specification only; no Observer, Runtime, Canvas, or release implementation

## 1. Purpose

Step 9 establishes the Brain Memory Foundation shared by the Knowledge Graph,
Work Graph, and Bridge Graph. External providers may supply observations, but
they do not define Brain schema, identity, relation semantics, or lifecycle.

The required construction order is:

```text
Shared Graph Sync Kernel
→ graph_scope and stale isolation
→ 9A Knowledge Graph Contract
→ 9B Work Graph Contract
→ 9C Bridge Graph Contract
→ GitHub Work Observer
```

Step 9 includes Memory. Goal/Task dispatch belongs to Step 10; capability
training and certification belong to Step 11.

## 2. Non-goals

This specification does not authorize:

- a second Graph engine, identity system, dedupe implementation, or Canvas;
- a GitHub Work Observer before all three Graph contracts are accepted;
- Evidence-specific fields in the shared kernel;
- Task dispatch, Agent retry/reassignment, notification, or `WAITING_OWNER`;
- Skill, Workflow, Tool, Connector, training, certification, or autonomy;
- release manifest, signing, production publishing, or version changes.

## 3. Shared Graph Sync Kernel boundary

The existing Step 8.5 `GraphSyncEngine` remains the verified source for these
semantics:

- stable identity;
- dedupe;
- incremental merge;
- `firstSeen` preservation;
- `lastSeen` advancement;
- stale-on-missing.

Step 9 must extract or generalize only these semantics. It must not declare the
entire Evidence-oriented engine universally reusable. Evidence field mapping,
revision interpretation, and Evidence validation remain the responsibility of
an Evidence Adapter.

The kernel accepts contract-valid nodes and relations plus explicit sync
ownership. It must not infer domain types, invent relation names, or interpret
provider payloads.

## 4. Required graph scopes

Every node and relation managed by the kernel has an explicit `graph_scope`:

```text
knowledge
work
bridge
```

Isolation invariants:

1. Knowledge sync cannot mark Work entities or relations stale.
2. Work sync cannot mark Knowledge entities or relations stale.
3. Knowledge or Work sync cannot mark Bridge relations stale.
4. A stale endpoint does not imply that a Bridge relation is stale.
5. Bridge lifecycle is evaluated from Bridge ownership and observations, not
   from endpoint lifecycle as a side effect.

## 5. Stable Task lifecycle

Suggested and approved work are lifecycle states of one `TASK` node, not two
node types and not two identities.

```text
GOAL
→ TASK(status=suggested)
→ TASK(status=approved)
→ TASK(status=queued|running|reviewing|waiting_owner|completed|failed|cancelled)
→ RUN
```

Owner approval updates the existing Task. It must preserve:

- stable Task identity;
- `firstSeen`;
- provenance and approval history;
- Goal linkage;
- prior lifecycle transitions.

Approval must never create a replacement Task identity. A `RUN` is a distinct
execution attempt and therefore has its own stable identity.

## 6. Sync ownership contract

`graph_scope` alone is insufficient for stale-on-missing. Every synchronized
entity or relation must be attributable to a verifiable sync owner and
partition.

Minimum ownership dimensions:

```yaml
graph_scope: work
sync_owner:
  provider: github
  repository: ken12121122-dotcom/ken12121122-dotcom.github.io
sync_partition: pull_requests
```

The canonical ownership key must distinguish at least:

```text
graph_scope
+ sync_owner
+ sync_partition
```

Provider-specific owner fields may be normalized into a deterministic owner
key, but their source values must remain inspectable for evidence and debugging.

Example GitHub partitions include:

- `repositories`;
- `branches`;
- `pull_requests`;
- `workflow_runs`;
- `check_runs`.

An artificial or manually authored Brain Task must use a different owner from
GitHub observations even when it references the same repository.

## 7. Stale-on-missing algorithm

For one completed sync batch, stale candidates are limited to records whose
ownership key exactly matches that batch:

```text
record.graph_scope == batch.graph_scope
AND record.sync_owner == batch.sync_owner
AND record.sync_partition == batch.sync_partition
```

Only records inside that set and absent from the completed snapshot may become
stale. A partial, failed, cancelled, paginated-incomplete, or uncommitted batch
must not apply stale-on-missing.

A GitHub Pull Request snapshot therefore cannot stale:

- Branch entities owned by the Branch partition;
- CI entities owned by Workflow or Check partitions;
- owner-authored Tasks;
- Knowledge entities;
- Bridge relations.

Incremental event ingestion may update or upsert records but cannot claim a
complete snapshot unless its Adapter supplies an explicit completeness proof.

## 8. Contract-owned Bridge semantics

Bridge relation types and directions are defined centrally by the Bridge Graph
Contract. Adapters may instantiate registered relations; they may not invent,
reverse, alias, or silently reinterpret relation semantics.

Initial relation registry:

```text
TASK --uses_knowledge--> KNOWLEDGE
RUN --learned_from--> KNOWLEDGE
RUN --generated--> MEMORY_CANDIDATE
MEMORY_CANDIDATE --promoted_to--> KNOWLEDGE
PR --provides_evidence_for--> KNOWLEDGE
GOAL --requires_capability--> CAPABILITY
```

The source type, target type, direction, cardinality, identity fields, and
allowed provenance for each relation must be validated before merge.

In particular, the deprecated direction below is invalid:

```text
KNOWLEDGE --learned_from--> RUN
```

## 9. Contract layers

### 9A Knowledge Graph Contract

Normative draft: [`STEP_9A_KNOWLEDGE_GRAPH_CONTRACT.md`](./STEP_9A_KNOWLEDGE_GRAPH_CONTRACT.md)

Defines Knowledge and Memory semantics, including:

- `KNOWLEDGE` and `MEMORY_CANDIDATE` identity;
- provenance and revision;
- canonical versus observed knowledge;
- lifecycle and promotion history;
- first/last seen and stale behavior;
- allowed Knowledge-internal relations.

### 9B Work Graph Contract

Normative draft: [`STEP_9B_WORK_GRAPH_CONTRACT.md`](./STEP_9B_WORK_GRAPH_CONTRACT.md)

Defines stable identities and lifecycle for:

- `GOAL`;
- `TASK`;
- `RUN`;
- `AGENT`;
- Repository, Branch, Commit, PR, Workflow Run, Check Run, and Evidence views.

This layer defines data states only. Step 10 owns state-machine execution,
approval actions, selection, retry, reassignment, dispatch, callback, and
notification.

### 9C Bridge Graph Contract

Normative draft: [`STEP_9C_BRIDGE_GRAPH_CONTRACT.md`](./STEP_9C_BRIDGE_GRAPH_CONTRACT.md)

Defines cross-scope relation semantics, direction, identity, provenance,
ownership, and independent lifecycle. Bridge relations must remain queryable
when an endpoint is stale, while UI may separately indicate endpoint status.

## 10. Adapter responsibilities

Adapters translate provider payloads into contract-valid sync inputs.

An Adapter must:

- declare `graph_scope`, `sync_owner`, and `sync_partition`;
- generate identities through contract-defined rules;
- map only registered node and relation types;
- declare whether a batch is a complete snapshot;
- preserve provider provenance and revision;
- reject unsupported schema versions.

An Adapter must not:

- define Graph schema;
- create new relation semantics;
- perform cross-partition stale marking;
- directly mutate Canvas state;
- promote Evidence into Brain truth;
- dispatch Agents.

The Evidence Adapter continues to own Evidence-specific fields and validation.

## 11. Kernel extraction sequence

1. Inventory the Step 8.5 `GraphSyncEngine` behaviors and tests.
2. Record the exact verified semantics for identity, dedupe, merge,
   `firstSeen`, `lastSeen`, and stale-on-missing.
3. Identify Evidence-specific inputs and outputs; leave them behind the
   Evidence Adapter boundary.
4. Define the shared kernel input model with explicit scope and ownership.
5. Add ownership-key and completed-snapshot invariants before extraction.
6. Prove Step 8.5 Evidence behavior remains unchanged through its Adapter.
7. Add Knowledge, Work, and Bridge contract fixtures only after their contracts
   are accepted.
8. Begin the GitHub Work Observer only after 9A, 9B, and 9C pass contract review.

## 12. Kernel batch envelope v1

`SharedGraphSyncKernel` accepts one already-validated owner/partition batch. The
required envelope is:

```yaml
format: amin-graph-sync-batch
version: 1
contract_schema_version: step9-core-v1
graph_scope: knowledge|work|bridge
sync_owner: {}
sync_partition: pull_requests
revision: provider-revision
provenance: {}
committed: true
batch_status: complete|partial
complete_snapshot: true|false
completeness_proof: {} # required when complete_snapshot is true
entities: []
relations: []
```

Each record supplies `stable_id`, `content_fingerprint`, and a contract-valid
`payload`. The kernel writes `sync_status` separately and never rewrites a
domain `lifecycle_status` inside the payload. A rejected batch returns an error
code plus the prior state unchanged. An existing `stable_id` cannot be silently
re-owned by another ownership key; that collision is rejected and requires an
explicit contract-level migration. A Bridge Adapter must call the central
`Step9BridgeGraphContract` registry before passing relations to the kernel.

`failed`, `cancelled`, uncommitted, malformed, and complete snapshots without
inspectable completeness proof are rejected. A valid `partial` batch may
upsert observations but cannot stale missing records.

## 13. Hard acceptance gates

Step 9 Core cannot be considered accepted unless automated contract checks can
prove all of the following:

1. Suggested-to-approved transition preserves Task identity.
2. A new Run creates a Run identity without replacing Task identity.
3. Duplicate observations merge into one stable record.
4. `firstSeen` is preserved and `lastSeen` advances.
5. Knowledge sync cannot stale Work.
6. Work sync cannot stale Knowledge.
7. Knowledge/Work sync cannot stale Bridge relations.
8. Pull Request sync cannot stale Branch, CI, manual Task, or Bridge records.
9. Incomplete snapshots cannot apply stale-on-missing.
10. Endpoint stale does not automatically stale a Bridge relation.
11. Unregistered or reversed Bridge semantics are rejected.
12. Existing Evidence synchronization remains behaviorally compatible through
    the Evidence Adapter.

Until these gates are implemented and pass, status must remain specification or
implementation-in-progress. No completion or release claim is permitted.

## 14. Downstream boundary

After Step 9 contracts and the GitHub Work Observer are complete:

- Step 10 builds Brain Runtime: suggestion, approval, Agent selection,
  retry/reassignment, state machine, notification, `WAITING_OWNER`, dispatch,
  callback, and Work Graph updates.
- Step 11 builds Capability Runtime: Skill, Workflow, Tool, Connector,
  capability scoring, training, certification, and autonomy.
