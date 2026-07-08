# RunState

Java console running app. Learning project and portfolio piece for Manley Johnson (CS student, SNHU).

## Project memory
Full context is in `docs/claude-memory/`. Read `docs/claude-memory/MEMORY.md` first, then relevant detail files, before starting any work.
Both Claude Code and Cowork share this folder. Keep it up to date when decisions are locked in.

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
- This is a learning project — explain Java/OOP concepts, connect explanations to this codebase
- Present options with a recommendation, confirm before writing code
- Always flag when it's a good time to commit
- When explaining or implementing code, walk through changes line-by-line with educational pauses; the user is learning, not just shipping
- Never replace existing code blocks with placeholder comments during edits; preserve real assignment/logic lines and only modify what was requested

## Key architecture rules
- loadRun() for sample data (no PR announcements), addRun() for new runs (announces PRs)
- buildRunResponse() now lives in RunAgent.java — keep it isolated and clean
- Rolling average snapshot must happen BEFORE addRun() in logRun()
- Never mention below-average performance in run responses — stay quiet if numbers are down
