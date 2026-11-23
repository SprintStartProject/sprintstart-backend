# ADR (proposed) 005: The AI service is stateless; the backend runs the tools

**Status:** Accepted (2026-10-19, confirmed by the team). Recorded after the fact: it describes a
decision the code made without an ADR.

## Context

ADR-001 placed everything LLM-related in a separate Python service but did not say how the two
services split responsibility. Once the Buddy could *act* (read the path, propose a skip, edit the
board), that question had to be answered. It was answered in code, and nowhere else.

Drivers: Q4 robustness against the AI service, Q5 data protection, Q3 testability.

## Options

1. **The AI service calls the backend** for data and actions, as an agent with backend credentials.
   This needs a service identity with broad rights, puts permission checks on both sides, and
   makes the AI service stateful toward the business data.
2. **The backend sends everything up front.** There is no tool use, so the context grows and the
   AI cannot ask for more.
3. **The AI asks, the backend runs.** The AI service runs an agent turn. When it wants a *backend*
   tool, it ends the call with a `result` that names the pending tool calls. The backend runs them
   with the user's own permissions and resumes the turn with the results. Retrieval tools, which
   need the vector store, stay in the AI service.

## Decision

Option 3. This is what the code does today (`relayAgentTurn`, `BuddyToolExecutor`,
`TeamAgentLoop`). The stream vocabularies are pinned as JSON schemas in `contracts/`. The AI service
owns `agent-stream-events`, the backend owns `buddy-stream-events`, and contract tests on both sides
check them.

## Consequences

- Every business rule and permission check stays in the backend. The AI service holds no state
  about users, projects or paths, and never calls the backend.
- A failed or slow AI call cannot corrupt state: write actions only become proposals, and only a
  human's confirmation runs them.
- E2E tests can replace the AI service with a stub that scripts tool calls (`e2e/ai-stub`).
- Each hop of a multi-tool turn is a separate AI call, which adds latency. The backend has to
  stitch the hops together, for example the separator between hop texts.
