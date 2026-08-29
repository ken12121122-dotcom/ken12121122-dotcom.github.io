# Step 9B Work Graph Contract

Status: Draft

## Purpose

The Work Graph records durable work state. This contract defines data and
identity only. Step 10 owns suggestion, approval, dispatch, retry,
reassignment, callback, notification, and executable state transitions.

## Brain work nodes

### GOAL

`goal_id` is stable across title, description, priority, and lifecycle edits.
Minimum lifecycle: proposed, active, paused, achieved, cancelled.

### TASK

Suggested and approved work are one node with one `task_id`:

```text
TASK(status=suggested)
→ same TASK(status=approved)
→ same TASK(status=queued|running|reviewing|waiting_owner|completed|failed|cancelled)
```

Approval records owner, timestamp, accepted revision, and audit evidence but
must not create a new Task identity. A Task references its Goal without
embedding a mutable Goal title into identity.

### RUN

Every execution attempt has a distinct `run_id`. Retry or Agent reassignment
creates a new Run while preserving `task_id`. Minimum data state:
queued, running, reviewing, waiting_owner, succeeded, failed, cancelled.

### AGENT

`agent_id` identifies a durable Agent such as Claude Code or Codex. Work history
references the permanent Agent identity. Capability scoring remains Step 11.

## GitHub work-view nodes

Repository, Branch, Commit, PR, Workflow Run, Check Run, and Evidence views use
provider-backed stable IDs. Mutable names, titles, conclusions, and branch heads
are attributes, not identities.

Required GitHub ownership example:

```yaml
graph_scope: work
sync_owner:
  provider: github
  repository: ken12121122-dotcom/ken12121122-dotcom.github.io
sync_partition: pull_requests
```

Partitions are independent, including repositories, branches, commits,
pull_requests, workflow_runs, and check_runs. A Pull Request snapshot cannot
stale records owned by another partition.

## Domain and sync lifecycle

Every synchronized Work record separates:

```text
lifecycle_status = domain state
sync_status = active|stale
```

GitHub closing a PR updates PR lifecycle. Missing a PR from a complete owned
snapshot updates its sync lifecycle. Neither action changes a Brain Task unless
Step 10 applies an explicit registered transition.

## Identity gates

- owner approval preserves `task_id` and `firstSeen`;
- retries preserve `task_id` and create new `run_id`;
- Agent reassignment creates a new Run or explicit Run assignment history;
- GitHub title/branch-head changes preserve provider node identity;
- duplicate provider observations merge deterministically;
- manual Tasks are never owned or staled by GitHub partitions.

## Observer boundary

A future GitHub Work Observer may emit contract-valid provider observations. It
cannot create Brain Tasks, approve work, dispatch Agents, invent relations, or
write Canvas state. Observer implementation remains blocked until 9C is
accepted.
