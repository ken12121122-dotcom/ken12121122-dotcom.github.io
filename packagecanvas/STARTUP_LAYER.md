# PackageCanvas · Startup Planning Layer v0.1

Status: generated / candidate / not deployed or approved.
Branch: feature/packagecanvas-startup-switch-layer-20261013
PR merge requires explicit owner approval.

## Routing
- Main index desktop Tools menu -> 🚀 創業計畫／公司設立.
- Mobile More sheet -> 🚀 創業計畫／公司設立.
- Separate startup.html with back links to index and research navigation.

## Features
- Five views: Overview, Registration Tasks, Research/Product, Finance/Exit, Settings/Backup.
- 24 template tasks with editable dates, checkboxes, evidence and notes.
- Contexts: flexible gap, focused personal work, weekday outreach, counter visit.
- Suggested planning windows only; not legal deadlines, bank reservations, or Calendar events.
- No scheduled tasks between 2026-11-05 and 2026-11-09.
- Candidate research and education-AI products are not validated.

## Privacy and governance
- This is a PUBLIC repository. No private GEN2 knowledge, real residential address, credentials or proprietary employer documents belong in code.
- User input is stored only in the local browser via localStorage key packagecanvas-startup-layer-v1.
- Manual JSON backup export/import; no cross-device sync.
- Task completion is not legal compliance, government approval, accountant attestation or OWNER approval.
- Formal company, capital, tax, IP and legal choices require human approval and authoritative source checks.
- Do not modify main or existing Knowledge World, GEN2, Android shell or private Drive knowledge base.

## Acceptance checklist
1. Both mobile and desktop entry points open startup.html.
2. Five views can be switched on mobile.
3. All 24 tasks appear and filters work.
4. Checkbox/date/evidence/notes persist after page reload on same device.
5. JSON export works; import validates schema and requests confirmation.
6. Private goal inputs are not committed into public repository.
7. Browser/device testing completed before owner merges PR.
8. Existing canvas, GEN2 and knowledge-world regressions absent.

## Rollback
- Before merge, discard draft PR/branch if tests fail.
- After merge, owner can revert PR; this feature does not modify original canvas data.

## Official sources for human verification
- https://onestop.nat.gov.tw/
- https://law.moj.gov.tw/LawClass/LawAll.aspx?pcode=J0080001
- https://gcis.nat.gov.tw/
- https://www.etax.nat.gov.tw/

## v0.2: Calendar deep links and complete task playbooks
- Calendar integration intentionally removed: no OAuth, Google Calendar API, automatic event creation, bidirectional sync, or calendar data read.
- Each task shows a direct Google Calendar date-view hyperlink based on the task's current local editable due date.
- Each task offers a Google Calendar event-create link with a prefilled all-day date, task title, generic prerequisites, actions, materials, completion criteria and risk warnings.
- The user must manually save the draft in Google Calendar. Previously saved calendar events will NOT change when the local task date changes.
- Dedicated startup-task-details.js supplies 24 generic, public playbooks; no private local notes/evidence/addresses are included in Calendar URLs.
- Existing localStorage key/schema is unchanged. Completion state, private fields and user-entered data remain on device.
- Research model includes independent research, validation, education, software/IP licensing, delegating operations, future sale and investment-supported financial independence.

## v0.2 regression checks
1. All 24 tasks render with full instructions and evidence requirements.
2. Every task exposes a day-view link and prefilled event draft link.
3. Google Calendar all-day dates use an EXCLUSIVE following end date and react to local due-date changes after rerender.
4. No user-specific fields are included in Google Calendar links.
5. Checkbox, task dates, notes, evidence and JSON backup remain locally stored.
6. Browser/device acceptance still recommended; no claim that user Calendar contains unsaved tasks.
