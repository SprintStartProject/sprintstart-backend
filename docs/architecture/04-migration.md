# 4 Migration

From the [as-is](02-as-is.md) to the [target](03-target.md) in small PRs. Every PR passes the full
gate set, and none changes behaviour or a URL. Each stage has an exit measure, so progress shows
up as numbers.

## 4.1 Tracked numbers

| Number | Today | Target | Source |
|---|---:|---:|---|
| Known Modulith violations | 110 (1 after stage A) | 0 | `known-module-violations.txt` |
| Lines in `onboarding` | 52,734 | 0 (package gone) | line count |
| Cross-feature edges against the layering | 59 of 500 | 0 | `allowedDependencies`, after stage C |
| Modules with declared `allowedDependencies` | 0 | all | `package-info.java` |
| Event listeners that survive a crash (`@ApplicationModuleListener`) | 0 | every listener that calls the AI or an external system | grep |

## 4.2 Stages

### Stage A: remove the cycles (cheap, high yield)

| PR | Change | Exit measure |
|---|---|---|
| A1+A2 (#218) | Delete `shared/seeder/DatabaseSeeder` (it only forwarded to `BlueprintSeeder`, which becomes the runner itself). Move `ApplicationConfig` from the root package to `shared.config`. Delete `WebRequestClient`, which only its own test used. | 110 → 37 |
| A3 (#219) | The Jira issue payload moves next to the event that carries it. `IngestionResponseException` moves to `ingestion`, `KeycloakRoleMapper` to `user`. Named interfaces for what other modules already used. `SessionActivityApi` instead of `user`'s internal service. | 37 → 5 |
| A4 (#220) | The Confluence and Notion write boundaries move into the connectors, and `ingestion` implements them. `ProjectSourceApi` moves into `user`, and `connectors` implements it. Project deletion reaches the connectors through `ProjectDeletedEvent`. | 5 → 1 |

The one entry left, `insights ↔ onboarding`, needs the Buddy's question log to move (B7).

Two changes from the original plan. The connectors do not publish canonical artifacts. Instead, synchronous calls that pointed upward were inverted (target §3.3, rule 3), which removed the cycles without redesigning run bookkeeping. And `config` turned out to be a composition root rather than kernel.

### Stage B: untangle inside `onboarding` (no package moves yet)

Each PR removes one cause from the [back-edge table](02-as-is.md#24-the-hidden-structure-inside-onboarding):

| PR | Change | Removes |
|---|---|---|
| B1 | `UserDeletedListener` becomes one listener per feature, each deleting its own data. A test asserts that every entity with a user id is covered. | 14 back edges, implements P5 |
| B2 | Split `OnboardingAiClient` into one client per feature (path, starter work, orientation, board diagrams, assistant). | 4 back edges, implements P6 |
| B3 | Move `OnboardingPathFromBlueprintFactory` to the path side. `blueprint` offers a read API for published blueprints, and the path stops reading blueprint repositories. | 34 edges, the blueprint ↔ path cycle |
| B4 | Extract the agent protocol (`BuddyAgentDtos`, proposal risk/service/repository, `BuddyStreamEvent`, the turn relay, tool-call argument readers, stored params) into one package, the future `assistant`. | 51 of the 68 teamspace → buddy edges |
| B5 | Goal changes publish an event that the board listens to. `UserGoalService` stops calling `BoardService`. | competency → board |
| B6 | Re-file the misfiled classes (`TeamOverviewUserDto`, `SaveBoardStructureRequest`, `OpenAreas`, `BuddyTeam*` on the Buddy side). | the remaining back edges |
| B7 | The Buddy's question log (`BuddyQuestion`, `QuestionAskedEvent`, `BuddyQuestionApi`) moves to `knowledge`. The Buddy records into it, and `insights` reads from it. | the `insights ↔ onboarding` cycle, **known violations 0** |

Exit: [`features.py`'s](README.md#how-the-numbers-were-produced) back-edge list is empty.

### Stage C: move the features out, one module per PR, bottom-up

Order: `assistant` → `blueprint` → `path` → `knowledge` → `arrival` → `starterwork` → `board` →
metrics into `insights` → `buddy` → `teamspace`. Each PR:

1. moves the feature's files to `com.sprintstart.sprintstartbackend.<feature>` (pure moves, no edits
   beyond package and import lines),
2. adds `package-info.java` with `@ApplicationModule(allowedDependencies = …)` per the
   [layering table](03-target.md#32-modules-and-layers),
3. moves the feature's tests the same way and re-keys the path-keyed quality files (detekt baseline,
   `known-*.txt`, ratchet history).

Exit: `onboarding` no longer exists. `ModuleBoundariesTest` passes with an empty known list.

Done (#231, #233, #235–#241 and this PR): `onboarding` is gone, and `ModuleBoundariesTest` passes with
an empty known list. Every feature module declares its allowed dependencies. Where a higher module
used to hold a lower one's entities, the lower module now answers in DTOs with typed ids (`ArrivalSteps`,
`StarterWorkPool`, `KnowledgeLookup`, `QuestionAttemptReader`, `ResolvedPathStep`).

Interim: most features still expose their `service` package and their request and response DTO
packages as named interfaces (`api-services`, `api-requests`, `api-responses`), so higher modules
call the services directly. Entities, repositories and mappers stay hidden (P4).

`path` is the first to replace that with an API (#244, #245, #246), and the template for the rest:
interfaces in `path/external` cut **by caller** (the board and knowledge read through `PathReadApi`,
the Buddy through `HirePathApi` and `HirePathActionsApi`, team mode through `PathReviewApi`,
`PhaseAuthoringApi`, `StepAuthoringApi` and `TaskAuthoringApi`), with **typed ids**, each
implemented by one facade in `path/service` over the services that own the calls. Detekt's limits
(11 functions per interface, 4 parameters) split a large caller once more. `path/service` is no
longer a named interface, so `ModuleBoundariesTest` keeps every caller on the API.
`knowledge` (#247), `arrival` (#248), `starterwork` (#249), `board` (#250) and `insights` (#251)
followed the same way. Team mode uses the Buddy's conversation machinery rather than its features, so that
moves into the `assistant` kernel instead of behind a Buddy API
([ADR-proposed-004](../adrs-proposed/adr-proposed-004-assistant-kernel-and-tool-spi.md)): the agent loop's
pieces in #252, compaction in #253. `buddy/service` is no longer a named interface. With that, `assistant` uses the platform (user, ingestion, upload) and
sits in L1 rather than L0.

Not yet declared: `shared`, `user`, `connectors`, `config` (the composition root) and `health`.
They depend on nothing above them today, but nothing enforces it.

### Stage D: decisions that need the new structure

| Item | Note |
|---|---|
| Tool SPI ([ADR-proposed-004](../adrs-proposed/adr-proposed-004-assistant-kernel-and-tool-spi.md)) | Features contribute their tools and actions from `<feature>/assistant/`. This replaces the hand-written routers and absorbs ADR-proposed-001 option 2. |
| `@ApplicationModuleListener` for AI-calling and sync listeners | Needs Modulith's event publication table, i.e. a schema change. |
| Versioned schema migrations | `ddl-auto: update` and the startup fixers are replaced by Flyway (or Liquibase). Needs its own ADR. |
| Second split of `blueprint` / `path` | Decide from the measured sizes after stage C. |
| Per-module configuration | Split `ApplicationConfig` into one `@ConfigurationProperties` class per module. |

## 4.3 Risks

- **Path-keyed quality files.** Detekt baseline IDs, `known-raw-id-parameters.txt` and ratchet entries
  carry file paths, so every move re-keys them. Regenerate them and verify the PR re-keys only,
  as for earlier splits.
- **Moved code counts as added code** for the mutation gate. Moves without edits keep tests intact.
  Where the gate still demands it, write the missing tests up front.
- **Rule in `AGENTS.md`:** "Do not move large parts of the project without need". These ADRs
  are the stated need, and the moves happen in small PRs.
- **Upstream divergence.** All of this happens in the fork. After stage C, changes from upstream
  `dev` no longer merge cleanly. Decide before stage C whether the fork stays the only line of
  development.
