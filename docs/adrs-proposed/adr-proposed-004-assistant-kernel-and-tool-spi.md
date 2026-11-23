# ADR (proposed) 004: An `assistant` kernel, and tools contributed by the features

**Status:** Accepted (2026-10-19, confirmed by the team).
**Builds on:** ADR-proposed-001 (split the Buddy's actions; option 2 there was a registry).

## Context

There are two assistants, the hire's Buddy and the PM's team mode. Both run the same protocol:
they send an agent turn to the AI service, relay its stream to the browser, run the backend tools
the AI asks for, and turn write actions into proposals that a human confirms.

That protocol grew inside the Buddy, because the Buddy came first. Team mode reaches into the Buddy
68 times, and 51 of those edges are protocol (`BuddyAgentDtos`, `BuddyProposalRisk`,
`BuddyStreamEvent`, the turn relay, `BuddyProposalService`) rather than Buddy behaviour. The tools
and actions themselves live in the assistants: `BuddyBoardTools`, `BuddyPathActions`,
`StarterWorkTeamActions` and so on. So a new board action changes `buddy`, and often `teamspace`
too, and both reach deep into the board.

Drivers: Q1 changeability (the most frequent kind of change is "the Buddy can now do X"), Q3
testability.

## Options

1. **Kernel only.** Move the protocol into an `assistant` module below both assistants, and keep
   the tools in the assistants. Small and safe, but adding an action still means editing an
   assistant's router.
2. **Kernel plus a tool SPI.** The kernel also defines the interface for a tool or action (spec,
   read, or propose/perform). Each feature implements its tools in `<feature>/assistant/` against
   its own services. The assistants collect the tools from the Spring context and filter them by
   role (hire or PM). This generalizes the `TypedTeamAction` registry team mode already has.
3. **Tools as a separate module per assistant.** Avoids touching the features, but recreates
   today's coupling under a new name.

## Decision

Option 1 now ([migration B4](../architecture/04-migration.md#stage-b-untangle-inside-onboarding-no-package-moves-yet)),
option 2 once the feature modules exist ([stage D](../architecture/04-migration.md#stage-d-decisions-that-need-the-new-structure)).
The kernel goes to L0, so features may depend on it to contribute tools.

## Consequences

- A new action "move a board card" touches `board` only: it adds one class in `board/assistant/`.
  This is the Q1 scenario in the drivers.
- The assistants shrink to conversation, sessions, memory and the agent loop.
- The kernel must stay small and stable, because every feature depends on it. Its size is tracked.
- The hand-written dispatch in `BuddyActionService` and its relatives disappears. Until then, the
  two patterns (router and registry) coexist.
