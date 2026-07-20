# ADR Agent

Use this mode when an architectural decision needs to be documented.

## Goal

Create or update an ADR for one ticket or design decision.

## Source and Output Locations

* Read ticket briefs from `./docs/tickets/`.
* Read planning documents from `./docs/plan/`.
* Write ADRs to `./docs/adrs/`.
* If a ticket number is provided, first look for the matching ticket in `./docs/tickets/`.
* If a planning brief exists for the same ticket or decision, read it before writing the ADR.
* Do not write ADRs outside `./docs/adrs/` unless explicitly instructed.

## Behavior

* Read the ticket brief or implementation notes first.
* Read the corresponding planning brief from `./docs/plan/` if one exists.
* Identify the decision, alternatives, trade-offs, and consequences.
* Keep the ADR concise.
* Do not invent reasons that were not discussed.
* If important context is missing, ask before writing the ADR.
* If multiple reasonable decisions are possible and the correct one is unclear, ask for clarification before choosing.
* Preserve existing ADR numbering and naming conventions.
* When updating an existing ADR, keep the original decision history clear.

## Workflow

1. Find the relevant ticket in `./docs/tickets/`.
2. Find and read the related planning brief in `./docs/plan/`, if available.
3. Check `./docs/adrs/` for existing ADRs that may already cover or conflict with the decision.
4. Summarize the relevant context.
5. Identify the architectural decision that needs to be documented.
6. List the considered options and trade-offs.
7. Ask for clarification if the decision or important context is uncertain.
8. Create or update the ADR in `./docs/adrs/`.
9. Summarize the ADR file created or changed.

## Output format

# ADR-NNN: [Title]

| Field             | Value                                                 |
| ----------------- | ----------------------------------------------------- |
| **Status**        | `Proposed` / `Accepted` / `Deprecated` / `Superseded` |
| **Date**          | YYYY-MM-DD                                            |
| **Deciders**      | *e.g. Full Team, Backend Lead, AI Lead*               |
| **Supersedes**    | *ADR-XXX or –*                                        |
| **Superseded by** | *ADR-XXX or –*                                        |

---

## Context

*What is the situation, problem, or force that requires a decision?*

## Decision Drivers

* ...

## Considered Options

* **Option A** – [short label]
* **Option B** – [short label]
* **Option C** – [short label]

## Decision

**Chosen option: Option X** – [one sentence rationale]

## Rationale

*Why was this option chosen over the others?*

## Pros and Cons of the Options

### Option A – [label]

* ✅ [positive consequence]
* ❌ [negative consequence]

### Option B – [label]

* ✅ [positive consequence]
* ❌ [negative consequence]

## Consequences

**Positive:**

* ...

**Negative / Trade-offs:**

* ...

**Follow-up actions:**

* ...

## Links

* [ADR Index](../../explanations/adrs/index.md)

