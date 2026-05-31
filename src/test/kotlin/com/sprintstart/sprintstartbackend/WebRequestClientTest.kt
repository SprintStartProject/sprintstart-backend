package com.sprintstart.sprintstartbackend

import kotlinx.serialization.Serializable
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Serializable
private data class SampleRequest(
    val name: String,
)

@Serializable
private data class SampleResponse(
    val result: String,
)

class WebRequestClientTest {
    private val mockWebServer = MockWebServer()
    private val client = WebRequestClient()

    @BeforeEach
    fun setUp() = mockWebServer.start()

    @AfterEach
    fun tearDown() = mockWebServer.shutdown()

    @Nested
    inner class Get {
        @Test
        fun `deserializes response body correctly`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setBody("""{"result":"ok"}""")
                    .addHeader("Content-Type", "application/json"),
            )

            val result = client.get<SampleResponse>(mockWebServer.url("/test").toUri())

            assertEquals("ok", result.result)
        }

        @Test
        fun `sends GET request to correct path`() {
            mockWebServer.enqueue(MockResponse().setBody("""{"result":"ok"}"""))

            client.get<SampleResponse>(mockWebServer.url("/some-path").toUri())

            val recorded = mockWebServer.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/some-path", recorded.path)
        }

        @Test
        fun `ignores unknown fields in response`() {
            mockWebServer.enqueue(
                MockResponse().setBody("""{"result":"ok","unknownField":"ignored"}"""),
            )

            // Should not throw despite unknown field
            val result = client.get<SampleResponse>(mockWebServer.url("/test").toUri())

            assertEquals("ok", result.result)
        }
    }

    @Nested
    inner class Post {
        @Test
        fun `serializes request body and sends as JSON`() {
            mockWebServer.enqueue(MockResponse().setBody("""{"result":"created"}"""))

            client.post<SampleRequest, SampleResponse>(
                uri = mockWebServer.url("/test").toUri(),
                body = SampleRequest(name = "sprint"),
            )

            val recorded = mockWebServer.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("application/json", recorded.getHeader("Content-Type"))
            assertEquals("""{"name":"sprint"}""", recorded.body.readUtf8())
        }

        @Test
        fun `deserializes response body correctly`() {
            mockWebServer.enqueue(MockResponse().setBody("""{"result":"created"}"""))

            val result = client.post<SampleRequest, SampleResponse>(
                uri = mockWebServer.url("/test").toUri(),
                body = SampleRequest(name = "sprint"),
            )

            assertEquals("created", result.result)
        }
    }

    @Nested
    inner class Put {
        @Test
        fun `sends PUT request with serialized body`() {
            mockWebServer.enqueue(MockResponse().setBody("""{"result":"updated"}"""))

            client.put<SampleRequest, SampleResponse>(
                uri = mockWebServer.url("/test").toUri(),
                body = SampleRequest(name = "updated"),
            )

            val recorded = mockWebServer.takeRequest()
            assertEquals("PUT", recorded.method)
            assertEquals("""{"name":"updated"}""", recorded.body.readUtf8())
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `sends DELETE request with serialized body`() {
            mockWebServer.enqueue(MockResponse().setBody("""{"result":"deleted"}"""))

            client.delete<SampleRequest, SampleResponse>(
                uri = mockWebServer.url("/test").toUri(),
                body = SampleRequest(name = "to-delete"),
            )

            val recorded = mockWebServer.takeRequest()
            assertEquals("DELETE", recorded.method)
            assertEquals("""{"name":"to-delete"}""", recorded.body.readUtf8())
        }
    }
}
