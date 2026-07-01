package com.sprintstart.sprintstartbackend.github

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubUser
import com.sprintstart.sprintstartbackend.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.github.util.GithubQueryLoader
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.http.HttpClient

class GithubClientTest {
    private val mockWebServer = MockWebServer()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private lateinit var githubClient: GithubClient
    private lateinit var queryLoader: GithubQueryLoader

    @BeforeEach
    fun setUp() {
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/graphql").toString()
        val repoBaseUrl = mockWebServer.url("/repos").toString()

        val applicationConfig = ApplicationConfig(
            ai = AiConfig(baseUrl = "http://unused"),
            github = GithubConfig(
                baseUrl = baseUrl,
                repoBaseUrl = repoBaseUrl,
                cron = "0 0 * * *",
            ),
            crypto = CryptoConfig(masterKey = "unused", salt = "unused"),
        )

        queryLoader = mockk {
            every { load("github/graphql/100-issues.graphql") } returns "{ issuesQuery }"
            every { load("github/graphql/issues-since.graphql") } returns "{ issuesSinceQuery }"
            every { load("github/graphql/pullrequests-since.graphql") } returns "{ prListQuery }"
            every { load("github/graphql/100-pullrequests-deep.graphql") } returns "{ prDetailsQuery }"
        }

        val httpClient = HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()

        githubClient = GithubClient(
            webClient = WebClient(httpClient, jsonParser),
            applicationConfig = applicationConfig,
            queryLoader = queryLoader,
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Nested
    inner class RepositoryExists {
        @Test
        fun `repositoryExists returns true when GitHub responds with 2xx`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}"),
            )

            val result = runBlocking { githubClient.repositoryExists(repository) }

            assertThat(result).isTrue()
        }

        @Test
        fun `repositoryExists returns false when GitHub responds with 404`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"message": "Not Found"}"""),
            )

            val result = runBlocking { githubClient.repositoryExists(repository) }

            assertThat(result).isFalse()
        }

        @Test
        fun `repositoryExists propagates exception on non-404 error`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

            assertThatThrownBy {
                runBlocking { githubClient.repositoryExists(repository) }
            }.hasMessageContaining("500")
        }
    }

    @Nested
    inner class FetchIssues {
        @Test
        fun `fetchIssues uses all-issues query when no sinceTimestamp provided`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(emptyIssuesResponse())

            runBlocking { githubClient.fetchIssues(repository) }

            verify { queryLoader.load("github/graphql/100-issues.graphql") }
            verify(exactly = 0) { queryLoader.load("github/graphql/issues-since.graphql") }
        }

        @Test
        fun `fetchIssues uses since-query when sinceTimestamp is provided`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(emptyIssuesResponse())

            runBlocking { githubClient.fetchIssues(repository, sinceTimestamp = "2024-01-01T00:00:00Z") }

            verify { queryLoader.load("github/graphql/issues-since.graphql") }
            verify(exactly = 0) { queryLoader.load("github/graphql/100-issues.graphql") }
        }

        @Test
        fun `fetchIssues passes sinceTimestamp in request variables`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(emptyIssuesResponse())

            runBlocking { githubClient.fetchIssues(repository, sinceTimestamp = "2024-01-01T00:00:00Z") }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.body.readUtf8()).contains("2024-01-01T00:00:00Z")
        }
    }

    @Nested
    inner class FetchIssuesPagination {
        @Test
        fun `fetchIssues returns all issues from a single page`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(issuesResponse(issues = listOf(issueJson(1)), hasNextPage = false))

            val result = runBlocking { githubClient.fetchIssues(repository) }

            assertThat(result).hasSize(1)
            assertThat(result.first().number).isEqualTo(1)
            assertThat(result.first().title).isEqualTo("Issue 1")
        }

        @Test
        fun `fetchIssues paginates until hasNextPage is false`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(issuesResponse(listOf(issueJson(1)), hasNextPage = true, cursor = "cursor-abc"))
            mockWebServer.enqueue(issuesResponse(listOf(issueJson(2)), hasNextPage = false))

            val result = runBlocking { githubClient.fetchIssues(repository) }

            assertThat(result).hasSize(2)
            assertThat(result.map { it.number }).containsExactly(1, 2)
        }

        @Test
        fun `fetchIssues sends cursor in second request when paginating`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(issuesResponse(listOf(issueJson(1)), hasNextPage = true, cursor = "cursor-abc"))
            mockWebServer.enqueue(issuesResponse(listOf(issueJson(2)), hasNextPage = false))

            runBlocking { githubClient.fetchIssues(repository) }

            mockWebServer.takeRequest() // discard first
            val secondRequest = mockWebServer.takeRequest()
            assertThat(secondRequest.body.readUtf8()).contains("cursor-abc")
        }

        @Test
        fun `fetchIssues returns empty list when repository has no issues`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(emptyIssuesResponse())

            val result = runBlocking { githubClient.fetchIssues(repository) }

            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class FetchAllPullRequests {
        @Test
        fun `fetchAllPullRequests returns empty list when no PRs found`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(emptyPrSearchResponse())

            val result = runBlocking { githubClient.fetchAllPullRequests(repository) }

            assertThat(result).isEmpty()
        }

        @Test
        fun `fetchAllPullRequests includes sinceTimestamp in search query string when provided`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(emptyPrSearchResponse())

            runBlocking {
                githubClient.fetchAllPullRequests(repository, sinceTimestamp = "2024-01-01T00:00:00Z")
            }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.body.readUtf8()).contains("updated:>=2024-01-01T00:00:00Z")
        }

        @Test
        fun `fetchAllPullRequests does not include updated filter when sinceTimestamp is null`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(emptyPrSearchResponse())

            runBlocking { githubClient.fetchAllPullRequests(repository) }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.body.readUtf8()).doesNotContain("updated:>=")
        }

        @Test
        fun `fetchAllPullRequests fetches details for each PR number found`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(prSearchResponse(listOf(prNodeJson(number = 42))))
            mockWebServer.enqueue(singlePrResponse(prNumber = 42))

            val result = runBlocking { githubClient.fetchAllPullRequests(repository) }

            assertThat(result).hasSize(1)
            assertThat(result.first().number).isEqualTo(42)
        }

        @Test
        fun `fetchAllPullRequests skips PR when details response returns null`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(prSearchResponse(listOf(prNodeJson(number = 99))))
            mockWebServer.enqueue(nullSinglePrResponse())

            val result = runBlocking { githubClient.fetchAllPullRequests(repository) }

            assertThat(result).isEmpty()
        }

        @Test
        fun `fetchAllPullRequests sends auth header on every request`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            mockWebServer.enqueue(emptyPrSearchResponse())

            runBlocking { githubClient.fetchAllPullRequests(repository) }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun issueJson(number: Int) =
        """
        {
            "number": $number,
            "title": "Issue $number",
            "body": "Body of issue $number",
            "state": "OPEN",
            "createdAt": "2024-01-01T00:00:00Z",
            "updatedAt": "2024-01-02T00:00:00Z",
            "closedAt": null,
            "url": "https://github.com/owner/repo/issues/$number",
            "author": { "login": "user$number" },
            "labels": { "nodes": [] },
            "assignees": { "nodes": [] },
            "comments": { "nodes": [] }
        }
        """.trimIndent()

    private fun issuesResponse(
        issues: List<String>,
        hasNextPage: Boolean,
        cursor: String? = null,
    ) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
                "data": {
                    "repository": {
                        "issues": {
                            "nodes": [${issues.joinToString(",")}],
                            "pageInfo": {
                                "hasNextPage": $hasNextPage,
                                "endCursor": ${if (cursor != null) "\"$cursor\"" else "null"}
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
        )

    private fun emptyIssuesResponse() = issuesResponse(emptyList(), hasNextPage = false)

    private fun prNodeJson(number: Int) =
        """
        { "number": $number, "id": "PR_$number", "title": "PR $number" }
        """.trimIndent()

    private fun prSearchResponse(nodes: List<String>, hasNextPage: Boolean = false) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                    "data": {
                        "search": {
                            "nodes": [${nodes.joinToString(",")}],
                            "pageInfo": { "hasNextPage": $hasNextPage, "endCursor": null }
                        }
                    }
                }
                """.trimIndent(),
            )

    private fun emptyPrSearchResponse() = prSearchResponse(emptyList())

    private fun singlePrResponse(prNumber: Int) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
                "data": {
                    "repository": {
                        "pullRequest": {
                            "number": $prNumber,
                            "title": "PR $prNumber",
                            "body": "PR body",
                            "state": "OPEN",
                            "createdAt": "2024-01-01T00:00:00Z",
                            "mergedAt": null,
                            "url": "https://github.com/owner/repo/pull/$prNumber",
                            "author": { "login": "author" },
                            "labels": { "nodes": [] },
                            "reviews": { "nodes": [] },
                            "comments": { "nodes": [] },
                            "reviewThreads": { "nodes": [] }
                        }
                    }
                }
            }
            """.trimIndent(),
        )

    private fun nullSinglePrResponse() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{ "data": { "repository": { "pullRequest": null } } }""")
}
