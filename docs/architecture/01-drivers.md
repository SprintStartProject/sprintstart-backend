# 1 Drivers

> **Status: confirmed (2026-10-19).** Nobody wrote these drivers down at the start. They were
> reconstructed from the product (Wiki, final presentation, demo runbooks), from the accepted ADRs
> and from what the code and the cluster setup show the system had to cope with, and then
> confirmed by the team.

## 1.1 What the system is for

A new hire joins a software team, and what they need to know is scattered across code, tickets,
documentation, chat and people. Their project manager (PM) plans each onboarding by hand and
answers the same questions over and over.

SprintStart reads a project's own sources (GitHub, Bitbucket, Jira, Confluence, Notion, uploads)
into one knowledge base and uses it in two ways:

- **Path:** a personalized onboarding plan. It is built from a template the PM curates (the
  *blueprint*) and carries arrival steps, phases, checks and first tasks. A board shows the plan.
- **Buddy:** an assistant that answers the hire's questions with citations and acts on their path
  with confirmation. A question it cannot answer goes to the PM, and the PM's confirmed answer is
  reused for later hires. The PM has a Buddy of their own, *team mode*, for running the team's
  onboarding.

## 1.2 Stakeholders

| Stakeholder | Expects from the architecture |
|---|---|
| New hire | Answers and a plan that fit their project and their skills. A failing AI call must not lose their progress. |
| Project manager | One place to curate the plan and to answer escalations once. Control over what the Buddy changes. |
| Developers (rotating student team, 3 sub-teams) | Finding the code for a feature quickly. Changing one feature without breaking another. |
| Operators (the team itself, university cluster) | A system that fits the memory limits and can be deployed with ArgoCD. |
| Examiners | A traceable path from goals to structure, and evidence that the structure holds. |

## 1.3 Quality goals

These are ordered by priority. When two goals conflict, the higher one wins.

| # | Goal | Why it matters here |
|---|---|---|
| Q1 | **Changeability of features** | The product changed course several times: tracks were dropped, the PM-only path was rebuilt, and team mode and the Buddy tool set were added late. New AI features keep arriving as tools and actions. |
| Q2 | **Understandability** | The team rotates and is mostly students. A feature has to be findable and readable on its own. Ironically, this is an onboarding product. |
| Q3 | **Testability** | AI behaviour is nondeterministic. Everything around the AI call (tools, actions, persistence, contracts) must be testable without it, and the user journeys end to end. |
| Q4 | **Robustness against the AI service** | LLM calls take 5–30 s and sometimes fail midway. A failed or cancelled turn must leave a consistent state and a readable reply. |
| Q5 | **Data protection and isolation** | Projects hold company knowledge and personal data. A user only sees their own project's data. Deleting a user removes their data in every feature. |
| Q6 | **Operability on limited resources** | One backend instance per environment on a shared university cluster, with tight memory limits. |

## 1.4 Quality scenarios

These make the goals testable. *Measure* names what the [migration](04-migration.md) tracks.

| Goal | Scenario | Measure |
|---|---|---|
| Q1 | A developer adds a new Buddy action on the board (e.g. "move a card"). | The change touches the board feature and the Buddy's tool registration only. No other feature module changes. |
| Q1 | The PM-curated part of the path changes shape (as in the PM-only path rebuild). | Changes stay inside `blueprint` and `path`. Assistants and the board compile unchanged against `path`'s API. |
| Q2 | A new developer has to fix a bug in starter-work matching. | All starter-work code lives in one module of under 5,000 lines that has a README-level description. |
| Q3 | A refactoring moves code between classes. | Unit, contract and E2E suites run without the AI service (stub). Mutation testing gates the changed code. |
| Q4 | The AI service drops the stream halfway through a Buddy reply. | The hire sees an error event, not a hanging spinner. The partial reply is stored as incomplete. No half-applied action remains. |
| Q5 | A user is deleted. | Every feature that stores user data removes it in reaction to `UserDeletedEvent`, verified by a test per feature. |
| Q5 | A request asks for another project's path, board or answers. | Rejected by the project authorization of the owning feature. |
| Q6 | The backend starts on the cluster. | It starts within the probe window with the configured heap. Background work (AI sync, compaction, reconciliation) is bounded and configurable. |

## 1.5 Constraints

| Constraint | Source |
|---|---|
| Kotlin on Spring Boot, one deployable backend | ADR-009, ADR-006 |
| Spring Modulith for module boundaries | ADR-006 |
| Separate Python service for everything LLM, RAG and embeddings | ADR-001, team split |
| Keycloak for authentication and authorization | ADR-015 |
| PostgreSQL as the backend's only store. Vectors live in the AI service. | Deployment |
| Kubernetes on the university cluster, deployed by ArgoCD | `sprintstart-k8s`, `uni-cluster` |
| Canonical artifact schema between connectors, ingestion and the AI service | ADR-016 |
| Three sub-teams (backend, frontend, AI) with two-week sprints. The project has formally ended. | Organisation |

## 1.6 What is deliberately not a driver

- **Horizontal scaling.** One instance per environment is enough. In-memory state, such as the
  generation registry and SSE emitters, is acceptable as long as Q6 holds.
- **Extracting microservices.** ADR-006 keeps the option open, but no driver asks for it. Module
  boundaries serve Q1 and Q2, not distribution.
