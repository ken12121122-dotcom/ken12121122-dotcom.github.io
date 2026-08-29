# Step 9C Bridge Graph Contract

Status: Draft

## Purpose

Bridge Graph stores typed cross-domain relations. Relation meaning, direction,
identity, cardinality, provenance, ownership, and lifecycle are Contract-owned.
Adapters may instantiate registered semantics only.

## Initial relation registry

| Source | Relation | Target | Direction fixed |
| --- | --- | --- | --- |
| TASK | `uses_knowledge` | KNOWLEDGE | yes |
| RUN | `learned_from` | KNOWLEDGE | yes |
| RUN | `generated` | MEMORY_CANDIDATE | yes |
| MEMORY_CANDIDATE | `promoted_to` | KNOWLEDGE | yes |
| PR | `provides_evidence_for` | KNOWLEDGE | yes |
| GOAL | `requires_capability` | CAPABILITY | yes |

`KNOWLEDGE --learned_from--> RUN` is invalid. Aliases, reversal, or new relation
types require a Contract revision; an Adapter cannot introduce them.

## Stable relation identity

A Bridge relation identity is derived from a registered semantic key plus
stable endpoint identities and, only when the registry allows parallel edges,
a stable discriminator:

```text
bridge_relation_id = semantic + source_id + target_id [+ discriminator]
```

Mutable labels, endpoint titles, provider timestamps, and Canvas coordinates
must not participate in identity.

## Ownership and stale isolation

Every Bridge relation uses `graph_scope=bridge` and an explicit owner/partition.
Neither endpoint's Knowledge or Work sync owns the Bridge relation.

Example:

```yaml
graph_scope: bridge
sync_owner:
  provider: amin_brain
  authority: work_knowledge_linker
sync_partition: task_knowledge_relations
```

Stale-on-missing is limited to a complete snapshot for the exact Bridge
ownership key. Endpoint stale state is queryable context but does not mutate
Bridge `sync_status`.

## Provenance

A Bridge write includes the producing actor/adapter, source revision, observed
time, and supporting entity/evidence references. Provider evidence may support
a relation without becoming its lifecycle owner.

## Registry validation

Before merge, validation must prove:

- source and target types match the registry;
- direction matches exactly;
- semantic is registered for the schema version;
- stable endpoint IDs exist, even if currently stale;
- ownership key is complete;
- required provenance is present;
- duplicate identities merge deterministically;
- endpoint stale does not stale the Bridge relation;
- Knowledge or Work batches cannot stale Bridge records.

## CAPABILITY boundary

`CAPABILITY` may exist as a referenced contract type so Goals can declare
requirements. Capability scoring, training, certification, trust degradation,
and autonomy are Step 11 and are not implemented by Step 9.

## Observer gate

The GitHub Work Observer remains blocked until the shared kernel validates all
three Graph contracts. GitHub Adapters cannot create Bridge semantics merely
because two provider records appear related.
