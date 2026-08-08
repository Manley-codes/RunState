# RunState

Java console running app. Learning project and portfolio piece for Manley Johnson (CS student, SNHU).

## Project memory
Full context is in `docs/claude-memory/`. Read `docs/claude-memory/MEMORY.md` first, then relevant detail files, before starting any work.
Both Claude Code and Cowork share this folder. Keep it current as decisions are made.

## How decisions get recorded
Manley works on this project with an agile mindset. He revises freely and expects to.
- **Default status is CURRENT, not locked.** A recorded decision is the working answer at the time it was made — a reference, never authority against a new idea. The few genuinely locked items say so and name what would reopen them.
- **Do not harden an agreement into a principle.** Him agreeing to something once is a choice, not a rule. Record it as the current choice with its reasoning.
- **Reasoning is recorded so it can be re-examined later, not to stop it being re-argued.** A decision being revisited is the process working, not backsliding.
- **A rule written for one context does not automatically govern another.** Check what an existing rule was actually written to govern before citing it against a new idea, and give its reasons rather than its authority.
- **Lead with what an idea makes possible before what constrains it.** Constraints are work items, not verdicts.

## Build & run
Main class: `com.runstate.App`
Source root: `src/`

## Scope rule
No large outside-of-scope features without explicit agreement.
Core: log a run → track how you felt → learn something meaningful from it.

## Tech stack
Java run-tracking app using MySQL for persistence. Use a dedicated DB user rather than root auth.

## Collaboration rules
- Small steps only — never implement a full plan without approval at each step
- Claude Code is the default code implementer after the user explicitly approves the exact bounded edit; Codex or another assistant implements code only when explicitly requested for that task
- This is a learning project — explain significant or unfamiliar Java/OOP and architecture decisions by connecting them to this codebase; do not require the user to type every line or re-explain familiar basics
- Present options with a recommendation, confirm before writing code
- Always flag when it's a good time to commit
- The user handles routine Git work. For an unfamiliar but career-useful Git operation, show the exact command and explain it before the user runs it; keep complicated or uncommon recovery operations assistant-guided
- Do not stage, commit, push, merge, or perform other routine Git mutations unless the user explicitly asks
- Never replace existing code blocks with placeholder comments during edits; preserve real assignment/logic lines and only modify what was requested

## Key architecture rules
- loadRun() for sample data (no PR announcements), addRun() for new runs (announces PRs)
- buildRunResponse() now lives in RunAgent.java — keep it isolated and clean
- RunStyle detection lives in RunStyleService.java (SRP); Runner.detectRunStyle(Run) only delegates. The profile is local and deterministic — never sent to the AI. See docs/claude-memory/design_runstyle_v1.md
- Never mention below-average performance in run responses — stay quiet if numbers are down
