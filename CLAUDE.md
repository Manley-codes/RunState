# RunState

Android running app (Kotlin/Compose), shared Java backend, and native SwiftUI iPhone app,
grown from the original Java console project. Learning project and portfolio piece for
Manley Johnson (CS student, SNHU).

## Project memory
Full context is in `docs/design/`. Read `docs/design/MEMORY.md` first, then relevant detail files, before starting any work.
Read the current delivery sequence in `docs/local/november_2026_demo_roadmap.md` when available.
Private files are not copied automatically between Git worktrees. During the independent
iPhone assignment, use the canonical handoff named below; Codex maintains the main memory
and roadmap, while Claude records iPhone progress/review items in that handoff.

## How decisions get recorded
Manley works on this project with an agile mindset. He revises freely and expects to.
- **Default status is CURRENT, not locked.** A recorded decision is the working answer at the time it was made — a reference, never authority against a new idea. The few genuinely locked items say so and name what would reopen them.
- **Do not harden an agreement into a principle.** Him agreeing to something once is a choice, not a rule. Record it as the current choice with its reasoning.
- **Reasoning is recorded so it can be re-examined later, not to stop it being re-argued.** A decision being revisited is the process working, not backsliding.
- **A rule written for one context does not automatically govern another.** Check what an existing rule was actually written to govern before citing it against a new idea, and give its reasons rather than its authority.
- **Lead with what an idea makes possible before what constrains it.** Constraints are work items, not verdicts.

## Build & run
Android: `android/` (Gradle). Backend: `backend/` (Maven, Java 17).
iPhone: `ios/RunState.xcodeproj` (SwiftUI, Xcode); the existing GitHub Mac workflow runs its
simulator tests and packages the unsigned field app. Windows source checks are not Xcode tests.
Legacy console main class: `com.runstate.App`; source root: `src/`.

## Scope rule
No large outside-of-scope features without explicit agreement.
Core: log a run → track how you felt → learn something meaningful from it.

## Tech stack
Android uses Room/SQLite for local runs; iPhone preserves its own local saved-run journal.
Provider credentials remain on the backend. The legacy MySQL app uses a dedicated DB user.

## Collaboration rules
- November 20, 2026: Manley approved independent iPhone implementation/testing by Claude in the existing `ios/run-completion-and-history` branch and `RunState-iphone-journey` worktree. Continue bounded, reviewable work without routine step approvals. This supersedes the older approval-before-every-edit and Claude-default rules.
- Claude owns `ios/**`, necessary `.github/workflows/ios.yml` changes and the canonical private handoff at `C:/Users/Owner/IdeaProjects/RunState-iphone-journey/docs/local/claude-ios-handoff-2026-11-20.md`. Codex owns Android, backend/contracts, shared assets/general CI, main project records and integration into master. Request cross-boundary work in the handoff; continue another independent task meanwhile.
- Work independently on accepted behavior and verified defects. Preserve the accepted reflection writer/song selection and existing saved data. Do not redesign screens, add major features or resume the locked-song recognition experiment without an agreed scope.
- This is a learning project — explain significant or unfamiliar Java/OOP and architecture decisions by connecting them to this codebase; do not require the user to type every line or re-explain familiar basics
- Ask only for an essential product decision, credential, new spending/deployment approval or device action. Save ordinary review/phone-test items in the handoff and continue computer-side work. No desktop takeover or repeated installation/listening requests while Manley is busy.
- Use plain English, explain unfamiliar technical decisions briefly, and give progress updates during longer work. Reports should distinguish implemented, automatically verified and physically verified.
- Always flag when it's a good time to commit
- Standing authorization: Codex handles commits/pushes until Manley takes over. For the approved independent iPhone assignment Claude may also stage, commit and push its scoped changes to `ios/run-completion-and-history`; no checkpoint approval is needed. Stage exact files, keep commits reviewable and check the existing draft PR's automated results.
- Codex coordinates shared updates and merges into master. Claude must not merge/push to master, force-push, rewrite shared history, discard another worker's changes or alter another worktree. Keep the existing draft PR; no automatic merge.
- Do not install/uninstall phone apps, erase data, make paid provider calls, change live hosting/budgets or publish a release as routine verification. Use simulated providers and existing GitHub checks; physical tests remain deferred.
- Never replace existing code blocks with placeholder comments during edits; preserve real assignment/logic lines and only modify what was requested

## Key architecture rules
- loadRun() for sample data (no PR announcements), addRun() for new runs (announces PRs)
- buildRunResponse() now lives in RunAgent.java — keep it isolated and clean
- RunStyle detection lives in RunStyleService.java (SRP); Runner.detectRunStyle(Run) only delegates. The profile is local and deterministic — never sent to the AI. See docs/design/design_runstyle_v1.md
- Never mention below-average performance in run responses — stay quiet if numbers are down
