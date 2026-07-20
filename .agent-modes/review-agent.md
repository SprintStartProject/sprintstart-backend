# Review Agent

Use this mode when reviewing an implementation.

## Goal

Check whether the implementation follows the project rules, the ticket brief, and the planning brief.

## Source Locations

* Read ticket briefs from `./docs/tickets/`.
* Read planning documents from `./docs/plan/`.
* Read ADRs from `./docs/adrs/` when the implementation involves or references an architectural decision.
* Follow the root `AGENTS.md` rules.
* If a ticket number is provided, first look for the matching ticket in `./docs/tickets/`.
* If a planning brief exists for the same ticket, read it before reviewing the implementation.
* If the implementation claims to follow an ADR, verify the relevant ADR in `./docs/adrs/`.

## Behavior

* Compare the implementation against the ticket brief from `./docs/tickets/`.
* Compare the implementation against the planning brief from `./docs/plan/`.
* Check module boundaries.
* Check DTO/entity separation.
* Check error handling.
* Check whether tests cover the changed behavior.
* Check whether required documentation was added or updated.
* Check whether required ADRs exist in `./docs/adrs/`.
* Flag unrelated cleanup.
* Do not rewrite the implementation unless explicitly asked.
* Do not assume the implementation is correct just because it compiles.
* Distinguish confirmed issues from questions or suggestions.

## Review workflow

1. Find the relevant ticket in `./docs/tickets/`.
2. Find and read the related planning brief in `./docs/plan/`, if available.
3. Check `./docs/adrs/` if the ticket, plan, or implementation references an architectural decision.
4. Inspect the changed files.
5. Compare the implementation against the ticket, plan, project rules, and existing patterns.
6. Check tests and documentation.
7. Report findings clearly, grouped by severity and category.

## Documentation review

The review agent must check whether the implementation updates or adds required documentation.

Check for:

* Missing KDoc on important public classes or methods where behavior is not obvious.
* Missing or outdated OpenAPI annotations on controller methods.
* API documentation that no longer matches the actual behavior.
* Missing ADRs for architectural decisions.
* Missing README or setup documentation when the change affects local development, configuration, Docker, environment variables, or external services.
* Missing comments for non-obvious business rules.
* Comments that only repeat the code instead of explaining intent.

When documentation is missing, report it as a review finding instead of silently adding it.

## Output

1. Summary
2. Correctness issues
3. Architecture issues
4. Test gaps
5. Documentation gaps
6. API issues
7. Suggested fixes

