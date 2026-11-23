package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.AgentAiClient
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCitationDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.MAX_AGENT_STEPS
import com.sprintstart.sprintstartbackend.assistant.external.ResolvedBuddyCitation
import com.sprintstart.sprintstartbackend.assistant.external.completeReply
import com.sprintstart.sprintstartbackend.assistant.external.emitCitationsAndDone
import com.sprintstart.sprintstartbackend.assistant.external.relayAgentTurn
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.coroutines.flow.FlowCollector

/** Everything one hire turn needs to run the agent loop. */
internal class BuddyTurn(
    val session: BuddySession,
    val user: UserId,
    val content: String,
    val currentPage: String?,
    val history: List<BuddyAgentMessageDto>,
    val scope: BuddyTurnScope,
)

/**
 * The hire's agent loop: asks the AI to reason over the conversation, runs each backend tool it
 * hands back on the caller's behalf, and repeats until it has a final answer or [MAX_AGENT_STEPS]
 * hops have passed. Every word is relayed to the client as it arrives.
 */
internal class BuddyAgentLoop(
    private val agentAiClient: AgentAiClient,
    private val toolCalls: BuddyToolCallRunner,
    private val replyStore: BuddyReplyStore,
) {
    /**
     * Runs the loop, appending everything shown to [emittedContent], and finishes the reply on the
     * stream: the fallback if needed, then the citations and `done`. Returns the citations shown.
     */
    suspend fun FlowCollector<BuddyStreamEvent>.run(
        turn: BuddyTurn,
        emittedContent: StringBuilder,
    ): List<ResolvedBuddyCitation> {
        var messages = turn.history +
            BuddyAgentMessageDto(role = BuddyAgentMessageDto.ROLE_USER, content = turn.content)
        val citations = mutableListOf<BuddyCitationDto>()
        var answer: String? = null
        var step = 0

        while (answer == null && step < MAX_AGENT_STEPS) {
            step++
            val response = relayAgentTurn(
                agentAiClient.buddyAgentTurnStream(buddyAgentRequest(messages, step, turn.session, turn.scope)),
                emittedContent,
            )
            citations += response.citations
            if (response.final) {
                answer = response.text
            } else {
                messages = withToolResults(response, turn)
            }
        }

        completeReply(answer, emittedContent)

        return replyStore.resolveCitations(citations).also { emitCitationsAndDone(it) }
    }

    /**
     * The AI needs a backend tool run: execute each on the caller's behalf and feed the result back
     * as a `tool` message appended to the running conversation.
     */
    private suspend fun FlowCollector<BuddyStreamEvent>.withToolResults(
        response: BuddyAgentResponse,
        turn: BuddyTurn,
    ): List<BuddyAgentMessageDto> {
        val next = response.messages.toMutableList()
        for (call in response.pendingToolCalls) {
            next.add(
                BuddyAgentMessageDto(
                    role = BuddyAgentMessageDto.ROLE_TOOL,
                    content = toolCalls.run(this, call, turn.user, turn.currentPage),
                    toolCallId = call.id,
                ),
            )
        }
        return next
    }
}
