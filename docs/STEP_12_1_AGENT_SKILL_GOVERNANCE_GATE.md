# Step 12.1 Agent / Skill Governance Gate

Status: Draft implementation on `feat/step12-1-agent-skill-governance-gate`

## Purpose

This check turns the existing Agent identity, Execution Receipt, Capability Skill type, source
records, and human-review boundary into a fail-closed pull-request gate. It does not create a
second Agent identity, Skill Registry, Graph, dedupe system, Connect system, or Canvas.

## Enforced evidence

Every pull request must contain exactly one valid `amin-agent-execution` marker. The marker is
accepted only for a same-repository PR from GitHub association `OWNER`, `MEMBER`, or
`COLLABORATOR`. Its permanent Agent ID and type must agree with the Step 12 contract. Existing
Android projection derives `agent-execution-receipt-v1` from that trusted PR and existing Work
Graph nodes; the CI check does not let the marker inject a receipt or Context node.

If a PR changes any `SKILL.md`, it must also contain exactly one `amin-skill-submission` marker
whose submissions cover those changed paths exactly. Each submission requires:

- a stable `skill:` ID and semantic version;
- a candidate review state;
- structured source records and Agent provenance;
- an explicit unresolved-gap list;
- `canonical=false` and `execution_enabled=false`.

Agent-authored PR evidence cannot claim `approved`, `certified`, or `canonical`. Promotion stays
an explicit OWNER/human governance action through the existing review boundary.

## Security boundary

The workflow uses `pull_request` with only `contents: read` and `pull-requests: read`. It does not
receive credentials, execute a submitted Skill, mutate GitHub, approve, merge, release, dispatch
workflows, read raw logs, select a model, or promote FOX Workspace material into a Baseline.

`CODEOWNERS` identifies governance-critical paths. It becomes an enforcement boundary only when
the OWNER enables a GitHub ruleset or branch protection that requires both the
`validate-governance` check and CODEOWNER approval. Repository code cannot truthfully claim that
server-side boundary is active until that setting is enabled.

## Required-check activation (OWNER gate)

After this stacked PR is reviewed and present on the protected target branch, the OWNER may:

1. enable a ruleset for `release/android` and active feature-stack bases;
2. require `Agent and Skill Governance Gate / validate-governance`;
3. require pull requests and CODEOWNER approval for governance-critical files;
4. prevent bypass except an explicit OWNER recovery path.

This activation is intentionally not performed by the Agent and is not part of the repository
commit. Until activation, the workflow supplies auditable evidence but is not a server-enforced
merge boundary.
