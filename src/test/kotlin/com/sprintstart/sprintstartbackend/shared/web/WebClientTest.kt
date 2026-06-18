package com.sprintstart.sprintstartbackend.shared.web

import com.sprintstart.sprintstartbackend.chat.models.responses.AiGenerateChatTitleResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import kotlin.test.Test

class WebClientTest {
    private val mockWebServer = MockWebServer()
    private val httpClient = HttpClient.newBuilder().build()
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val webClient = WebClient(httpClient, jsonParser)

    @BeforeEach
    fun start() = mockWebServer.start()

    @AfterEach
    fun stop() = mockWebServer.shutdown()

    @Test
    fun `sync GET deserializes response correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title": "hello"}"""),
        )

        val response = webClient
            .get()
            .uri(mockWebServer.url("/test").toUri())
            .sync()
            .perform<AiGenerateChatTitleResponse>()

        assertEquals("hello", response.title)
        assertEquals("/test", mockWebServer.takeRequest().path)
    }

    @Test
    fun `sync throws WebClientException on non-2xx`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val ex = assertThrows<WebClientException> {
            webClient
                .get()
                .uri(mockWebServer.url("/test").toUri())
                .sync()
                .perform<AiGenerateChatTitleResponse>()
        }

        assertEquals(500, ex.statusCode)
    }

    @Test
    fun `stream emits chunks and stops at termination marker`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"type":"token","content":"Hello"}
                    
                    data: {"type":"token","content":" world"}
                    
                    data: [DONE]
                    
                    """.trimIndent(),
                ),
        )

        val chunks = webClient
            .post()
            .uri(mockWebServer.url("/stream").toUri())
            .body(mapOf("prompt" to "hi"))
            .stream()
            .perform<AiStreamMessage>()
            .toList()

        assertEquals(2, chunks.size)
        assertEquals("Hello", chunks[0].content)
        assertEquals(" world", chunks[1].content)
    }
}
