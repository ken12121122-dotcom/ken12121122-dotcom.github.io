# Amin Governance Phase 2 — Executable Workflow

Status: generated draft  
Issue: #75

## Goal

Convert the merged functional BOM and Node Registry into an executable governance gate.

```text
Request
  ↓
Declare node_id + paths + change_type
  ↓
Impact Resolver
  ├─ validate node IDs
  ├─ expand reverse dependencies
  ├─ apply known contract triggers
  ├─ calculate validation scope
  └─ classify risk / human approval
  ↓
Issue / Agent / Branch
  ↓
Pull Request governance contract
  ↓
GitHub Actions governance validation
  ↓
Implementation validation
  ↓
Human approval when required
  ↓
Integration / release
```

## Executable components

- `governance/tools/impact_resolver.py`: read-only dependency impact calculation.
- `governance/change-request.schema.json`: canonical machine-readable request contract.
- `governance/examples/change-request-control-api.json`: executable example.
- `.github/ISSUE_TEMPLATE/governed-change.yml`: governed change request form.
- `.github/pull_request_template.md`: PR governance contract.
- `.github/workflows/governance-impact-validation.yml`: CI smoke/fail-closed validation.

## Fail-closed behavior

An unknown Node Registry ID returns a non-zero exit code, `valid: false`, `block_release: true`, and `requires_human_review: true`.

The resolver is read-only. It does not mutate `node-registry.yaml`, `dependency-impact-rules.yaml`, production source, APKs, credentials, permissions, or release authority.

## Current automation boundary

Phase 2 v1 automates impact calculation and validation gating. It does **not** autonomously create implementation branches, assign external AI agents, merge PRs, or perform production release. Those actions remain explicit orchestration steps, and high-risk actions retain human approval.

## Next increment

After this validation gate is accepted, the next phase can consume the resolver JSON to automatically create structured implementation tasks and route work to agents while keeping the same Node Registry and approval boundaries.
