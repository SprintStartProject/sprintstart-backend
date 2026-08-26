package com.sprintstart.sprintstartbackend.connectors.confluence.client

import com.sprintstart.sprintstartbackend.shared.web.WebClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.net.http.HttpClient
import java.util.Base64

internal const val TEST_EMAIL = "client-test@example.invalid"
internal const val TEST_TOKEN = "phase3-test-token"

internal abstract class ConfluenceClientTestSupport {
    protected val mockWebServer = MockWebServer()
    protected val credentials = ConfluenceClientCredentials(
        email = TEST_EMAIL,
        apiToken = TEST_TOKEN,
    )

    protected lateinit var client: ConfluenceClient
    protected lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        mockWebServer.start()
        baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val httpClient = HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
        client = ConfluenceClient(WebClient(httpClient, CONFLUENCE_TEST_JSON))
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    protected fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    protected fun enqueueError(status: Int, body: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    protected fun decodeBasicAuthorization(header: String?): String {
        val encoded = requireNotNull(header).removePrefix("Basic ")
        return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }

    protected fun basicAuthorizationValue(): String {
        val encoded = Base64.getEncoder().encodeToString("$TEST_EMAIL:$TEST_TOKEN".toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }
}
