package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The AI service's agent stream, decoded the way the client decodes it: with unknown keys ignored,
 * from payloads shaped like the ones `POST /onboarding/buddy/agent/stream` writes.
 */
class BuddyAgentStreamEventTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(payload: String): BuddyAgentStreamEvent = json.decodeFromString(payload)

    @Test
    fun `decodes a reasoning or token fragment`() {
        val reasoning = decode("""{"type": "reasoning", "content": "Let me think"}""")
        val token = decode("""{"type": "token", "content": " about it"}""")

        assertThat(reasoning.type).isEqualTo(BuddyAgentStreamEvent.REASONING)
        assertThat(reasoning.content).isEqualTo("Let me think")
        assertThat(token.type).isEqualTo(BuddyAgentStreamEvent.TOKEN)
        assertThat(token.content).isEqualTo(" about it")
    }

    @Test
    fun `decodes a tool_use and ignores the arguments it also carries`() {
        val event = decode("""{"type": "tool_use", "name": "search_docs", "arguments": {"query": "deploy"}}""")

        assertThat(event.type).isEqualTo(BuddyAgentStreamEvent.TOOL_USE)
        assertThat(event.name).isEqualTo("search_docs")
    }

    @Test
    fun `decodes a final result into the response the loop acts on`() {
        val event = decode(
            """
            {"type": "result", "final": true, "text": "Here you go.",
             "messages": [{"role": "assistant", "content": "Here you go.", "tool_calls": [], "tool_call_id": null}],
             "pending_tool_calls": [],
             "citations": [{"artifact_id": "a1", "start_line": 12, "start_page": null}],
             "reasoning": ["I searched twice."]}
            """.trimIndent(),
        )

        val response = event.toResponse()

        assertThat(response.final).isTrue()
        assertThat(response.text).isEqualTo("Here you go.")
        assertThat(response.messages.single().role).isEqualTo("assistant")
        assertThat(response.citations.single().artifactId).isEqualTo("a1")
        assertThat(response.citations.single().startLine).isEqualTo(12)
        assertThat(response.reasoning).containsExactly("I searched twice.")
    }

    @Test
    fun `decodes a result that hands back a backend tool to run`() {
        val event = decode(
            """
            {"type": "result", "final": false, "text": "",
             "messages": [{"role": "assistant", "content": "",
                           "tool_calls": [{"id": "call_0", "name": "get_my_metrics", "arguments": {}}]}],
             "pending_tool_calls": [{"id": "call_0", "name": "get_my_metrics", "arguments": {}}],
             "citations": [], "reasoning": []}
            """.trimIndent(),
        )

        val response = event.toResponse()

        assertThat(response.final).isFalse()
        assertThat(response.pendingToolCalls.map { it.name }).containsExactly("get_my_metrics")
        val carriedBack = response.messages
            .single()
            .toolCalls
            .single()
        assertThat(carriedBack.id).isEqualTo("call_0")
    }

    @Test
    fun `decodes the error that ends a failed turn`() {
        val event = decode("""{"type": "error", "message": "An unexpected error occurred"}""")

        assertThat(event.type).isEqualTo(BuddyAgentStreamEvent.ERROR)
        assertThat(event.message).isEqualTo("An unexpected error occurred")
    }
}
