# ADR (proposed) 002: Dissolve `onboarding` into layered feature modules

**Status:** Accepted (2026-10-19, confirmed by the team).
**Amends:** ADR-006 (Spring Modulith), ADR-008 (module structure).

## Context

ADR-006 chose Spring Modulith so that module boundaries would be "explicit and verifiable". In
practice, every feature built after June 2026 (Buddy, board, arrival, starter work, competency,
orientation, team mode) went into `onboarding`, because the hire's data lived there. The module
now holds 52,734 lines (53% of the backend), with a flat `service/` package of 117 files. Modulith
can only check boundaries *between* modules, so inside `onboarding` nothing is checked.

The dependency graph shows that the features exist anyway: Louvain clustering gives a modularity
of 0.68, and 88% of cross-feature edges already follow one consistent direction
(see [as-is §2.4](../architecture/02-as-is.md#24-the-hidden-structure-inside-onboarding)).

Drivers: Q1 changeability, Q2 understandability, Q3 testability ([drivers](../architecture/01-drivers.md)).

## Options

1. **Keep `onboarding` and sub-package it by feature.** The packages would be clearer, but Modulith
   does not check sub-packages, so the boundaries would erode again. Nested application modules
   could check them, at the price of one extra level of nesting in every package name.
2. **Feature modules at the top level with a declared layering.** Each feature becomes a Modulith
   module, and `allowedDependencies` encodes the layering, so `ModuleBoundariesTest` checks it.
   - The boundaries are checked by the tool we already chose (ADR-006).
   - Package moves across about 500 files, done in roughly ten PRs.
3. **Leave the structure and only fix the back edges.** Lowest effort, but nothing stops the next
   feature from going into the same module.

## Decision

Option 2. The modules are `blueprint`, `path`, `arrival`, `starterwork` (with competency, goals and
orientation), `knowledge`, `board`, `buddy`, `teamspace` and the kernel `assistant`. Onboarding
metrics move to `insights`. The layering in
[target §3.2](../architecture/03-target.md#32-modules-and-layers) is declared per module and
tested.

ADR-008's layout is kept inside each module. The pattern does not change, only where the module
boundaries are.

## Consequences

- A feature is one package that a developer can find by its domain name. Module size becomes a
  tracked metric.
- Back edges must be removed before the moves ([migration stage B](../architecture/04-migration.md#stage-b-untangle-inside-onboarding-no-package-moves-yet)).
  That is work, but each one fixes a real design problem.
- Path-keyed quality files (detekt baseline, known-violation lists) are re-keyed with each move.
- Merging upstream into the fork becomes impractical after the moves.
