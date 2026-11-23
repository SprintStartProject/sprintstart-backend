# ADR (proposed) 001: Split the buddy's action components by action

**Status:** Accepted (provisionally by agent 2026-10-18, confirmed by the team 2026-10-19).
Superseded by option 2 through [ADR-proposed-004](adr-proposed-004-assistant-kernel-and-tool-spi.md)
(#262–#265): every action is now a `HireAction` bean, and the routers named below no longer exist.

## Context

The buddy's actions live in a few components that grew one action at a time: `BuddyPathActions`
(1006 lines, five actions), `BuddyActionService` (877), and the board pair that was already split
once (`BuddyBoardWriteActions` → `BuddyBoardEditActions`). Each action is a propose/perform pair
plus a tool spec, and the pairs share little beyond the router that dispatches them.

Files this size are over the 400-line limit and the ratchet lets them shrink but never grow, so
every other quality change that touches them (typed ids, an injected dispatcher, a lint fix) is
blocked or has to be squeezed around them. The professor's review named long, multi-purpose
classes as the main weakness of the codebase.

## Options

1. **Split by action behind the existing router.** Each action, or a small group that shares a
   subject, becomes its own `@Component` with `propose`, `perform` and its spec; the existing
   component keeps `specs`/`handles`/`propose`/`perform` and delegates. This is what
   `BuddyBoardWriteActions` already does with `BuddyBoardEditActions`.
   - Small, reviewable PRs, one action at a time; no change to `BuddyActionService` or the API.
   - The router stays as a hand-written `when`; adding an action still touches it.
2. **One `BuddyAction` interface and a registry.** Every action implements
   `spec / propose / perform`, Spring collects them, and the router becomes a lookup — the shape
   team mode already has with `TypedTeamAction`.
   - Removes the hand-written dispatch entirely; adding an action is adding a class.
   - A large cross-cutting change (every action at once, `BuddyActionService` included) and a
     second pattern next to team mode's until both are unified.
3. **Leave the files and raise or except the limit.** Nothing to review now; every later change to
   these files stays blocked or needs an exception.

## Decision

Option 1, now: it unblocks the oversized files one reviewable PR at a time and matches a pattern
the codebase already has. Option 2 remains the direction once the actions are separate classes —
each extracted component is already shaped like a registry entry.

## Consequences

- New components per action (first: `BuddyQuestionActions`), each with its own spec, and a shared
  `PathActionSupport.kt` for what the path actions have in common (the "not proposed" refusal and
  the lenient list readers).
- A pure move per PR; review it with `git diff --color-moved`.
- The router's `handles`/`propose`/`perform` keep listing every action until option 2.
