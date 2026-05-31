package com.sprintstart.sprintstartbackend.chat

import com.sprintstart.sprintstartbackend.chat.models.requests.AiPromptRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiWebRequestClientTest {
    private val mockWebServer = MockWebServer()
    private val client = AiWebRequestClient()

    @BeforeEach
    fun setUp() = mockWebServer.start()

    @AfterEach
    fun tearDown() = mockWebServer.shutdown()

    /**
     * Builds a MockResponse that streams SSE lines.
     * Each string in [lines] becomes a "data: <line>\n\n" SSE event.
     */
    private fun sseResponse(vararg lines: String): MockResponse {
        val body = lines.joinToString("") { "data: $it\n\n" }
        return MockResponse()
            .addHeader("Content-Type", "text/event-stream")
            .setBody(Buffer().writeUtf8(body))
    }

    private val sampleRequest = AiPromptRequest(prompt = "Hello", context = emptyList())

    @Nested
    inner class TokenEmission {
        @Test
        fun `emits token type messages as raw json`() = runTest {
            mockWebServer.enqueue(
                sseResponse(
                    """{"type":"token","content":"Hello"}""",
                    """{"type":"token","content":" world"}""",
                    """{"type":"done"}""",
                ),
            )

            val result = client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = sampleRequest,
                ).toList()

            assertEquals(2, result.size)
            assertTrue(result[0].contains("Hello"))
            assertTrue(result[1].contains("world"))
        }

        @Test
        fun `does not emit done type message`() = runTest {
            mockWebServer.enqueue(
                sseResponse(
                    """{"type":"token","content":"Hello"}""",
                    """{"type":"done"}""",
                ),
            )

            val result = client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = sampleRequest,
                ).toList()

            assertEquals(1, result.size)
        }

        @Test
        fun `does not emit error type message`() = runTest {
            mockWebServer.enqueue(
                sseResponse(
                    """{"type":"error","content":"Something went wrong"}""",
                ),
            )

            val result = client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = sampleRequest,
                ).toList()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `skips lines that do not start with data prefix`() = runTest {
            val body = "event: ping\n\n" +
                "data: {\"type\":\"token\",\"content\":\"Hello\"}\n\n" +
                "data: {\"type\":\"done\"}\n\n"

            mockWebServer.enqueue(
                MockResponse()
                    .addHeader("Content-Type", "text/event-stream")
                    .setBody(Buffer().writeUtf8(body)),
            )

            val result = client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = sampleRequest,
                ).toList()

            assertEquals(1, result.size)
        }
    }

    @Nested
    inner class Termination {
        @Test
        fun `stops emitting after done type even if more tokens follow`() = runTest {
            mockWebServer.enqueue(
                sseResponse(
                    """{"type":"token","content":"Hello"}""",
                    """{"type":"done"}""",
                    """{"type":"token","content":"Should not appear"}""",
                ),
            )

            val result = client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = sampleRequest,
                ).toList()

            assertEquals(1, result.size)
            assertTrue(result[0].contains("Hello"))
        }

        @Test
        fun `stops emitting on empty type`() = runTest {
            mockWebServer.enqueue(
                sseResponse(
                    """{"type":"token","content":"Hello"}""",
                    """{"type":""}""",
                    """{"type":"token","content":"Should not appear"}""",
                ),
            )

            val result = client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = sampleRequest,
                ).toList()

            assertEquals(1, result.size)
        }

        @Test
        fun `returns empty flow when stream has no token messages`() = runTest {
            mockWebServer.enqueue(sseResponse("""{"type":"done"}"""))

            val result = client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = sampleRequest,
                ).toList()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class RequestShape {
        @Test
        fun `sends POST with correct headers`() = runTest {
            mockWebServer.enqueue(sseResponse("""{"type":"done"}"""))

            client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = sampleRequest,
                ).toList()

            val recorded = mockWebServer.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("application/json", recorded.getHeader("Content-Type"))
            assertEquals("text/event-stream", recorded.getHeader("Accept"))
        }

        @Test
        fun `serializes body as JSON in request`() = runTest {
            mockWebServer.enqueue(sseResponse("""{"type":"done"}"""))

            client
                .streamPost<AiPromptRequest>(
                    uri = mockWebServer.url("/prompt").toUri(),
                    body = AiPromptRequest(prompt = "What is the goal?", context = emptyList()),
                ).toList()

            val recorded = mockWebServer.takeRequest()
            assertTrue(recorded.body.readUtf8().contains("What is the goal?"))
        }
    }
}
