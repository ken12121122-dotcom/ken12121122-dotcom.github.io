# Amin Agent Integration Policy

Status: generated draft  
Version: 0.2.0-draft

## Purpose

Define how Issues, AI agents, branches, Pull Requests, validation, integration, and release interact without allowing temporary agent workspaces to become the system architecture.

## Core flow

```text
Master BOM / Node Registry
        ↓
Issue
        ↓
Dependency Impact Calculation
        ↓
Agent task
        ↓
Temporary branch
        ↓
Pull Request
        ↓
Review + Validation
        ↓
Integration target
        ↓
Release process
        ↓
Production
```

## Mandatory rules

1. Every implementation task must map to at least one `node_id` before release.
2. Agent branches are temporary execution workspaces; they are not authoritative system structure.
3. Agents must not make the final production-release decision.
4. Source branches must not silently create a second production-release path.
5. Cross-module changes must declare affected nodes and dependencies in the PR description.
6. Missing dependency information must be recorded as an unresolved gap; it must not be invented.
7. Production release remains subject to validation and human approval.
8. Source files are not overwritten merely to make governance metadata agree with assumptions.
9. Before implementation or integration, changed nodes must be evaluated with `governance/dependency-impact-rules.yaml`.
10. A branch name, Issue, or PR is evidence of work history, not proof that a capability is released; release status requires release-backed evidence.
11. A change affecting an interface contract, data schema, permission, credential, externally callable API, or release authority cannot be treated as an isolated local change.

## Branch convention

```text
agent/<task>
feature/<capability>
fix/<defect>
ci/<validation-change>
docs/<documentation-change>
release/<release-work>
```

Branches should be deleted or archived after their changes are integrated and no longer needed for traceability.

## Pull Request contract

Every governed PR should identify:

```yaml
node_ids:
  - AMIN-XXXX
change_type: feature | fix | integration | refactor | validation | docs | release | governance
source_branch: ""
target_branch: ""
changed_paths: []
affected_dependencies: []
impact_class: local | cross_node | platform
risk_level: low | medium | high
unresolved_gaps: []
validation_required: true
human_approval_required: true
```

## Dependency-aware integration

When a node changes:

1. Validate all changed `node_id` values against the Node Registry.
2. Resolve direct dependencies, reverse dependencies, parent/child links, and shared source paths.
3. Expand impact when an interface contract, schema, route, permission, protocol, or release contract changes.
4. Record every impacted node and the dependency path that caused inclusion.
5. Run module-level validation on changed nodes.
6. Run integration validation across impacted nodes.
7. Block release if a required dependency is unknown, unvalidated, or high-risk without human approval.

The machine-readable algorithm and known contract triggers are defined in `governance/dependency-impact-rules.yaml`.

## Evidence classification

Repository history must be classified explicitly:

- `confirmed`: implementation or release evidence directly supports the capability.
- `partial`: some source or dependency ownership remains unresolved.
- `unverified`: a branch or claim exists, but inspected evidence does not establish a distinct integrated/released capability.

For example, a branch carrying a future bridge number must not be promoted into the BOM as a released capability when its own release metadata still reports the previous bridge.

## Release authority

Observed Android production metadata currently exists at:

- Development version source: `release/android/android-native/release-version.json`
- Production manifest: `main/amin-vault/native-release-manifest.json`
- Production APK location: `main/amin-vault/releases/`

This policy does not change those release locations.

## Human approval points

Human approval is required before:

- production release;
- changing release authority or production paths;
- deleting source data;
- modifying permissions or credentials;
- database migrations with destructive or irreversible effects;
- changing externally callable control/security boundaries;
- accepting a high-risk dependency or integration exception.

## Rollback

Governance changes must be isolated in their own branch/PR until approved. If rejected, close the PR and leave production code unchanged.
