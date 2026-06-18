package com.sprintstart.sprintstartbackend.shared.web

import com.sprintstart.sprintstartbackend.chat.models.responses.AiGenerateChatTitleResponse
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

class SyncExecutionTest {
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
    fun `perform deserializes 200 response into target type`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title": "Generated title"}"""),
        )

        val response = webClient
            .get()
            .uri(mockWebServer.url("/test").toUri())
            .sync()
            .perform<AiGenerateChatTitleResponse>()

        assertEquals("Generated title", response.title)
    }

    @Test
    fun `performRaw returns response body as plain string`() = runTest {
        val rawBody = """{"title": "Generated title"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(rawBody),
        )

        val response = webClient
            .get()
            .uri(mockWebServer.url("/test").toUri())
            .sync()
            .performRaw()

        assertEquals(rawBody, response)
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `perform throws WebClientException on 4xx response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        val ex = assertFailsWith<WebClientException> {
            webClient
                .get()
                .uri(mockWebServer.url("/test").toUri())
                .sync()
                .perform<AiGenerateChatTitleResponse>()
        }

        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `perform throws WebClientException on 5xx response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val ex = assertFailsWith<WebClientException> {
            webClient
                .get()
                .uri(mockWebServer.url("/test").toUri())
                .sync()
                .perform<AiGenerateChatTitleResponse>()
        }

        assertEquals(500, ex.statusCode)
    }

    @Test
    fun `performRaw throws WebClientException on non-2xx`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val ex = assertFailsWith<WebClientException> {
            webClient
                .get()
                .uri(mockWebServer.url("/test").toUri())
                .sync()
                .performRaw()
        }

        assertEquals(404, ex.statusCode)
    }

    @Test
    fun `WebClientException carries status code and message`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable"),
        )

        val ex = assertFailsWith<WebClientException> {
            webClient
                .get()
                .uri(mockWebServer.url("/test").toUri())
                .sync()
                .performRaw()
        }

        assertEquals(503, ex.statusCode)
        assertEquals("Service Unavailable", ex.body)
    }
}
