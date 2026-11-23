# ADR (proposed) 003: Calls go down, events go up; `external` is the module API

**Status:** Accepted (2026-10-19, confirmed by the team).
**Amends:** ADR-007 (event-based communication), ADR-008 (the `api/` package).

## Context

ADR-007 says modules communicate *only* through events, and ADR-008 says the public `api/`
package holds *only* events. The code did something else, consistently:

- Every module exposes a package called `external/`, marked as a Modulith `@NamedInterface`, with
  synchronous interfaces such as `UserApi`, `ProjectMembershipApi`, `ArtifactIngestionApi` and
  `UploadApi`, next to its events.
- `onboarding` has 77 dependencies on `user` alone, 59 of them through `UserApi` and
  `ProjectMembershipApi`. They are queries ("who is this user", "is this user a member") that need an
  answer, and an event cannot return one.
- No listener uses `@ApplicationModuleListener`. There are 69 `@EventListener` and 9
  `@TransactionalEventListener`.

So ADR-007 was never followed, and it could not have been. What is missing is a rule for when to
call and when to publish.

Drivers: Q1 changeability, Q4 robustness, Q5 data protection.

## Options

1. **Enforce ADR-007 as written.** Every query becomes a request/reply over events. That is a lot
   of ceremony for in-process reads and gives no benefit in a single deployable.
2. **Allow everything through `external`.** This matches the code today but has no rule for
   direction, which is how the cycles in [as-is §2.6](../architecture/02-as-is.md#26-module-boundary-violations-spring-modulith) arose.
3. **Calls down, events up.** A module may call the `external` API of a module *below* it in the
   layering, for queries and for commands. A module never calls upward. When a lower module has to
   tell a higher one something, it publishes an event from its `external` package.

## Decision

Option 3:

- `external/` is the only public package of a module. It holds API interfaces, their DTOs and
  events. Entities and repositories are never in it.
- Downward calls go through `external` interfaces. Upward and sideways notifications go through
  events, for example `UserDeletedEvent` (every feature deletes its own data) or a goal change
  (`starterwork` → `board`).
- When a lower module needs an answer from a higher one synchronously, the call is inverted. The
  lower module declares the interface in its `external` package, and the higher module implements
  it. For example, `user` owns `ProjectSourceApi` and `connectors` implements it.
- A listener whose work must survive a rollback or crash (an AI call, an external sync) uses
  `@ApplicationModuleListener`, which records and retries the publication. A listener that must run
  in the publisher's transaction uses `@EventListener` and says so in its KDoc.

## Consequences

- The direction of every edge is checkable: `allowedDependencies` covers calls, and events flow
  against the layering.
- The ADR-008 package name changes from `api/` to `external/`. That is what the code already does,
  so no rename is needed.
- `@ApplicationModuleListener` needs Modulith's event publication table, which is a schema change
  ([migration stage D](../architecture/04-migration.md#stage-d-decisions-that-need-the-new-structure)).
