---
description: "When a [Direction] issue is labeled 'approved', Claude Code implements the chosen candidate on a new branch and opens a PR back to release/android for normal PR validation."
emoji: "🛠️"
labels: [governance, build]

on:
  issues:
    types: [labeled]
  workflow_dispatch:

if: github.event_name != 'issues' || github.event.label.name == 'approved'

permissions:
  contents: read
  issues: read
  pull-requests: read

engine: claude

tools:
  github:
    toolsets: [issues, repos, pull_requests]
  edit:
  bash: ["git"]

safe-outputs:
  create-pull-request:
    base-branch: release/android
    draft: true
    title-prefix: "[Agent] "
    labels: [agent-build, needs-review]
---

# Task: implement the approved direction (bounded to a new branch only)

1. Read the full "[Direction] ..." issue that was just labeled `approved`,
   including every comment. Find the comment where this workflow (or Ken)
   confirmed exactly which candidate direction was chosen.

2. If you cannot determine, with confidence, which single candidate was
   chosen — for example if multiple candidates were discussed and none
   was clearly confirmed — do NOT guess and do NOT write any code.
   Instead, add a comment on the issue asking Ken to confirm the exact
   candidate, and stop.

3. If the chosen candidate is clear, check out `release/android` and
   create a new branch named `agent/direction-<issue-number>`.

4. Implement only the work described by that candidate, scoped to
   `android-native/**`. Do not:
   - touch `.github/workflows/**`
   - touch `android-native/release-version.json`
   - touch `amin-vault/native-release-manifest.json`
   - push to `release/android` or `main` directly
   These four are reserved for the existing `Android Production Release`
   pipeline and must never be modified by this workflow.

5. Commit your changes on the new branch with a message referencing the
   issue number. Open a draft pull request back to `release/android`
   via safe-outputs, with a description that:
   - restates the chosen direction in one sentence
   - lists what you changed and why
   - links back to the "[Direction] ..." issue

6. Do not mark the PR ready for review, do not merge, do not modify any
   other branch. Stop once the draft PR is open — the existing
   "Android PR Validation" workflow will run automatically against it.
