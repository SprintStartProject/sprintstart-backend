# AGENTS.md

## Project overview

This is a Kotlin/Spring Boot backend for an AI-assisted onboarding tool.

The project uses Spring Modulith, PostgreSQL, JPA/Hibernate, Gradle Kotlin DSL, and Docker Compose. The backend is structured into modules that should stay loosely coupled.

## Tech stack

* Kotlin
* Spring Boot
* Spring Modulith
* Spring Data JPA / Hibernate
* PostgreSQL
* Gradle Kotlin DSL
* JUnit 5
* MockK / Mockito Kotlin where already used
* MockMvc for controller tests
* Docker Compose for local infrastructure

## Agent modes

This repository supports different agent modes for different types of work.

The general rules in this file always apply. Specialized mode files add extra behavior for a specific task type.

Available modes:

* Planning mode: `.agent-modes/planning-agent.md`
* Issue mode: `.agent-modes/issue-agent.md`
* Development mode: `.agent-modes/development-agent.md`
* ADR mode: `.agent-modes/adr-agent.md`
* Review mode: `.agent-modes/review-agent.md`

When working in a specific mode, follow both this `AGENTS.md` file and the referenced mode file.
If there is a conflict, ask for clarification instead of silently choosing one.

## Agent mode selection

The user may select an agent mode using:

* `[agent:planning]`
* `[agent:issue]`
* `[agent:development]`
* `[agent:adr]`
* `[agent:review]`
* `[agent:default]`

## Commands

Run tests:

```bash
./gradlew test
```

Run full checks:

```bash
./gradlew clean check
```

Start local services:

```bash
docker compose up -d
```

Do not use Maven commands in this project. This project uses Gradle Kotlin DSL.

## Communication during work

Narrate what you are doing as you go. Do not work silently and present a result at the end.

* Before reading a file, say which file you are opening and why.
* Before making a change, briefly state what you intend to do and why.
* After reading a file, summarize the relevant parts before moving on.
* If you discover something unexpected — a bug, a design inconsistency, a naming mismatch — call it out immediately rather than silently working around it.
* If you are about to make a decision that has trade-offs, state the options and explain which one you are choosing and why.
* If a task turns out to be larger than expected, say so before diving in and confirm the approach.
* If you are unsure how something should behave, ask rather than assume.
* Keep commentary concise. You do not need to narrate every line, but the overall reasoning should always be visible.

## Architecture rules

* Keep modules loosely coupled.
* Communicate between modules through explicit API interfaces or Spring Modulith events.
* Do not access another module's repositories directly.
* Keep JPA entities separate from request/response DTOs.
* Do not expose JPA entities through API responses.
* Put business logic in services, not controllers.
* Use mapper functions/classes to convert entities to response DTOs.
* Repository interfaces belong to the module that owns the entity.
* Keep module-facing APIs small and explicit.
* Prefer events for cross-module reactions when the module does not need an immediate return value.

## Kotlin style

* Prefer constructor injection.
* Prefer readable block bodies over expression bodies for non-trivial functions.
* Use explicit return types for public functions.
* Use descriptive test names with Kotlin backticks.
* Avoid unnecessary comments that only repeat the code.
* Add KDoc only where it helps explain public behavior.
* Prefer clarity over cleverness.
* Keep functions small and focused.

## Documentation comments

* Write documentation comments for important public classes and methods when the behavior is not immediately obvious.
* Start each comment with a single-sentence summary that describes what the class or method does.
* Follow the summary with a longer paragraph that explains the relevant implementation behavior, assumptions, side effects, ownership rules, or design decisions.
* Keep the explanation useful. Do not describe every line of code.
* Include KDoc tags such as `@param`, `@return`, and `@throws` where they add clarity.
* Use `@throws` when the method can intentionally fail with a known exception, such as `ResponseStatusException`.
* Add OpenAPI documentation for every controller method that is exposed as an API endpoint.
* Place OpenAPI annotations below the KDoc comment and directly above the method annotation or method declaration.
* Use OpenAPI annotations such as `@Operation`, `@ApiResponses`, `@ApiResponse`, `@Content`, and `@Schema` to describe the endpoint behavior, expected status codes, request body, and response body.
* Keep OpenAPI descriptions aligned with the actual controller behavior. Do not document responses, errors, or permissions that the method does not implement.
* Do not add comments that only repeat the code. Prefer comments that explain intent, behavior, and API usage.



## Testing expectations

* Add or update tests when behavior changes.
* Service tests should cover business rules and repository interactions.
* Controller tests should verify HTTP status codes and JSON response bodies.
* Test relevant error cases, especially 400 and 404 responses.
* Use descriptive test names.
* Prefer focused tests over large tests that check too many things at once.
* Run `./gradlew test` after changing production code.
* Run `./gradlew clean check` when making larger changes.
* After running tests, report the results explicitly — how many passed, whether anything failed, and what you did about it.

## Error handling

* Use `ResponseStatusException` for simple HTTP errors.
* Return 404 when an entity does not exist.
* Return 400 for invalid request data or invalid state.
* Avoid plain `IllegalArgumentException` for user-facing errors unless it is mapped globally.
* Do not let `NoSuchElementException` from `.orElseThrow()` leak into the API.
* Prefer clear error messages that include the relevant entity type and ID where useful.

## Persistence conventions

* Use UUIDs for IDs.
* Keep relationships explicit and understandable.
* Use `mappedBy` on the non-owning side of bidirectional relationships.
* Use cascading only when the child lifecycle truly belongs to the parent.
* Do not expose JPA entities through API responses.
* Do not put API-specific formatting logic into JPA entities.
* Keep database ownership aligned with module ownership.

## API conventions

* Use request DTOs for incoming request bodies.
* Use response DTOs for outgoing API responses.
* Do not return JPA entities from controllers.
* Use path variables for resource identity.
* Use request bodies for data needed to create or update resources.
* Prefer RESTful resource paths.
* Keep controllers thin.
* Put validation and business decisions in services or validators, not in controllers.

## Mapper conventions

* Use mappers to convert entities to response DTOs.
* Simple derived fields, such as counts, are acceptable in mappers.
* Do not put complex business decisions into mappers.
* Keep mapping code readable and predictable.

## Spring Modulith conventions

* Prefer explicit module APIs for synchronous cross-module access.
* Prefer Spring events for cross-module updates or reactions.
* Use `ApplicationEventPublisher` to publish domain/application events.
* Use `@ApplicationModuleListener` for module event listeners when appropriate.
* Events should contain enough data for the listener to react without reaching into another module's internals.
* Do not expose internal entities across module boundaries.

## Validation conventions

* Use Jakarta Validation annotations on request DTOs where appropriate.
* Use service-level validation for business rules that cannot be expressed with simple annotations.
* Keep validation errors user-facing and predictable.
* For PATCH requests, nullable fields are acceptable when omitted values should remain unchanged.

## Do not

* Do not add new production dependencies without explaining why and confirming it with me.
* Do not change public API contracts unless asked.
* Do not rename packages unnecessarily.
* Do not move large parts of the project without need.
* Do not ignore failing tests.
* Do not make unrelated cleanup changes.
* Do not replace the existing architecture with a different pattern.
* Do not mix DTOs and JPA entities.
* Do not access another module's database repositories directly.
* Do not remove tests unless they are obsolete and the reason is clear.
* Do not work silently through a multi-step task and only communicate at the end.

## Before finishing a task

* Summarize what changed.
* Mention which tests were run.
* If tests were not run, explain why.
* Mention any follow-up work or limitations.
