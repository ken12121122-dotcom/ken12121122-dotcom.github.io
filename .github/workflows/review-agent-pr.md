---
description: "Codex reviews any pull request opened by the build-from-direction workflow, checks it stayed in scope, and leaves a single review comment. Never writes or modifies code."
emoji: "🔍"
labels: [governance, review]

on:
  pull_request:
    types: [opened, synchronize]
    branches: [release/android]

if: contains(github.event.pull_request.labels.*.name, 'agent-build')

permissions:
  contents: read
  pull-requests: read

engine: codex

tools:
  github:
    toolsets: [pull_requests, repos]

safe-outputs:
  add-comment:
    max: 1
---

# Task: review an agent-generated PR (read-only, single comment only)

1. Confirm this PR carries the `agent-build` label and its head branch
   starts with `agent/direction-`. If either is false, stop and produce
   no output — this workflow only reviews agent-generated PRs.

2. Diff this PR against `release/android`. Check for a critical
   boundary violation: does it touch any of
   - `.github/workflows/**`
   - `android-native/release-version.json`
   - `amin-vault/native-release-manifest.json`
   If yes, this is a stop-ship issue — say so plainly and first in your
   comment.

3. Find the linked "[Direction] ..." issue referenced in the PR body.
   Compare the actual diff against the confirmed candidate direction
   from that issue. Flag anything in the diff that goes beyond what was
   approved as scope creep.

4. Review the code itself for correctness and risk: obvious bugs,
   missing null/bounds checks, resource or lifecycle leaks typical to
   Android, and anything that could weaken the existing Registry,
   Connect, Gate, or Command permission boundaries mentioned in past
   release notes.

5. Do not attempt to build, run, or test the code yourself, and do not
   duplicate what the existing "Android PR Validation" checks already
   cover — note only whether those checks have completed, not their
   internal results.

6. Post exactly one review comment via `add-comment` with three short
   sections:
   - **Scope check** — in scope / boundary violation / scope creep
   - **Risk and quality notes** — a few short bullets, only what
     matters
   - **Recommendation** — approve as-is / needs changes / stop, critical
     violation

Never modify any file, never push, never merge, never open another PR
or issue. Stop after your single comment.
