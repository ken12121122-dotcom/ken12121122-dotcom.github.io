# AMIN Android Project Status

> Canonical engineering handoff / current-state document for ChatGPT, Codex, Claude Code, and OWNER.
>
> Before starting Android engineering work, read this document together with the active Issue / PR and verify current GitHub state. Do not treat this file as a substitute for code or CI evidence.

Last status update: 2026-08-30

## 1. Production baseline

- Repository: `ken12121122-dotcom/ken12121122-dotcom.github.io`
- Canonical Android development branch: `release/android`
- `main` is primarily the published APK / updater manifest endpoint; it is not the Android development baseline.
- Current production version: `0.11.17-bridge85`
- `versionCode`: `181`
- Latest synchronized `release/android` baseline SHA: `6a2404b06101ada270f126befb74db4fc83761ac`
- Production workflow: `Android Production Release`
- Release metadata source: `android-native/release-version.json`

## 2. Current engineering stage

### Completed / established foundations

- Dynamic Graph: established.
- Graph Interaction: established.
- GitHub Evidence -> Dynamic Graph: existing foundation.
- Shared Graph Sync Kernel: established with stable identity, dedupe, incremental merge, firstSeen / lastSeen, ownership-safe stale-on-missing.
- Single Dynamic Canvas: canonical visualization surface.
- GitHub Source Scanner: can resolve fixed repository/ref source evidence and source revision.
- Canonical Evidence pipeline: adapter / batch merge / revision matching / staging / review foundation exists.
- Node Registry / manual approval foundation exists for NODE / ACTION / CONNECT candidates.
- Android phone-control prototype exists separately on `feat/step10-brain-phone`.
- Android production update / signed APK / public manifest pipeline exists.

### In progress

- Issue #120: Android GitHub Control Layer for mobile Agent operations.
- Phase 1: read-only GitHub Work Graph observability.
- Draft PR #121: `feat/github-control-layer-phase1-readonly`; OWNER phone acceptance completed for the Phase 1 read-only GitHub Work candidate.
- Issue #123: Step 11 Capability Runtime.
- Draft PR #124: `feat/step11-capability-runtime-contract`, stacked on the accepted PR #121 head.
- Step 11 first delivery remains limited to the executable Capability Graph v1 contract, governed read-only inventory, versioned time-bounded certification scope, and a read-only manager inside the existing Unified Graph / Dynamic Canvas. The manager filters type (including Command), lifecycle, and certification state and renders structured source/evidence details without exposing Connect or execution actions.
- Step 11 execution, autonomy, write controls, merge, production release, and Phase 2 remain out of scope.

### Not complete yet

- Agent Runtime.
- Full Brain Memory Foundation.
- GitHub PR / CI / Release / Artifact Work Observer in production Android.
- GitHub write/control actions from the production Android app.
- Claude Code / Codex assignment orchestration from the Android app.
- Full game-like Agent operations UI.

## 3. Active Issue

### Issue #120 — Android GitHub Control Layer for mobile Agent operations

Purpose: make GitHub the engineering backend while the Android app becomes the OWNER / Agent operating surface. Do not rebuild GitHub infrastructure inside the app.

Target architecture:

```text
Android App
  -> GitHub Control Layer
  -> GitHub API / Actions
  -> Shared Graph Sync Kernel
  -> Work Graph / Unified Graph
  -> existing Dynamic Canvas / Android UI
```

## 4. OWNER-approved Phase 1 boundary

Approved decisions:

1. Phase 1 is GitHub Work Graph **read-only observability** only.
2. Do **not** merge the whole `feat/step10-brain-phone` branch. Reuse only suitable low-level components / patterns such as transport, Device Flow, and Android Keystore token-vault design where appropriate.
3. Phase 1 uses a GitHub-only Work scope. Do not create Task / Agent / Bridge relations yet.
4. Phase 1 first version does **not** read raw CI logs. Show failed job / failed step only. Raw log summarization is deferred to Phase 1.1.
5. All implementation must stay on a feature branch + PR. Do not directly modify production `release/android` for Phase 1 implementation.

Approved feature branch name:

`feat/github-control-layer-phase1-readonly`

Branch must be created from the latest `release/android`; PR base remains `release/android`.

## 5. Non-negotiable architecture rules

- Do not create a second Graph Engine.
- Do not create a second stable-identity system.
- Do not create a second dedupe / merge engine.
- Reuse `SharedGraphSyncKernel`.
- Do not create a second Dynamic Canvas.
- GitHub Work Graph must compose into the existing Unified Graph / Dynamic Canvas.
- Evidence schema remains Evidence; do not force PR / CI engineering state into the Evidence schema.
- Source Graph represents code structure and is not a replacement for GitHub Work Graph.
- GitHub provider IDs should back Work Graph stable IDs rather than mutable titles.
- Sync errors, pagination truncation, or rate limiting must not erase valid previous state or incorrectly mark missing nodes stale.
- Phase 1 must remain read-only.
- Production merge / release / destructive controls require an explicit OWNER security gate in later phases.

## 6. Phase 1 Work Graph v1 target

Initial node types identified by the architecture analysis:

- `REPOSITORY`
- `BRANCH`
- `COMMIT`
- `ISSUE`
- `PULL_REQUEST`
- `REVIEW`
- `WORKFLOW_RUN`
- `JOB`
- `RELEASE`
- `ARTIFACT`

Stable-ID direction:

```text
github:repo:<repository_id>
github:ref:<repository_id>:refs/heads/<branch>
github:commit:<repository_id>:<sha>
github:issue:<repository_id>:<number>
github:pr:<repository_id>:<number>
github:review:<repository_id>:<review_id>
github:run:<repository_id>:<run_id>
github:job:<repository_id>:<job_id>
github:release:<repository_id>:<release_id>
github:artifact:<repository_id>:<artifact_id>
```

The contract must be executable / validated rather than documentation-only before broad expansion.

## 7. Phase 1 minimal implementation direction

Codex architecture analysis proposed this sequence:

1. Freeze executable Work Graph v1 contract.
2. Build a read-only GitHub client, reusing safe Step 10 transport / authentication patterns where suitable.
3. Add independent Work Observer partitions for repositories, branches, commits, issues, pull requests, reviews, workflow runs, jobs, releases, and artifacts.
4. Feed validated Work batches through the existing `SharedGraphSyncKernel`.
5. Add a Work-specific store / partition orchestration without introducing a second merge engine.
6. Compose Registry + Evidence + Work through `UnifiedGraphProvider` into the existing Dynamic Canvas.
7. Add regression tests protecting Registry nodes, Commands, custom Connect relations, Evidence nodes, and stable GitHub identity.
8. Add the minimal Android read-only status UI only after the data path is coherent.

Testing policy: OWNER does not need APKs for trivial or cosmetic intermediate UI. Prefer a complete, meaningful functional slice before asking OWNER to install and test a new APK.

## 8. Phase 1 Android UI target

First complete useful read-only surface should eventually provide:

- manual refresh
- repository / branch selection
- recent PR summary
- CI / workflow-run summary
- failed job / failed step
- recent release summary
- focus the corresponding Work Graph node on the existing Dynamic Canvas
- last sync time
- API rate-limit state
- partial / error state

Not Phase 1:

- raw CI log download / summary (Phase 1.1)
- background always-on sync
- Agent assignment
- PR merge
- workflow dispatch
- release button
- GitHub write actions

## 9. GitHub permission boundary

Phase 1 GitHub App should remain repository-scoped and read-only.

Expected read permissions when required by implemented endpoints:

- Metadata: Read
- Contents: Read
- Issues: Read
- Pull requests: Read
- Actions: Read
- Checks: Read
- Commit statuses: Read

Do not grant for Phase 1:

- Contents: Write
- Issues: Write
- Pull requests: Write
- Actions: Write
- Workflows: Write
- Administration: Write

Never put GitHub App private keys, client secrets, PATs, production secrets, or equivalent long-lived credentials inside the APK, BuildConfig, repository Issues, application logs, or ordinary SharedPreferences.

Android token storage should use Android Keystore + authenticated encryption and be excluded from Android backup.

The existing LAN / phone-control token is not a GitHub credential and must not be reused as one.

## 10. Unmerged Step 10 prototype boundary

`feat/step10-brain-phone` contains potentially reusable foundations including:

- GitHub HTTPS transport
- Device Flow protocol
- Android Keystore + AES-GCM token vault
- ETag polling
- GitHub session validation
- phone Brain feed

It also contains higher-level Agent Runtime / write behavior including Issue creation / approval / cancellation and is coupled to `amin-agent-control`.

Do not merge the whole branch into Phase 1. Extract / adapt only the approved low-level pieces after verifying they fit the current `release/android` architecture.

## 11. Release engineering follow-up

Release workflow currently exists and successfully published `0.11.17-bridge85` / versionCode 181.

A release-gate requirements document has been added at:

`docs/android-release-gate-requirements.md`

Required release-process direction:

- enforce advancement to the next production version before publishing
- prevent same-version republishing from appearing as a valid new release
- generate a Release Change Summary
- identify source commit / commits and Android file changes
- distinguish metadata-only releases from user-visible functional changes
- preserve the distinction between release-version gating and updater `mandatory` behavior

The requirements document does not by itself prove the workflow implementation is complete. Verify the workflow before claiming the gate is enforced.

## 12. Repository security follow-up

Architecture analysis identified the following current risks to address separately from Phase 1 read-only implementation:

- `main` has no branch protection / repository ruleset currently identified.
- `release/android` has no branch protection / repository ruleset currently identified.
- Production release workflow currently has broad `contents: write` at job scope.
- Signing secrets are available during the production job rather than isolated to a protected release environment.

Before Phase 2 write/control capabilities, target:

- protect `release/android`; require PR + Android PR Validation
- protect `main`; restrict ordinary direct pushes
- move production secrets to a GitHub Environment with OWNER required reviewer
- build / test jobs use least privilege (`contents: read`)
- only final publishing path temporarily receives required write permission
- Android client never receives direct production merge / release authority

These security improvements should not unnecessarily block the Phase 1 read-only MVP.

## 13. Current handoff / next action

Current owner of implementation: Codex.

Codex should:

- work from latest `release/android`
- use `feat/github-control-layer-phase1-readonly`
- implement the OWNER-approved Phase 1 boundary
- preserve existing Graph / identity / dedupe / Canvas architecture
- follow the top-level `AGENTS.md` continuous-execution rule and continue through tests / CI fixes
- stop at the complete OWNER mobile acceptance gate; do not enter Phase 2 automatically

OWNER prefers testing after a complete meaningful function exists, not merely to inspect an intermediate UI.

## 14. Handoff protocol for future AI sessions

For ChatGPT / Codex / Claude Code:

1. Read this file first.
2. Read the currently active Issue(s), especially Issue #120 while it remains active.
3. Inspect the active feature branch / PR and current CI; do not assume this document is newer than GitHub evidence.
4. Verify `release/android` production baseline before branching or proposing a release.
5. Treat code, tests, PR status, workflow runs, and release manifest as authoritative evidence for implementation state.
6. Update this status document when an architecture decision, production baseline, active phase, or handoff state materially changes.
7. Do not copy secrets, tokens, private keys, PATs, or credentials into this document.

## 15. Planned roadmap — Agent Workforce / Engineering Dashboard

This is a planned post-Phase-1 capability, not an instruction to interrupt or expand Issue #120. Begin only after the GitHub read-only Control Layer reaches the appropriate OWNER acceptance gate.

Purpose: evolve the Android app from GitHub engineering observability into an OWNER-facing Agent workforce control surface. GitHub remains the engineering source of truth; the app presents traceable Agent status rather than inventing a parallel task system.

### Role ownership

- **ChatGPT — Product Architect / Engineering Orchestrator / Progress Reporter**
  - discuss product direction with OWNER
  - translate requirements into scope / contracts / acceptance criteria
  - route work between Claude Code and Codex
  - architecture / integration review
  - summarize GitHub Evidence into OWNER progress reports
  - define OWNER acceptance flow
- **Claude Code — UI / Product Engineer**
  - Android UI / UX
  - Agent cards, screens, interactions, visual states
  - presentation and UI-state binding
  - OWNER Attention presentation
- **Codex — Backend / Platform Engineer**
  - GitHub Control Layer and engineering data path
  - Issue / branch / PR / workflow / checks state
  - domain / repository / service / parser / cache logic
  - automated tests / CI / integration validation
- **OWNER** retains final authority for product direction, subjective UX acceptance, architecture/security boundary changes, destructive/write permissions, merge, and production release.

Agents may make minimal cross-domain changes required to complete an approved task, but must not create competing implementations, duplicate ownership, or parallel Graph / identity / dedupe / merge / Canvas architecture.

### Planned Android capabilities

1. Agent workforce dashboard showing Claude Code, Codex, ChatGPT and future Agents.
2. Status vocabulary: `working`, `waiting`, `blocked`, `idle`, `review`.
3. Show current task, Issue, branch, PR, CI/check status and blocker where GitHub Evidence supports them.
4. OWNER Attention surface showing only decisions or gates that actually require OWNER action.
5. Tap an Agent to inspect its traceable GitHub evidence chain.
6. Reuse the existing Work Graph / Unified Graph / Dynamic Canvas and stable identity architecture.
7. Later phase: mobile assignment / approve / reject / review controls, subject to explicit write/security gates.

### Shared Agent status contract direction

Do not allow Claude Code and Codex to invent separate status schemas. Reuse existing models where possible; otherwise establish one shared executable contract with fields equivalent to:

```text
agent_id
agent_type
role
issue_number
branch
pr_number
current_task
status
ci_status
blocking_reason
owner_attention_required
owner_attention_reason
started_at
last_activity_at
evidence[]
```

All derived status must remain traceable to GitHub Evidence. UI presentation must not guess engineering state.

### Progress reporting layer

The same evidence model should later support ChatGPT / Engineering Orchestrator progress reporting:

- summarize what Claude Code is working on
- summarize what Codex is working on
- show integration / CI health
- identify blockers
- identify OWNER Attention
- support scheduled OWNER engineering reports
- eventually surface those reports directly inside the Android app

Scheduled reporting is a later capability; it is not part of Issue #120 Phase 1.
