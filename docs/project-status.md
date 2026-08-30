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
- Last known `release/android` baseline SHA before Phase 1 work: `15296c67f93abf1fa574196385647c7cea7b1051`
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
- Codex completed the first architecture / gap analysis and received approval to begin the minimal Phase 1 implementation.

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
- stop and report after a meaningful reviewable implementation slice rather than entering Phase 2 automatically

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

## 16. Planned roadmap material — Skill Platform / Agent Governance / Memory Intelligence

> Source: recurring GitHub trend intelligence. These items are **roadmap material / backlog inputs**, not active implementation instructions and must not expand Issue #120 Phase 1.
>
> Implementation rule: when one of these packages becomes active, first apply the repository's Reference-First process: inspect existing AMIN capability, search mature external references / official SDKs / reusable patterns, verify license and architecture fit, then choose `REUSE -> ADAPT -> EXTEND -> BUILD`. Record material reference evidence in the Issue / PR. Do not create a parallel Graph, identity, dedupe, merge, Canvas, Memory, Agent-status, or permission architecture.

### Package A — Skill Platform

Purpose: establish a stable platform contract so Android, future game-world clients, Personal Skills, Canonical Skills and third-party Creator tooling can refer to the same capability identity rather than inventing client-specific feature models.

Roadmap material:

1. **Skill Manifest / Skill Contract v0.1**
   - define stable `skill_id`, name, version and developer / owner identity
   - define profession / category and prerequisites
   - define capabilities / actions and tool dependencies
   - define input / output contract
   - define permission requirements
   - define Memory read / write scope
   - allow optional world representation metadata without coupling the core contract to one renderer
2. **Skill Registry**
   - one canonical registry for installed / available / enabled skills
   - preserve stable identity and version provenance
   - do not use UI navigation state as the source of truth
3. **Skill Tree projection**
   - treat the Skill Tree as a product / learning / game-world projection over Skill Registry + prerequisites
   - do not hard-bind Skill Tree structure one-to-one to Git branches or directory layout
4. **Canonical Skill vs Personal Skill**
   - Canonical Skill: reviewed, versioned, supported platform capability
   - Personal Skill: user-specific composition / workflow built from approved capabilities
   - a Personal Skill must not silently gain permissions beyond its component capabilities

Guidance before implementation:

- Search existing Agent Skill / plugin manifest specifications before defining a new schema.
- Prefer an executable / validated contract over documentation-only fields.
- Keep renderer metadata optional so Android Native UI, a future 3D world, Web or other clients can share the same Skill core.
- OWNER Gate is required for material security / permission / platform-contract decisions, not for ordinary implementation inside an approved contract.

### Package B — Agent Workforce Contract / Fleet State

Purpose: make Agent execution state a shared platform concept rather than a dashboard-specific UI model.

Roadmap material:

1. Formalize one **Agent Execution Status Contract** shared by Claude Code, Codex, ChatGPT and future Agents.
2. Reuse / extend the planned status fields in Section 15 rather than introducing a second status schema.
3. Separate evidence-backed facts from inferred / presentation-only status.
4. Define a platform-level Fleet State that can later be rendered by Android, Web or a game-world client.
5. Keep GitHub Evidence traceability for engineering Agents; do not fabricate work state from UI activity alone.

Guidance before implementation:

- Study mature Agent harness / fleet-control patterns for work isolation, status, task ownership, waiting / blocked semantics and evidence provenance.
- The Android Workforce Dashboard is one projection of Fleet State, not the owner of Fleet State.
- Do not enter assignment / execution / write control until the corresponding OWNER security gate is approved.

### Package C — Trust & Governance

Purpose: establish a reusable permission and certification model before Agents / Skills can execute increasingly powerful actions.

Roadmap material:

1. Explore a shared permission-level vocabulary equivalent to:

```text
L0 OBSERVE
L1 ANALYZE
L2 PROPOSE
L3 EXECUTE
L4 MODIFY
L5 DESTRUCTIVE / RELEASE
```

2. Map actual capabilities to explicit permissions rather than trusting a display-level label alone.
3. Add Skill security / certification gates before third-party or generated Skills can enter the canonical environment.
4. Future Creator / Developer certification should be evidence-based: training / Quest evidence, Sandbox work, automated tests, security checks, architecture checks and review.
5. Preserve explicit OWNER gates for destructive, release, credential, security-boundary and high-risk permission changes.

Guidance before implementation:

- Do not interpret the L0-L5 vocabulary as an already-approved authorization implementation; it is a roadmap model to research and validate.
- Prefer least privilege and capability-specific grants.
- Generated / Personal Skills remain sandboxed until their permissions and dependencies are known.
- Third-party Skills must have provenance, version and review evidence before canonical publication.

### Package D — Memory Intelligence / Provenance

Purpose: make future Brain / Memory results explainable and traceable instead of storing decontextualized facts.

Roadmap material:

1. Define Memory provenance fields / relations for source, creation time, producing Agent / conversation, supporting evidence, confidence, relationships and verification state.
2. Preserve the distinction between user-provided memory, Agent-derived inference and externally retrieved knowledge.
3. Allow UI / future game-world clients to answer: "Where did this memory come from?" without duplicating the underlying Memory model.
4. Use provenance to support later consolidation, conflict detection, confidence updates and evidence inspection.

Guidance before implementation:

- First inspect the existing Brain / Neural Memory foundations and current Graph / Evidence models.
- Extend the canonical Memory architecture when appropriate; do not create a second Memory Graph merely for provenance visualization.
- External knowledge must retain source / retrieval provenance where technically available.

### Lower-priority research material

- **Architecture / Diagram Skill** — investigate reusable diagram-generation / architecture-documentation Skills; likely `REUSE / ADAPT`, but not a core runtime priority.
- **Local / device-side Agent runtime** — monitor and research when Android hardware / model constraints justify it; do not force it into the current architecture.
- **Physical-device Agent control** — retain as future / currently unrelated research unless a concrete product requirement appears.

### Suggested activation order after the current Phase 1 gate

When OWNER chooses to activate these roadmap packages, prefer:

```text
1. Skill Contract / Registry research
2. Agent Execution Status Contract / Fleet State
3. Permission / Governance contract
4. Memory Provenance
5. Creator / Certification expansion
```

This ordering is guidance, not automatic authorization to start a new phase. Each activated package must have an Issue / acceptance boundary and must follow the top-level Agent preflight / continuous-execution rules once its direction is approved.
