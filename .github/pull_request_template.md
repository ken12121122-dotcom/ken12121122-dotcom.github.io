## Change summary

Describe the implementation change in concrete terms.

## Governance contract

```yaml
change_id: ""
node_ids:
  - AMIN-XXXX
change_type: feature
changed_paths: []
affected_dependencies: []
unresolved_gaps: []
validation_required: true
human_approval_required: false
```

## Impact result

Attach or paste the output of:

```bash
python governance/tools/impact_resolver.py <change-request.json>
```

```yaml
impacted_nodes: []
validation_scope: []
risk_level: low | medium | high
requires_human_review: false
block_release: false
```

## Validation

- [ ] Node IDs exist in `governance/node-registry.yaml`.
- [ ] Changed paths are declared.
- [ ] Impact Resolver has been executed.
- [ ] Required module/integration validation has passed.
- [ ] Unknown dependency/source gaps are explicitly recorded.
- [ ] No alternate production release path was introduced.
- [ ] Human approval obtained where required.

## Release boundary

State whether this PR changes production code, release artifacts, release authority, permissions, credentials, or destructive data behavior.
