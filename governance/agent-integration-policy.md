# Amin Agent Integration Policy

Status: generated draft  
Version: 0.1.0-draft

## Purpose

Define how Issues, AI agents, branches, Pull Requests, validation, integration, and release interact without allowing temporary agent workspaces to become the system architecture.

## Core flow

```text
Master BOM / Node Registry
        ↓
Issue
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
change_type: feature | fix | integration | validation | docs | release
source_branch: ""
target_branch: ""
affected_dependencies: []
unresolved_gaps: []
validation_required: true
human_approval_required: true
```

## Dependency-aware integration

When a node changes:

1. Read the Node Registry.
2. Resolve direct dependencies and declared linked nodes.
3. Create or update implementation tasks for affected nodes.
4. Run module-level validation.
5. Run integration validation across all affected nodes.
6. Block release if required dependencies remain unvalidated.

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
- accepting a high-risk dependency or integration exception.

## Rollback

Governance changes must be isolated in their own branch/PR until approved. If rejected, close the PR and leave production code unchanged.
