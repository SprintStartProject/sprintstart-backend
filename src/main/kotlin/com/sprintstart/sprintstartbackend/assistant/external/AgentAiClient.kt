package com.sprintstart.sprintstartbackend.assistant.external

import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCompactRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCompactResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceCalls
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceException
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import org.springframework.stereotype.Component

/** The AI service's agent endpoints, shared by the hire's buddy and team mode. */
@Component
class AgentAiClient(
    private val calls: AiServiceCalls,
) {
    /**
     * Runs one turn of the tool-using buddy agent, streaming it as it happens.
     *
     * Stateless: the backend carries [BuddyAgentRequest.messages] between turns and executes the
     * tools only it can run. The AI emits `reasoning` and `token` fragments and a `tool_use` before
     * each search it runs itself, then one terminal `result` — either the final answer
     * (`final=true`) or the pending backend-tool calls plus the running message list to carry back
     * with each tool result appended.
     *
     * A malformed chunk is logged and skipped. A non-2xx response is wrapped in an
     * [AiServiceException] carrying the upstream status/body; so is a failure after the response
     * started, which the AI reports as an `error` event, and a stream that ends without a `result`
     * (see `relayAgentTurn`).
     */
    fun buddyAgentTurnStream(request: BuddyAgentRequest): Flow<BuddyAgentStreamEvent> =
        calls
            .stream<BuddyAgentRequest, BuddyAgentStreamEvent>(
                "/api/v1/onboarding/buddy/agent/stream",
                request,
                "buddy agent",
            ).catch { e ->
                if (e is WebClientException) {
                    val msg = "Failed to run buddy agent turn (HTTP ${e.statusCode}): ${e.body}"
                    throw AiServiceException(e.statusCode, e.body, msg)
                }
                throw e
            }

    /**
     * Folds older buddy turns into the mentor's durable memory note.
     *
     * Nothing waits on this: call it after a turn, never ahead of the answer.
     *
     * A non-2xx (including the AI service's 503 for an unavailable model) is wrapped in an
     * [AiServiceException]. That is not a degraded success — the caller must leave its cursor
     * where it is, because advancing past messages nothing summarized would drop them from both the
     * prompt and the memory standing in for it.
     */
    suspend fun compactBuddyMemory(request: BuddyCompactRequest): BuddyCompactResponse =
        calls.post("/api/v1/onboarding/buddy/compact", request, "compact buddy memory")

    /**
     * Opens a buddy visit: the greeting arrives as it is written.
     *
     * The greeting streams as `token` chunks; the memory note and any opening action arrive on the
     * terminal `done`. A malformed chunk is skipped rather than killing the stream. The AI service
     * degrades to a plain welcome on its own failures, so an `error` chunk is not expected here — a
     * transport failure still surfaces to the caller.
     */
    fun streamBuddyOpen(request: BuddyOpenRequest): Flow<BuddyOpenStreamEvent> =
        calls.stream("/api/v1/onboarding/buddy/open/stream", request, "buddy-open")
}
