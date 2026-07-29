package com.sprintstart.sprintstartbackend.connectors.jira

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredential
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredentialsId
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.util.Base64

class JiraClientTest {
    private val mockWebServer = MockWebServer()
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private lateinit var jiraClient: JiraClient

    @BeforeEach
    fun setUp() {
        mockWebServer.start()
        val httpClient = HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
        jiraClient = JiraClient(WebClient(httpClient, jsonParser))
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `searchIssues sends Basic auth header`() {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val credential = JiraCredential(JiraCredentialsId("user@example.com", "token"), "secret")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(emptySearchResponse()),
        )

        runBlocking { jiraClient.searchIssues(baseUrl, credential, "project=TEST") }

        val request = mockWebServer.takeRequest()
        val authHeader = request.getHeader("Authorization")
        assertThat(authHeader).startsWith("Basic ")

        val decoded = authHeader?.removePrefix("Basic ")?.let { String(Base64.getDecoder().decode(it)) }
        assertThat(decoded).isEqualTo("user@example.com:secret")
    }

    @Test
    fun `searchIssues uses nextPageToken for pagination`() {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val credential = JiraCredential(JiraCredentialsId("user@example.com", "token"), "secret")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(searchResponse(listOf(issueJson("1", "TEST-1")), nextPageToken = "token-123")),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(searchResponse(listOf(issueJson("2", "TEST-2")), isLast = true)),
        )

        val issues = runBlocking { jiraClient.searchIssues(baseUrl, credential, "project=TEST") }

        assertThat(issues).hasSize(2)
        assertThat(issues.map { it.key }).containsExactly("TEST-1", "TEST-2")

        val firstRequest = mockWebServer.takeRequest()
        assertThat(firstRequest.path).doesNotContain("nextPageToken")

        val secondRequest = mockWebServer.takeRequest()
        assertThat(secondRequest.path).contains("nextPageToken=token-123")
    }

    private fun emptySearchResponse(): String = searchResponse(emptyList(), isLast = true)

    private fun searchResponse(
        issues: List<String>,
        isLast: Boolean = false,
        nextPageToken: String? = null,
    ): String {
        val tokenPart = nextPageToken?.let { ", \"nextPageToken\": \"$it\"" } ?: ""
        return """
            {
                "issues": [${issues.joinToString(", ")}],
                "isLast": $isLast$tokenPart
            }
            """.trimIndent()
    }

    @Test
    fun `searchProjects does not follow redirects to avoid dropping Authorization header`() {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val credential = JiraCredential(JiraCredentialsId("user@example.com", "token"), "secret")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "$baseUrl/redirected/project/search"),
        )

        val exception = assertThrows<WebClientException> {
            runBlocking { jiraClient.searchProjects(baseUrl, credential) }
        }

        assertThat(exception.statusCode).isEqualTo(302)
    }

    @Test
    fun `searchProjects sends Basic auth and paginates`() {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val credential = JiraCredential(JiraCredentialsId("user@example.com", "token"), "secret")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(projectSearchResponse(listOf(projectJson("PROJ")), isLast = false)),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(projectSearchResponse(listOf(projectJson("NEXT")), isLast = true)),
        )

        val projects = runBlocking { jiraClient.searchProjects(baseUrl, credential) }

        assertThat(projects).hasSize(2)
        assertThat(projects.map { it.key }).containsExactly("PROJ", "NEXT")

        val request = mockWebServer.takeRequest()
        assertThat(request.getHeader("Authorization")).startsWith("Basic ")
    }

    @Test
    fun `checkInstanceCapabilities returns true for a valid Jira server`() {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"serverTitle\": \"Jira\"}"),
        )

        val result = runBlocking { jiraClient.checkInstanceCapabilities(baseUrl) }

        assertThat(result).isTrue()
    }

    @Test
    fun `checkInstanceCapabilities returns false when server title is not Jira`() {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"serverTitle\": \"Confluence\"}"),
        )

        val result = runBlocking { jiraClient.checkInstanceCapabilities(baseUrl) }

        assertThat(result).isFalse()
    }

    @Test
    fun `checkInstanceCapabilities returns false when instance is not reachable`() {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        mockWebServer.enqueue(MockResponse().setResponseCode(503))

        val result = runBlocking { jiraClient.checkInstanceCapabilities(baseUrl) }

        assertThat(result).isFalse()
        assertThat(mockWebServer.takeRequest().path).isEqualTo("/rest/api/3/serverInfo")
    }

    private fun projectSearchResponse(
        projects: List<String>,
        isLast: Boolean,
    ): String =
        """
        {
            "values": [${projects.joinToString(", ")}],
            "isLast": $isLast
        }
        """.trimIndent()

    private fun projectJson(key: String): String = "{\"key\": \"$key\"}"

    private fun issueJson(id: String, key: String): String {
        return """
            {
                "id": "$id",
                "key": "$key",
                "changelog": { "histories": [] },
                "fields": {
                    "summary": "Issue $key",
                    "issueType": { "name": "Task", "description": "" },
                    "creator": { "displayName": "A", "active": true, "created": "2024-01-01T00:00:00Z", "updated": "2024-01-01T00:00:00Z" },
                    "created": "2024-01-01T00:00:00Z",
                    "description": { "type": "doc", "version": 1, "content": { "type": "text", "text": "desc" } },
                    "project": { "key": "TEST", "name": "Test", "projectTypeKey": "software" },
                    "reporter": { "displayName": "A", "active": true, "created": "2024-01-01T00:00:00Z", "updated": "2024-01-01T00:00:00Z" },
                    "comment": { "comments": [] },
                    "assignee": null,
                    "updated": "2024-01-01T00:00:00Z",
                    "status": { "name": "Open", "description": "", "category": { "key": "new", "name": "To Do" } }
                }
            }
            """.trimIndent()
    }
}
