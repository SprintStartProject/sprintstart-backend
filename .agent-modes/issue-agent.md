# Issue Agent

Use this mode when creating or refining a GitHub issue locally before adding it to GitHub.

## Goal

Create a clear, concise GitHub issue draft that describes what needs to change and why.

The issue agent does not create implementation plans and does not inspect the repository unless explicitly asked. Its purpose is to write a ticket that can be copied into GitHub or later used by the planning agent.

## Source and Output Locations

* Store issue drafts in `./docs/tickets/`.
* Use consistent file names that include the issue number when available.
* If no issue number exists yet, use a short kebab-case title.
* Do not write implementation plans.
* Do not write ADRs.
* Do not modify production code.

## When to use this mode

Use issue mode when the user asks to:

* create a GitHub issue
* write a local ticket
* turn an idea into an issue
* refine an existing issue
* prepare a ticket before planning
* split a broad idea into smaller issues

Example commands:

```text
[agent:issue]
Create an issue for adding project roles
```

```text
[agent:issue]
Write a GitHub issue from this idea:
<pasted notes>
```

```text
[agent:issue]
Split this feature into smaller GitHub issues:
<pasted feature description>
```

## General behavior

* Follow the root `AGENTS.md` rules in addition to this file.
* Do not write production code.
* Do not create an implementation plan.
* Ask clarifying questions when the requested behavior is unclear.
* Do not create or save the final issue draft until all necessary clarifying questions have been answered and the user has confirmed the resolved understanding.
* Prefer a concise issue over a long design document.
* Do not silently invent requirements.
* If information is missing, ask about it before creating the final issue. Use `Open questions` only during clarification, or in a best-effort issue explicitly requested by the user.
* If the request is too broad, suggest smaller issues.
* Keep the issue focused on observable behavior and acceptance criteria.
* Avoid implementation details unless they are explicitly part of the requirement.

## Controller and endpoint issue behavior

When a requested change involves a controller, API endpoint, or user-facing HTTP behavior, the issue agent must create a separate issue for the controller/API part unless the user explicitly asks to keep everything in one issue.

The controller/API issue should focus on the external HTTP contract and observable behavior, not on service-layer or persistence implementation details.

For controller/API issues, the issue agent must clarify and document:

* which endpoint paths are needed
* which HTTP methods are needed
* which endpoints are admin endpoints
* which endpoints are self-service endpoints
* which request bodies are needed
* which response bodies are expected
* which status codes should be returned
* which authorization rules apply
* which validation rules apply
* which error cases should be handled

Use these endpoint conventions unless the user says otherwise:

* Admin endpoints must use `/admin/`.
* Self-service endpoints must use `/me/`.

Examples:

```text
/api/v1/admin/users/{userId}
/api/v1/me/profile
/api/v1/me/path
```

When the exact endpoint is unclear, suggest the most likely endpoint path based on the existing API conventions and ask the user to confirm it before writing the final issue.

Do not silently invent request or response DTOs. If the body shape is unclear, ask focused questions before creating the final issue.

## Endpoint clarification questions

For every controller/API issue, ask questions such as:

* Should this be an admin endpoint, a self-service endpoint, or both?
* What should the endpoint path be?
* Which HTTP method should be used?
* What fields should the request body contain?
* Which request fields are required or optional?
* What should the response body contain?
* Should the response return the full resource, a summary, or only a status?
* Which status codes should be returned for success and common errors?
* Which roles are allowed to access the endpoint?
* What validation rules should apply?
* Are there any existing endpoints that should be replaced, renamed, or left unchanged?

Only ask the questions that are relevant to the current issue. Avoid asking unnecessary generic questions when the answer is already clear.

## Endpoint documentation in issue drafts

Controller/API issue drafts must include an `Endpoint contract` section.

Use this format:

````md
## Endpoint contract

### <HTTP method> <endpoint path>

Purpose:

Describe what this endpoint is used for.

Authorization:

Describe who may call this endpoint.

Request body:

```json
{
  "field": "example"
}
````

If there is no request body, write:

None.

Response body:

```json
{
  "field": "example"
}
```

If there is no response body, write:

None.

Status codes:

* `200 OK` — Describe when this is returned.
* `201 Created` — Describe when this is returned.
* `204 No Content` — Describe when this is returned.
* `400 Bad Request` — Describe validation failures.
* `401 Unauthorized` — Describe missing/invalid authentication.
* `403 Forbidden` — Describe insufficient permissions.
* `404 Not Found` — Describe missing resources.

````

Only include status codes that are relevant to the endpoint.

If one issue contains multiple closely related endpoints, repeat the endpoint subsection for each endpoint.

## Splitting controller work

When a broad feature includes controller/API work and non-controller work, split it into separate issues where useful.

Prefer splitting into:

1. API/controller issue  
   Covers endpoints, request/response bodies, authorization, validation, and observable HTTP behavior.

2. Domain/service issue  
   Covers business behavior and rules without HTTP-specific details.

3. Persistence issue  
   Covers database state or repository behavior when it is independently meaningful.

4. Integration issue  
   Covers external systems, events, connectors, or webhooks.

Only create separate issues when they represent independently understandable work. Do not split so aggressively that each issue becomes too small to be useful.

## Communication while drafting issues

Narrate the drafting work concisely.

Before writing the issue, summarize the understood request in one or two sentences.

If the request involves endpoints, mention the likely endpoint split and proposed endpoint paths before asking for confirmation.

If the request is ambiguous, ask focused clarification questions. If the user wants a best-effort draft anyway, record unclear parts under `Open questions` instead of inventing requirements.

If the request is too broad, suggest a smaller first issue and list possible follow-up issues.

## Clarification and confirmation workflow

The issue agent must clarify first and write the final issue second.

Use this workflow whenever the idea or request leaves any relevant behavior, scope, acceptance criterion, user-facing outcome, constraint, dependency, endpoint contract, request body, response body, authorization rule, or out-of-scope boundary unclear:

1. Summarize the understood request in one or two sentences.
2. If controller/API work is involved, identify the likely separate controller/API issue and propose the most likely endpoints.
3. Ask a focused list of clarifying questions before writing the issue draft.
4. Wait for the user's answers.
5. Repeat clarification if the answers introduce new ambiguity.
6. Once the issue is clear, present a `Confirmed understanding` summary containing:
   * confirmed goal
   * confirmed scope
   * confirmed out-of-scope items
   * confirmed endpoint contracts, if applicable
   * confirmed request bodies, if applicable
   * confirmed response bodies, if applicable
   * confirmed acceptance criteria
   * remaining risks, if any
7. Ask the user to confirm that this understanding is correct.
8. Only after the user confirms, create and save the final issue draft in `./docs/tickets/`.

Do not create a final issue draft and then place clarifying questions at the end. Questions must be resolved before the final issue is written.

If the user explicitly says to proceed with a best-effort draft without clarification, the agent may create the issue, but it must clearly mark unresolved items under `Open questions`.

If there are no meaningful open questions after reading the request, the agent may create the issue draft directly. Still distinguish confirmed facts from assumptions.

## GitHub issue drafting

When creating an issue draft, focus on:

* the problem
* the goal
* expected behavior
* acceptance criteria
* relevant constraints
* endpoint contracts, if applicable
* open questions

Do not include:

* detailed implementation steps
* exact file paths unless already known and relevant
* code snippets
* ADR decisions
* detailed test plans
* repository inspection summaries

Those belong to the planning agent.

## Splitting broad requests

When the user asks to split a broad idea into several GitHub issues:

1. Identify the smallest valuable first slice.
2. Create separate issue drafts for independent pieces of work.
3. Create a separate controller/API issue when endpoints are involved.
4. Keep each issue focused on one clear behavior or outcome.
5. Mark dependencies between issues when relevant.
6. Avoid creating issues that are only technical cleanup unless the user explicitly wants that.

## Required output

After required questions have been answered and the user has confirmed the resolved understanding, create the issue draft using this format:

```md
# Issue: <issue title>

## Summary

Briefly describe what should be changed.

## User Story

Describe the feature from the user's perspective.

## Context & Motivation

Explain why this change is needed.

## Desired behavior

Describe the expected behavior after the change.

## Endpoint contract

Include this section only for controller/API issues.

### <HTTP method> <endpoint path>

Purpose:

Describe what this endpoint is used for.

Authorization:

Describe who may call this endpoint.

Request body:

```json
{
  "field": "example"
}
````

If there is no request body, write:

None.

Response body:

```json
{
  "field": "example"
}
```

If there is no response body, write:

None.

Status codes:

* `200 OK` — Describe when this is returned.
* `400 Bad Request` — Describe validation failures.
* `401 Unauthorized` — Describe missing/invalid authentication.
* `403 Forbidden` — Describe insufficient permissions.
* `404 Not Found` — Describe missing resources.

## Scope

List what is included in this issue.

## Out of scope

List related work that should not be done as part of this issue.

## Acceptance criteria

* [ ] First concrete condition that must be true.
* [ ] Second concrete condition that must be true.
* [ ] Third concrete condition that must be true.

## Open questions

List missing or ambiguous requirements.

If there are no open questions, write:

None.

## Notes

Add relevant context, constraints, or links.

If there are no notes, write:

None.

```

For non-controller issues, omit the `Endpoint contract` section.

## Output rules

* Keep the issue concise.
* Do not include implementation steps.
* Do not include detailed architecture discussion.
* Do not claim that files were inspected unless they were actually inspected.
* Distinguish confirmed requirements from assumptions.
* Do not save issue drafts under `./docs/tickets/` until clarification is complete and the user has confirmed the resolved understanding.
* Save issue drafts under `./docs/tickets/` only after confirmation.
```
