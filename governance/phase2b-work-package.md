# Phase 2B — Governed Work Package Generation

Status: generated draft  
Issue: #77

## Purpose

Convert a natural-language request into a structured candidate work package without inventing nodes or modifying product source.

```text
Natural-language request
        ↓
Node Alias / Registry Matching
        ↓
Unique high-confidence candidate?
   ├─ No → selection_required / no_candidate
   └─ Yes
        ↓
Dependency Impact Resolver
        ↓
Work Package
   ├─ selected node IDs
   ├─ impacted nodes
   ├─ validation scope
   ├─ risk level
   ├─ suggested branch
   ├─ Issue draft
   └─ PR governance block
```

## Safety boundaries

- Alias records only map to node IDs already present in `governance/node-registry.yaml`.
- Ambiguous requests do not auto-select a node.
- Unknown requests fail closed and do not generate a fictitious node.
- The generator is read-only with respect to product source and governance source-of-truth files.
- Generated work packages have `review_status: generated`; they are not Approved.
- Final release decisions remain human-only.

## Usage

```bash
python governance/tools/work_package_generator.py governance/examples/work-request-voice-orb.json --output work-package.json
```

A request may explicitly provide `selected_node_ids` after human review. Explicit IDs are still validated against the Registry.

## Request contract

```json
{
  "change_id": "VOICE-ORB-001",
  "request_text": "修改首頁語音球功能",
  "change_type": "feature",
  "changed_paths": [],
  "selected_node_ids": []
}
```

## Output states

- `valid=true`: structured work package generated.
- `selection_required`: multiple/weak candidates; human or upstream logic must select.
- `no_candidate`: no registered node matches; BOM/Registry gap must be reviewed.
- `unknown_selected_node`: an explicit node ID is not registered; execution is blocked.
