package com.sprintstart.sprintstartbackend.shared.web

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RequestBuilderTest {
    private val mockWebServer = MockWebServer()
    private val httpClient = HttpClient.newBuilder().build()
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val webClient = WebClient(httpClient, jsonParser)

    @BeforeEach
    fun start() = mockWebServer.start()

    @AfterEach
    fun stop() = mockWebServer.shutdown()

    // ── URI ───────────────────────────────────────────────────────────────────

    @Test
    fun `uri(String) and uri(URI) both reach the server`() = runTest {
        repeat(2) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        }

        // String overload
        webClient
            .get()
            .uri(
                mockWebServer
                    .url("/test")
                    .toString(),
            ).sync()
            .performRaw()

        // URI overload
        webClient
            .get()
            .uri(
                mockWebServer
                    .url("/test")
                    .toUri(),
            ).sync()
            .performRaw()

        assertEquals("/test", mockWebServer.takeRequest().path)
        assertEquals("/test", mockWebServer.takeRequest().path)
    }

    // ── Headers ───────────────────────────────────────────────────────────────

    @Test
    fun `single header is sent to server`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        webClient
            .get()
            .uri(
                mockWebServer
                    .url("/test")
                    .toUri(),
            ).header("Authorization", "Bearer token123")
            .sync()
            .performRaw()

        val recorded = mockWebServer.takeRequest()
        assertEquals("Bearer token123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `multiple headers are all sent to server`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        webClient
            .get()
            .uri(
                mockWebServer
                    .url("/test")
                    .toUri(),
            ).headers(
                "Authorization" to "Bearer token123",
                "X-Request-Id" to "abc-456",
            ).sync()
            .performRaw()

        val recorded = mockWebServer.takeRequest()
        assertEquals("Bearer token123", recorded.getHeader("Authorization"))
        assertEquals("abc-456", recorded.getHeader("X-Request-Id"))
    }

    @Test
    fun `headers are immutable - chaining does not mutate original builder`() = runTest {
        repeat(2) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        }

        val base = webClient
            .get()
            .uri(
                mockWebServer
                    .url("/test")
                    .toUri(),
            )
        val withAuth = base.header("Authorization", "Bearer token")
        val withoutAuth = base

        withAuth.sync().performRaw()
        withoutAuth.sync().performRaw()

        assertNotNull(mockWebServer.takeRequest().getHeader("Authorization"))
        assertEquals(null, mockWebServer.takeRequest().getHeader("Authorization"))
    }

    // ── Body ──────────────────────────────────────────────────────────────────

    @Test
    fun `body() serializes to JSON and sets Content-Type automatically`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        webClient
            .post()
            .uri(
                mockWebServer
                    .url("/test")
                    .toUri(),
            ).body(mapOf("key" to "value"))
            .sync()
            .performRaw()

        val recorded = mockWebServer.takeRequest()
        assertEquals("application/json", recorded.getHeader("Content-Type"))
        assertEquals("""{"key":"value"}""", recorded.body.readUtf8())
    }
}
