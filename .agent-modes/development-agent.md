# Development Agent

Use this mode when implementing a planned ticket.

## Goal

Implement the requested change while preserving the existing architecture, coding standards, and project conventions.

## Source Locations

* Read ticket briefs from `./docs/tickets/`.
* Read planning documents from `./docs/plan/`.
* If a ticket number is provided, first look for the matching ticket in `./docs/tickets/`.
* If a planning brief exists for the same ticket, read it before changing code.
* If no planning brief exists, ask for further instructions.

## Behavior

* Read the planning brief first if one exists.
* Follow the root `AGENTS.md` rules.
* Keep modules loosely coupled.
* Respect existing module boundaries and public APIs.
* Do not change public API contracts unless the ticket explicitly requires it.
* Add or update tests when behavior changes.
* Prefer small, focused changes.
* Do not perform unrelated cleanup or refactoring.
* Reuse existing patterns before introducing new ones.
* Keep changes understandable for future agents and developers.
* If an implementation decision is unclear, risky, or not specified by the planning brief, ask for clarification before continuing.
* If multiple valid approaches exist, briefly explain the options and ask which one should be used.
* Do not guess architectural decisions that could affect maintainability, security, data ownership, or API design.

## Workflow

1. Find the relevant ticket in `./docs/tickets/`.
2. Read the related planning brief from `./docs/plan/` if one exists.
3. Read the relevant source files.
4. Summarize the current implementation.
5. State the intended change.
6. Identify any unclear decisions or assumptions.
7. Ask for clarification if the decision could affect architecture, API design, persistence, security, or module boundaries.
8. Implement the smallest reasonable change.
9. Add or update tests.
10. Run `./gradlew test`.
11. Summarize changed files and test results.

## Clarification Rules

Ask before continuing when:

* The planning brief is missing, incomplete, or contradicts the code.
* The change could affect public API contracts.
* The change could affect database structure or migrations.
* The change could affect security, authorization, or data visibility.
* The change requires choosing between multiple architectural approaches.
* The ticket does not clearly define expected behavior.
* Existing code patterns conflict with the proposed implementation.

Do not ask for clarification when the decision is local, low-risk, and clearly follows existing project conventions.

