package com.sprintstart.sprintstartbackend.ingestion

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.UploadConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ArtifactIngestionClientTest {
    private val mockWebServer = MockWebServer()
    private lateinit var client: ArtifactIngestionClient

    @BeforeEach
    fun setUp() {
        mockWebServer.start()
        val webClient = WebClient(HttpClient.newBuilder().build(), Json { ignoreUnknownKeys = true })
        val applicationConfig = ApplicationConfig(
            ai = AiConfig(baseUrl = mockWebServer.url("/").toString().removeSuffix("/")),
            github = GithubConfig(baseUrl = "https://github.example.com"),
            crypto = CryptoConfig(masterKey = "test-master-key", salt = "test-salt"),
            upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
        )
        client = ArtifactIngestionClient(webClient, applicationConfig)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchIngestStatus sends a GET with repeated artifact_ids and parses the snake_case body`() = runTest {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[""" +
                    """{"artifact_id":"$first","status":"indexed","updated_at":"2026-09-20""" +
                    """T10:00:00+00:00","chunk_count":3,"extra":1},""" +
                    """{"artifact_id":"$second","status":"unknown","updated_at":null,"chunk_count":null}]}""",
            ),
        )

        val response = client.fetchIngestStatus(listOf(first, second))

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/ingest/status?artifact_ids=$first&artifact_ids=$second", request.path)
        assertEquals(listOf(first.toString(), second.toString()), response.items.map { it.artifactId })
        assertEquals("indexed", response.items[0].status)
        assertEquals("2026-09-20T10:00:00+00:00", response.items[0].updatedAt)
        assertEquals(3, response.items[0].chunkCount)
        assertNull(response.items[1].updatedAt)
        assertNull(response.items[1].chunkCount)
    }

    @Test
    fun `fetchIngestStatus turns a non-2xx answer into IngestionResponseException`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("down"))

        val error = assertFailsWith<IngestionResponseException> {
            client.fetchIngestStatus(listOf(UUID.randomUUID()))
        }

        assertEquals("Failed to read ingest status (HTTP 503): down", error.message)
    }

    @Test
    fun `fetchIngestStatus rejects a body that is not the status shape`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"artifacts":[]}"""))

        assertFailsWith<SerializationException> { client.fetchIngestStatus(listOf(UUID.randomUUID())) }
    }

    @Test
    fun `fetchIngestStatus surfaces an unreachable AI service as IOException`() = runTest {
        mockWebServer.shutdown()

        assertFailsWith<IOException> { client.fetchIngestStatus(listOf(UUID.randomUUID())) }
    }

    @Test
    fun `fetchIngestStatus gives up on a hung AI service after the status timeout`() = runTest {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertFailsWith<HttpTimeoutException> { client.fetchIngestStatus(listOf(UUID.randomUUID())) }
    }
}
