# Backend architecture

SprintStart's backend was built feature by feature without an architecture worked out up front.
This folder reconstructs that architecture after the fact and sets a target for it, in four steps.
Each step builds on the one before it:

| Step | Document | Question it answers |
|---|---|---|
| 1 | [Drivers](01-drivers.md) | What does the architecture have to achieve, and within which limits? |
| 2 | [As-is architecture](02-as-is.md) | What did we actually build? Recovered from the code, not from memory. |
| 3 | [Target architecture](03-target.md) | Which structure serves the drivers, and which rules keep it in place? |
| 4 | [Migration](04-migration.md) | How do we get from step 2 to step 3 in small, verifiable steps? |

Decisions behind the target are recorded as ADRs. The accepted ADRs (ADR-001 to ADR-020) live in the
[Wiki](https://github.com/SprintStartProject/Wiki/tree/main/docs/source/explanations/adrs). New ones
are in [`../adrs-proposed/`](../adrs-proposed/); the folder name is historical, each file states its status.

## How the numbers were produced

The as-is numbers come from the source tree at the commit named in each document:

- **Dependency graph:** a file-level graph of `src/main/kotlin`. Edges come from identifiers resolved
  against top-level, non-private declarations. Imports decide between homonyms, and references within
  the same package count even though they need no import.
- **Feature clusters:** Louvain community detection on the `onboarding` subgraph (resolution 1.0).
- **Module violations:** Spring Modulith's own verification, i.e. `ModuleBoundariesTest` and
  `src/test/resources/architecture/known-module-violations.txt`.

The scripts are not part of the build. Once the target is in place, the
[enforcement rules](03-target.md#enforcement) keep these numbers current instead.
