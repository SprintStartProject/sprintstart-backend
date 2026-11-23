# 4 Migration

From the [as-is](02-as-is.md) to the [target](03-target.md) in small PRs. Every PR passes the full
gate set, and none changes behaviour or a URL. Each stage has an exit measure, so progress shows
up as numbers.

## 4.1 Tracked numbers

| Number | Today | Target | Source |
|---|---:|---:|---|
| Known Modulith violations | 110 | 0 | `known-module-violations.txt` |
| Lines in `onboarding` | 52,734 | 0 (package gone) | line count |
| Cross-feature edges against the layering | 59 of 500 | 0 | `allowedDependencies`, after stage C |
| Modules with declared `allowedDependencies` | 0 | all | `package-info.java` |
| Event listeners that survive a crash (`@ApplicationModuleListener`) | 0 | every listener that calls the AI or an external system | grep |

## 4.2 Stages

### Stage A: remove the cycles (cheap, high yield)

| PR | Change | Exit measure |
|---|---|---|
| A1 | Delete `shared/seeder/DatabaseSeeder` (it only forwards to `BlueprintSeeder`). `BlueprintSeeder` becomes the runner itself. | 36 cycle entries gone |
| A2 | Move `ApplicationConfig` from the root package to `shared.config`. Delete `WebRequestClient`, which only its own test uses. | 37 cycle entries gone. Root package holds only the application class. |
| A3 | `ingestion` gets its own `IngestionResponseException`. Fix the single non-exposed references (`user` → `connectors`, `onboarding` → `SessionActivityService`, `insights` → Buddy question types exposed via `external`). | 10 entries gone |
| A4 | Connectors publish canonical artifacts (ADR-016). `ingestion` stops reading Jira, GitHub and Bitbucket response classes. Expose the connector event packages `ingestion` listens to. Break the last real cycles: `config ↔ user` (role mapping moves into `user`) and `connectors ↔ ingestion`. | 27 entries gone. **Known violations: 0.** |

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
