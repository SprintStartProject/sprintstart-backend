package com.sprintstart.sprintstartbackend.shared.ai

import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Serializable
private data class ProgressEvent(
    val type: String,
    val content: String? = null,
)

class AiServiceCallsTest {
    private val ai = AiMockServer()
    private val calls = AiServiceCalls(ai.webClient, ai.applicationConfig)

    @AfterEach
    fun tearDown() = ai.close()

    @Test
    fun `a refused call says what it was doing and carries the status and body`() = runTest {
        ai.rejectNext(status = 500, body = "model overloaded")

        val error = assertFailsWith<AiServiceException> {
            calls.post<String, String>("/api/v1/anything", "body", "assemble diagram")
        }

        assertEquals(500, error.statusCode)
        assertEquals("model overloaded", error.body)
        assertEquals("Failed to assemble diagram (HTTP 500): model overloaded", error.message)
    }

    @Test
    fun `a stream passes the events through up to the end marker and ignores other lines`() = runTest {
        ai.streamNext(
            ": keep-alive",
            """data: {"type":"token","content":"Hel"}""",
            "",
            """data: {"type":"token","content":"lo"}""",
            "data: [DONE]",
            """data: {"type":"token","content":"after the end"}""",
        )

        val events = calls.stream<String, ProgressEvent>("/api/v1/stream", "body", "AI progress").toList()

        assertEquals(listOf(ProgressEvent("token", "Hel"), ProgressEvent("token", "lo")), events)
    }

    @Test
    fun `a malformed chunk is skipped and the stream goes on`() = runTest {
        ai.streamNext(
            """data: {"type":"token","content":"one"}""",
            "data: this is not json",
            """data: {"type":"token","content":"two"}""",
        )

        val events = calls.stream<String, ProgressEvent>("/api/v1/stream", "body", "AI progress").toList()

        assertEquals(listOf("one", "two"), events.map { it.content })
    }

    @Test
    fun `a stream the AI service refuses fails with its status and body`() = runTest {
        ai.rejectNext(status = 422, body = "bad request")

        val error = assertFailsWith<WebClientException> {
            calls.stream<String, ProgressEvent>("/api/v1/stream", "body", "AI progress").toList()
        }

        assertEquals(422, error.statusCode)
        assertEquals("bad request", error.body)
    }
}
