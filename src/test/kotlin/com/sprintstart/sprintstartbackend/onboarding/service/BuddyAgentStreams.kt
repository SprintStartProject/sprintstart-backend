package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentStreamEvent
import io.mockk.MockKStubScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * What the AI service streams for one call that ends in this outcome.
 *
 * A final answer arrives as one `token` carrying its text, then the terminal `result`. A call that
 * only hands back pending backend tools says nothing before its `result`, as a hop that went straight
 * to a tool call would not.
 */
internal fun BuddyAgentResponse.asStream(): Flow<BuddyAgentStreamEvent> =
    flow {
        if (final && text.isNotEmpty()) {
            emit(token(text))
        }
        emit(result(this@asStream))
    }

internal fun token(content: String) = BuddyAgentStreamEvent(type = BuddyAgentStreamEvent.TOKEN, content = content)

internal fun reasoning(content: String) =
    BuddyAgentStreamEvent(type = BuddyAgentStreamEvent.REASONING, content = content)

internal fun toolUse(name: String) = BuddyAgentStreamEvent(type = BuddyAgentStreamEvent.TOOL_USE, name = name)

internal fun result(response: BuddyAgentResponse) =
    BuddyAgentStreamEvent(
        type = BuddyAgentStreamEvent.RESULT,
        final = response.final,
        text = response.text,
        messages = response.messages,
        pendingToolCalls = response.pendingToolCalls,
        citations = response.citations,
        reasoning = response.reasoning,
    )

internal fun streamOf(vararg events: BuddyAgentStreamEvent): Flow<BuddyAgentStreamEvent> = flowOf(*events)

internal infix fun <B> MockKStubScope<Flow<BuddyAgentStreamEvent>, B>.returnsStream(response: BuddyAgentResponse) =
    returns(response.asStream())

internal infix fun <B> MockKStubScope<Flow<BuddyAgentStreamEvent>, B>.returnsStreams(
    responses: List<BuddyAgentResponse>,
) = returnsMany(responses.map { it.asStream() })
