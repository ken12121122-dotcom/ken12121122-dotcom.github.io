# Step 12 Agent Control Dashboard Contract

Status: Draft implementation on `feat/step12-agent-control-dashboard-readonly`

## Purpose

Step 12 presents an OWNER-facing, read-only Agent workforce view derived from existing GitHub
Work Graph evidence. It does not dispatch an Agent or create another task, identity, graph,
dedupe, merge, or Canvas system.

## Architecture

```text
GitHub PR machine-readable Agent marker
  -> GitHubWorkAdapter normalized attributes
  -> existing SharedGraphSyncKernel / GitHub Work store
  -> AgentStatusProjector
  -> existing UnifiedGraphProvider
  -> existing single Dynamic Canvas / Agent manager
```

The marker supplies only explicit Agent ownership and declared workflow state. CI is derived
from existing `PULL_REQUEST --has_run--> WORKFLOW_RUN` evidence. Missing evidence produces
`idle` or `unknown`; presentation code cannot guess.

## Marker v1

```html
<!-- amin-agent-execution {
  "agent_id": "agent:codex",
  "agent_type": "codex",
  "role": "Backend / Platform Engineer",
  "issue_number": 122,
  "current_task": "Step 12 Agent Control Dashboard",
  "status": "working",
  "blocking_reason": "",
  "owner_attention_required": false,
  "owner_attention_reason": "",
  "started_at": "2026-09-01T00:00:00Z"
} -->
```

Allowed status values are `working`, `waiting`, `blocked`, `idle`, and `review`. A blocked
marker requires a blocking reason. OWNER Attention requires an explicit reason. Invalid or
oversized markers are retained only as a normalized validation error and cannot assign work.
Exactly one marker is allowed. A marker is trusted only on a same-repository PR whose GitHub
`author_association` is `OWNER`, `MEMBER`, or `COLLABORATOR`; fork and untrusted contributor
markers cannot assign an Agent. Permanent Agent IDs must match their declared Agent type.
Raw PR body content is not persisted in Agent status attributes.

## Stable identity

The Step 9B durable identities remain authoritative:

- `agent:claude-code`
- `agent:codex`
- `agent:chatgpt`

Agent nodes are a derived Unified Graph projection. Their evidence relations target existing
provider-backed GitHub Work node IDs. The projection does not claim ownership of or stale
provider records.

## Output

```yaml
AgentExecutionStatus:
  schema_version: agent-execution-status-v1
  agent_id: string
  agent_type: claude_code | codex | chatgpt | other
  role: string
  issue_number: integer
  branch: string
  pr_number: integer
  current_task: string
  status: working | waiting | blocked | idle | review
  ci_status: passing | failing | pending | unknown
  blocking_reason: string
  owner_attention_required: boolean
  owner_attention_reason: string
  started_at: timestamp | ""
  last_activity_at: timestamp | ""
  evidence:
    - source_type: issue | pr | workflow_run
      source_ref: stable GitHub Work node ID
      url: string
  read_only: true
```

## Security boundary

- Public GET-only GitHub observation remains unchanged.
- No token, credential, GitHub App permission, write endpoint, raw log access, workflow
  dispatch, merge, release, Agent dispatch, approval, cancellation, retry, assignment write,
  Tool execution, autonomy, or production action is added.
- CI failure may set display state to blocked, but cannot create OWNER Attention by itself.
- OWNER Attention is presentation of explicit GitHub evidence, not an approval action.
- Agent cards and relations are rendered through the existing single Dynamic Canvas.

## Failure and stop conditions

- Missing marker: Agent is idle with no evidence.
- Invalid marker: no assignment is projected.
- Missing PR-to-run relation: `ci_status=unknown`.
- Partial/rate-limited GitHub sync: previous valid Work records remain protected by the existing
  SharedGraphSyncKernel partition rules.
- Any request for write, dispatch, credentials, autonomy, merge, release, or production is a
  separate OWNER gate and outside Step 12.
