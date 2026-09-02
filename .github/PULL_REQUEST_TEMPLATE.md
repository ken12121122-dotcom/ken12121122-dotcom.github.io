## Change and verification

- OWNER-approved scope:
- User-visible behavior:
- Tests / checks:
- Formal version changed: no
- Safe rollback point:

## Agent execution evidence

Keep exactly one marker. Update its values; do not identify an Agent from writing style, account
name, or commit message.

<!-- amin-agent-execution {"agent_id":"agent:codex","agent_type":"codex","role":"Backend / Platform Engineer","issue_number":0,"current_task":"Describe the approved task","status":"working","blocking_reason":"","owner_attention_required":false,"owner_attention_reason":"","started_at":""} -->

If and only if this PR changes one or more `SKILL.md` files, add exactly one
`amin-skill-submission` marker covering every changed Skill path. Agent-authored submissions
must stay non-canonical and execution-disabled until a separate OWNER review.

<!-- Example only; remove this whole example unless a SKILL.md changes.
amin-skill-submission {"schema_version":"skill-submission-v1","submissions":[{"path":"path/to/SKILL.md","skill_id":"skill:stable-id","version":"0.1.0","review_status":"generated","canonical":false,"execution_enabled":false,"source_records":[{"source_type":"issue","source_ref":"github:issue:0","authority":"owner-approved"}],"provenance":{"agent_id":"agent:codex","method":"amin-agent-execution"},"unresolved_gaps":[]}]}
-->
