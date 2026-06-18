package com.sprintstart.sprintstartbackend.shared.web

import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StreamExecutionTest {
    private val mockWebServer = MockWebServer()
    private val httpClient = HttpClient.newBuilder().build()
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val webClient = WebClient(httpClient, jsonParser)

    @BeforeEach
    fun start() = mockWebServer.start()

    @AfterEach
    fun stop() = mockWebServer.shutdown()

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `emits typed chunks and stops at termination marker`() = runTest {
        mockWebServer.enqueue(
            sseResponse(
                """
data: {"type":"token","content":"Hello"}
            
data: {"type":"token","content":" world"}
            
data: [DONE]
            
                """.trimIndent(),
            ),
        )

        val chunks = webClient
            .post()
            .uri(
                mockWebServer
                    .url("/stream")
                    .toUri(),
            ).stream()
            .perform<AiStreamMessage>()
            .toList()

        assertEquals(2, chunks.size)
        assertEquals("Hello", chunks[0].content)
        assertEquals(" world", chunks[1].content)
    }

    @Test
    fun `custom termination marker stops stream correctly`() = runTest {
        mockWebServer.enqueue(
            sseResponse(
                """
                data: {"type":"token","content":"Hello"}
                
                data: STREAM_END
                
                data: {"type":"token","content":"should not appear"}
                
                """.trimIndent(),
            ),
        )

        val chunks = webClient
            .post()
            .uri(
                mockWebServer
                    .url("/stream")
                    .toUri(),
            ).stream()
            .perform<AiStreamMessage>(terminationMarkers = setOf("STREAM_END"))
            .toList()

        assertEquals(1, chunks.size)
        assertEquals("Hello", chunks[0].content)
    }

    // ── Header injection ──────────────────────────────────────────────────────

    @Test
    fun `Accept text-event-stream header is injected automatically`() = runTest {
        mockWebServer.enqueue(sseResponse("data: [DONE]\n"))

        webClient
            .post()
            .uri(
                mockWebServer
                    .url("/stream")
                    .toUri(),
            ).stream()
            .perform<AiStreamMessage>()
            .toList()

        assertEquals("text/event-stream", mockWebServer.takeRequest().getHeader("Accept"))
    }

    @Test
    fun `caller-provided Accept header is not overwritten`() = runTest {
        mockWebServer.enqueue(sseResponse("data: [DONE]\n"))

        webClient
            .post()
            .uri(
                mockWebServer
                    .url("/stream")
                    .toUri(),
            ).header("Accept", "application/x-ndjson")
            .stream()
            .perform<AiStreamMessage>()
            .toList()

        assertEquals("application/x-ndjson", mockWebServer.takeRequest().getHeader("Accept"))
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    fun `malformed chunk is skipped and stream continues by default`() = runTest {
        mockWebServer.enqueue(
            sseResponse(
                """
                data: {"type":"token","content":"before"}
                
                data: this is not valid json {{{
                
                data: {"type":"token","content":"after"}
                
                data: [DONE]
                
                """.trimIndent(),
            ),
        )

        val chunks = webClient
            .post()
            .uri(
                mockWebServer
                    .url("/stream")
                    .toUri(),
            ).stream()
            .perform<AiStreamMessage>()
            .toList()

        // Malformed chunk skipped, valid chunks either side still emitted
        assertEquals(2, chunks.size)
        assertEquals("before", chunks[0].content)
        assertEquals("after", chunks[1].content)
    }

    @Test
    fun `onChunkError returning false cancels stream`() = runTest {
        mockWebServer.enqueue(
            sseResponse(
                """
                data: {"type":"token","content":"before"}
                
                data: this is not valid json {{{
                
                data: {"type":"token","content":"after"}
                
                data: [DONE]
                
                """.trimIndent(),
            ),
        )

        val chunks = webClient
            .post()
            .uri(
                mockWebServer
                    .url("/stream")
                    .toUri(),
            ).stream()
            .perform<AiStreamMessage>(
                onChunkError = { _, _ -> false }, // cancel on error
            ).toList()

        // Stream stopped after the malformed chunk
        assertEquals(1, chunks.size)
        assertEquals("before", chunks[0].content)
    }

    @Test
    fun `lines not starting with data are silently skipped`() = runTest {
        mockWebServer.enqueue(
            sseResponse(
                """
                : this is an SSE comment
                
                data: {"type":"token","content":"Hello"}
                
                : another comment
                
                data: [DONE]
                
                """.trimIndent(),
            ),
        )

        val chunks = webClient
            .post()
            .uri(
                mockWebServer
                    .url("/stream")
                    .toUri(),
            ).stream()
            .perform<AiStreamMessage>()
            .toList()

        assertEquals(1, chunks.size)
        assertEquals("Hello", chunks[0].content)
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `non-2xx at stream open throws WebClientException before any chunks`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val ex = assertFailsWith<WebClientException> {
            webClient
                .post()
                .uri(
                    mockWebServer
                        .url("/stream")
                        .toUri(),
                ).stream()
                .perform<AiStreamMessage>()
                .toList()
        }

        assertEquals(401, ex.statusCode)
    }

    @Test
    fun `empty stream emits no chunks`() = runTest {
        mockWebServer.enqueue(sseResponse("data: [DONE]\n"))

        val chunks = webClient
            .post()
            .uri(
                mockWebServer
                    .url("/stream")
                    .toUri(),
            ).stream()
            .perform<AiStreamMessage>()
            .toList()

        assertTrue(chunks.isEmpty())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sseResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody(body)
}
