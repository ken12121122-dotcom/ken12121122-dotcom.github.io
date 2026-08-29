---
description: "Proposes the next update direction immediately after a production release ships, and keeps discussing it in comments until Ken picks one."
emoji: "🧭"
labels: [governance, roadmap]

on:
  workflow_run:
    workflows: ["Android Production Release"]
    types: [completed]
  issue_comment:
    types: [created]
  workflow_dispatch:

if: github.event_name != 'workflow_run' || github.event.workflow_run.conclusion == 'success'

permissions:
  contents: read

engine: claude   # swap to "codex" if you'd rather Codex draft the candidates

tools:
  github:
    toolsets: [issues, repos]
  bash: ["git", "gh"]

safe-outputs:
  create-issue:
    title-prefix: "[Direction] "
    labels: [proposed-update, needs-decision]
  add-comment:
    max: 5
---

# Task

## Case 1 — a production release just completed (github.event_name == "workflow_run")
1. Confirm the triggering "Android Production Release" run concluded successfully. If it did not, stop immediately and produce no output.
2. Read the newly published `android-native/release-version.json` on `release/android` and `amin-vault/native-release-manifest.json` on `main` to see exactly what just shipped.
3. Read the last 10 commits on `release/android` under `android-native/**` and the `releaseNotes` of the last 3 published versions.
4. Read open issues and any TODO/FIXME markers under `android-native/`, grouped by theme (bug fix, performance, new feature, tech debt).
5. Propose exactly 2 to 4 candidate directions for the next update. For each candidate include:
   - **What**: one concrete sentence
   - **Why now**: the evidence from steps 2-4 that supports it
   - **Size**: S / M / L
   - **Risk**: one line — what's fragile or could go wrong
6. Open exactly one issue via safe-outputs, titled "[Direction] Following <version just released>", with the candidates as a numbered checklist.
7. Do not modify any files. Do not open a branch or pull request. Stop after opening the issue.

## Case 2 — a new comment appears on an existing "[Direction] ..." issue (github.event_name == "issue_comment")
1. Only act if the issue carries the label `proposed-update` and the comment is not from this workflow's own bot account.
2. Read the full comment thread so far for context.
3. If the comment asks a question or challenges a candidate, reply with a short, direct answer — or a revised version of that candidate — via `add-comment`. A few sentences, conversational, not a report.
4. If the comment clearly picks a direction (e.g. "選 2", "go with option 3", "approved: X"), reply confirming which one was chosen and that it's ready to hand to Claude Code. Do not propose further candidates once one is picked.
5. Never open a second issue for the same release cycle. Never modify files or open a pull request — this workflow only discusses and confirms a decision; it does not implement anything.

Stop after your safe-output in either case.
