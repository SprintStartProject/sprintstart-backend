package com.sprintstart.sprintstartbackend.connectors.overview

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import kotlin.test.assertEquals

class SourceClientTest {
    private val mockWebServer = MockWebServer()
    private lateinit var client: SourceClient

    @BeforeEach
    fun setUp() {
        mockWebServer.start()
        val httpClient = HttpClient.newBuilder().build()
        val jsonParser = Json { ignoreUnknownKeys = true }
        val webClient = WebClient(httpClient, jsonParser)
        val baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
        val applicationConfig = ApplicationConfig(
            ai = com.sprintstart.sprintstartbackend.AiConfig(baseUrl = baseUrl),
            github = com.sprintstart.sprintstartbackend.GithubConfig(
                baseUrl = "https://github.example.com",
                cron = "0 0 * * *",
            ),
            crypto = com.sprintstart.sprintstartbackend.CryptoConfig(
                masterKey = "test-master-key",
                salt = "test-salt",
            ),
            upload = com.sprintstart.sprintstartbackend.UploadConfig(
                directory = "/tmp/uploads",
                maxFileSizeBytes = 100,
            ),
        )
        client = SourceClient(applicationConfig, webClient)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `configureConnector sends PATCH to the correct endpoint`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.configureConnector("github", true)

        val request = mockWebServer.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/connectors/github", request.path)
        assertEquals("""{"enabled":true}""", request.body.readUtf8())
    }

    @Test
    fun `configureConnector sends disabled status`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.configureConnector("github", false)

        val request = mockWebServer.takeRequest()
        assertEquals("""{"enabled":false}""", request.body.readUtf8())
    }

    @Test
    fun `patchSources sends PATCH to the correct endpoint`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.patchSources("github", mapOf("owner/repo" to true))

        val request = mockWebServer.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/sources/github", request.path)
        assertEquals("""{"sources":{"owner/repo":true}}""", request.body.readUtf8())
    }
}
