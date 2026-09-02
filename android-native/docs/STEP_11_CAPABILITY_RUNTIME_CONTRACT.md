# Step 11 Capability Runtime Contract v1

Status: Draft implementation contract

## Purpose

Step 11 adds a governed Capability Graph read model for approved Skills,
Workflows, Tools, Connectors, Commands, and Certifications. It reuses existing
Registry identity, `SharedGraphSyncKernel`, `UnifiedGraphProvider`, and the
single Dynamic Canvas.

This first slice is inventory-only. It does not execute a Tool, train an Agent,
grant a credential, dispatch work, or raise autonomy.

## Scope and ownership

Capability records use the existing `work` graph scope. Step 11 does not add a
fourth graph scope.

```yaml
graph_scope: work
sync_owner:
  provider: amin_registry
  authority: node_registry | command_catalog
sync_partition: capabilities | commands
```

Complete snapshots may mark missing records stale only inside the exact owner
and partition. A Command refresh cannot stale Registry capabilities.

## Stable identity

- Registered capability: reuse the existing `capability_id` unchanged.
- Registered command: `command:<command_id>`.
- Mutable titles, descriptions, routes, status labels, and Canvas coordinates
  never participate in stable identity.

The Adapter may calculate a content fingerprint for change detection, but it
does not create a competing identity or dedupe engine.

## Entity types

```text
CAPABILITY
SKILL
WORKFLOW
TOOL
CONNECTOR
COMMAND
CERTIFICATION
```

## Lifecycle and review

Lifecycle values:

```text
discovered | proposed | approved | training | certified | suspended | revoked
```

Review values:

```text
generated | reviewed | approved | rejected | deprecated
```

Certification values:

```text
not_certified | training | certified | expired | suspended | revoked
```

Every projected record includes `autonomy_level=none` and
`execution_enabled=false`. A certified record requires both inspectable
certification evidence and `human_approval.review_status=approved`. It also
requires a versioned, time-bounded `certification_scope` containing `scope_id`,
`version`, `issued_at`, and `expires_at`. The contract exposes this boundary as
`trust_status=time_bounded` and `trust_expires_at`; it does not calculate a
trust score or renew, downgrade, or revoke certification automatically.

Certification relations are explicitly limited to
`CAPABILITY --certified_by--> CERTIFICATION` and
`CERTIFICATION --certifies--> CAPABILITY`. They remain read-only and project
with execution disabled.

## Source records

Every entity requires at least one structured source record containing a
non-empty `source_id` and `authority`. Certification evidence uses the same
inspectable structure; unstructured strings or missing authorities reject the
batch. The first inventory authorities are the Android
manifest / Node Registry, built-in Voice Command Catalog, and approved Dynamic
Command Registry.

Missing or invalid source data rejects the new batch and preserves the last
committed Capability state.

## Projection boundary

`CapabilityInventoryProjector` only adds read-only nodes, relations, and
commands to the existing Unified Graph JSON. It does not choose coordinates,
create a Canvas, or enable relation execution. Capability relations project
with their Gate disabled. The existing Canvas may expose a read-only Capability
manager for filtering and inspection; it is not a second graph or execution
surface. The manager filters type (including Command), lifecycle, and
certification state. Its detail view renders source records, certification
evidence, approval state, scope, and expiry as readable fields rather than a
raw object. An elapsed certification expiry is highlighted as a read-only
effective status; the stored record is not silently renewed, downgraded, or
revoked.

## OWNER gates beyond this slice

The following require a later explicit OWNER decision:

- Tool or shell execution;
- network Connector credentials or new secrets;
- certification policy and trust scoring;
- Agent-to-Capability assignment;
- autonomy elevation or autonomous dispatch;
- GitHub write, merge, workflow dispatch, release, destructive, or production
  authority.

## Rollback

Removing the Step 11 projector call and Capability inventory classes returns
the existing Unified Graph behavior. The inventory preference contains only a
rebuildable, non-secret read model and is not an execution authority.
