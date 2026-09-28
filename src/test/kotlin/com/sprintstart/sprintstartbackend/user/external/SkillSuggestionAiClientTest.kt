package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.UploadConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.user.external.model.SkillCatalogItemDto
import com.sprintstart.sprintstartbackend.user.external.model.SkillSuggestionRequestDto
import com.sprintstart.sprintstartbackend.user.model.exceptions.SkillSuggestionAiException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillSuggestionAiClientTest {
    private val mockWebServer = MockWebServer()
    private lateinit var client: SkillSuggestionAiClient

    @BeforeEach
    fun setUp() {
        mockWebServer.start()
        val httpClient = HttpClient.newBuilder().build()
        val jsonParser = Json { ignoreUnknownKeys = true }
        val webClient = WebClient(httpClient, jsonParser)
        val baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
        val applicationConfig = ApplicationConfig(
            ai = AiConfig(baseUrl = baseUrl),
            github = GithubConfig(baseUrl = "https://github.example.com"),
            crypto = CryptoConfig(masterKey = "test-master-key", salt = "test-salt"),
            upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
        )
        client = SkillSuggestionAiClient(webClient, applicationConfig)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `suggestSkills sends POST to correct endpoint with serialized payload`() = runTest {
        val responseJson =
            """
            {
                "suggestions": [
                    {
                        "name": "React",
                        "category": "Frontend & UI",
                        "reason": "Used in the web client",
                        "confidence": "high",
                        "isNew": false,
                        "chunkIds": ["c1"]
                    },
                    {
                        "name": "Communication",
                        "category": "Soft Skills",
                        "reason": "Teamwork essential",
                        "confidence": "high",
                        "isNew": false,
                        "chunkIds": []
                    }
                ]
            }
            """.trimIndent()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson),
        )

        val request = SkillSuggestionRequestDto(
            roleName = "Frontend Developer",
            roleDescription = "Builds frontend components",
            projectId = "p1",
            projectIndustry = "Fintech",
            availableSkills = listOf(
                SkillCatalogItemDto(id = "s1", name = "React", category = "Frontend & UI", universal = false),
                SkillCatalogItemDto(id = "s2", name = "Communication", category = "Soft Skills", universal = true),
            ),
        )

        val result = client.suggestSkills(request)

        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/skills/suggest", recorded.path)

        assertEquals(2, result.suggestions.size)
        val react = result.suggestions[0]
        assertEquals("React", react.name)
        assertEquals("Frontend & UI", react.category)
        assertEquals("Used in the web client", react.reason)
        assertEquals("high", react.confidence)
        assertFalse(react.isNew)
        assertEquals(listOf("c1"), react.chunkIds)

        val comm = result.suggestions[1]
        assertEquals("Communication", comm.name)
        assertTrue(comm.chunkIds.isEmpty())
    }

    @Test
    fun `suggestSkills throws SkillSuggestionAiException on upstream failure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail":"LLM backend down"}"""),
        )

        val request = SkillSuggestionRequestDto(
            roleName = "Backend Developer",
            roleDescription = "APIs and services",
        )

        val ex = assertThrows<SkillSuggestionAiException> {
            client.suggestSkills(request)
        }

        assertEquals(503, ex.statusCode)
        assertTrue(ex.body.contains("LLM backend down"))
    }
}
