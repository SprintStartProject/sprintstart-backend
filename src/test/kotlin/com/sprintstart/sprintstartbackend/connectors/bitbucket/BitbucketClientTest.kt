package com.sprintstart.sprintstartbackend.connectors.bitbucket

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.BitbucketConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.UploadConfig
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.entity.BitbucketCredentialId
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.entity.BitbucketUser
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
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
import java.net.URLDecoder
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets

class BitbucketClientTest {
    private val mockWebServer = MockWebServer()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private lateinit var bitbucketClient: BitbucketClient
    private lateinit var applicationConfig: ApplicationConfig

    @BeforeEach
    fun setUp() {
        mockWebServer.start()

        val baseUrl = mockWebServer.url("").toString()

        applicationConfig = ApplicationConfig(
            ai = AiConfig(baseUrl = "http://unused"),
            github = GithubConfig(baseUrl = "https://api.github.com"),
            bitbucket = BitbucketConfig(baseUrl = baseUrl),
            crypto = CryptoConfig(masterKey = "unused", salt = "unused"),
            upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
        )

        val httpClient = HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()

        bitbucketClient = BitbucketClient(
            webClient = WebClient(httpClient, jsonParser),
            applicationConfig = applicationConfig,
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun connection(
        workspace: String = "owner",
        slug: String = "repo",
        token: String = "test-token",
    ) = BitbucketConnection(
        workspace = workspace,
        slug = slug,
        user = BitbucketUser(
            id = BitbucketCredentialId(authId = "some-id", name = "test-credential"),
            token = token,
        ),
    )

    @Nested
    inner class FetchWorkspaceMetadata {
        @Test
        fun `fetchWorkspaceMetadata requests the workspace endpoint and maps the response`() {
            mockWebServer.enqueue(workspaceMetadataResponse())

            val result = runBlocking { bitbucketClient.fetchWorkspaceMetadata("team", "test-token") }

            assertThat(result.slug).isEqualTo("team")
            assertThat(result.name).isEqualTo("Team")
            assertThat(result.isPrivate).isFalse()
            assertThat(result.url).isEqualTo("https://bitbucket.org/team")
            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/workspaces/team")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }

        @Test
        fun `fetchWorkspaceMetadata percent-encodes the workspace path segment`() {
            mockWebServer.enqueue(workspaceMetadataResponse())

            runBlocking { bitbucketClient.fetchWorkspaceMetadata("my team", "test-token") }

            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/workspaces/my%20team")
        }

        @Test
        fun `fetchWorkspaceMetadata throws on non-2xx response`() {
            mockWebServer.enqueue(notFoundResponse())

            assertThatThrownBy {
                runBlocking { bitbucketClient.fetchWorkspaceMetadata("missing", "test-token") }
            }.isInstanceOf(WebClientException::class.java)
        }
    }

    @Nested
    inner class GetWorkspaceMembers {
        @Test
        fun `getWorkspaceMembers returns members from a single page`() {
            mockWebServer.enqueue(membersPage(members = listOf("alice", "bob"), nextUrl = null))

            val result = runBlocking { bitbucketClient.getWorkspaceMembers("team", "test-token") }

            assertThat(result.members.map { it.user.displayName }).containsExactly("Alice", "Bob")
            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/workspaces/team/members?pagelen=100")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }

        @Test
        fun `getWorkspaceMembers follows the next link across pages`() {
            val secondPageUrl = mockWebServer.url("/workspaces/team/members?page=2").toString()
            mockWebServer.enqueue(membersPage(members = listOf("alice"), nextUrl = secondPageUrl))
            mockWebServer.enqueue(membersPage(members = listOf("bob"), nextUrl = null))

            val result = runBlocking { bitbucketClient.getWorkspaceMembers("team", "test-token") }

            assertThat(result.members.map { it.user.displayName }).containsExactly("Alice", "Bob")
            mockWebServer.takeRequest() // discard first
            val secondRequest = mockWebServer.takeRequest()
            assertThat(secondRequest.path).isEqualTo("/workspaces/team/members?page=2")
            assertThat(secondRequest.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }
    }

    @Nested
    inner class WorkspaceExists {
        @Test
        fun `workspaceExists returns true when the workspace endpoint responds with 2xx`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val result = runBlocking { bitbucketClient.workspaceExists("team", "test-token") }

            assertThat(result).isTrue()
        }

        @Test
        fun `workspaceExists returns false when the workspace endpoint responds with 404`() {
            mockWebServer.enqueue(notFoundResponse())

            val result = runBlocking { bitbucketClient.workspaceExists("missing", "test-token") }

            assertThat(result).isFalse()
        }

        @Test
        fun `workspaceExists propagates exception on non-404 error`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

            assertThatThrownBy {
                runBlocking { bitbucketClient.workspaceExists("team", "test-token") }
            }.hasMessageContaining("500")
        }
    }

    @Nested
    inner class RepositoryExists {
        @Test
        fun `repositoryExists returns true when the repository endpoint responds with 2xx`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val result = runBlocking { bitbucketClient.repositoryExists(connection()) }

            assertThat(result).isTrue()
            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/repositories/owner/repo")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }

        @Test
        fun `repositoryExists returns false when the repository endpoint responds with 404`() {
            mockWebServer.enqueue(notFoundResponse())

            val result = runBlocking { bitbucketClient.repositoryExists(connection()) }

            assertThat(result).isFalse()
        }

        @Test
        fun `repositoryExists propagates exception on non-404 error`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

            assertThatThrownBy {
                runBlocking { bitbucketClient.repositoryExists(connection()) }
            }.hasMessageContaining("500")
        }
    }

    @Nested
    inner class DiscoverRepositoriesOfWorkspace {
        @Test
        fun `discoverRepositoriesOfWorkspace returns repositories and derives their urls`() {
            mockWebServer.enqueue(repositoriesPage(repositories = listOf("repo-a", "repo-b")))

            val result = runBlocking {
                bitbucketClient.discoverRepositoriesOfWorkspace(
                    workspace = "team",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            assertThat(result.repositories).hasSize(2)
            assertThat(result.repositories.map { it.name }).containsExactly("repo-a", "repo-b")
            assertThat(result.repositories.first().url).isEqualTo("https://bitbucket.org/team/repo-a")
            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/repositories/team?pagelen=30&page=1")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }

        @Test
        fun `discoverRepositoriesOfWorkspace converts zero-based page to one-based page number`() {
            mockWebServer.enqueue(repositoriesPage(repositories = emptyList()))

            runBlocking {
                bitbucketClient.discoverRepositoriesOfWorkspace(
                    workspace = "team",
                    token = "test-token",
                    page = 2,
                    pageSize = 10,
                )
            }

            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/repositories/team?pagelen=10&page=3")
        }

        @Test
        fun `discoverRepositoriesOfWorkspace throws on non-2xx response`() {
            mockWebServer.enqueue(notFoundResponse())

            assertThatThrownBy {
                runBlocking {
                    bitbucketClient.discoverRepositoriesOfWorkspace(
                        workspace = "missing",
                        token = "test-token",
                        page = 0,
                        pageSize = 30,
                    )
                }
            }.isInstanceOf(WebClientException::class.java)
        }
    }

    @Nested
    inner class FetchAllPullRequests {
        @Test
        fun `fetchAllPullRequests requests every pull request state when no filter is given`() {
            mockWebServer.enqueue(pullRequestsPage(pullRequests = emptyList(), nextUrl = null))

            val result = runBlocking { bitbucketClient.fetchAllPullRequests(connection()) }

            assertThat(result).isEmpty()
            val request = mockWebServer.takeRequest()
            assertThat(request.path).startsWith("/repositories/owner/repo/pullrequests?pagelen=100")
            listOf("OPEN", "MERGED", "DECLINED", "SUPERSEDED").forEach { state ->
                assertThat(request.path).contains("state=$state")
            }
            assertThat(request.path).doesNotContain("q=")
        }

        @Test
        fun `fetchAllPullRequests filters on updated_on when sinceTimestamp is provided`() {
            mockWebServer.enqueue(pullRequestsPage(pullRequests = emptyList(), nextUrl = null))

            runBlocking {
                bitbucketClient.fetchAllPullRequests(
                    connection(),
                    sinceTimestamp = "2024-01-01T00:00:00Z",
                )
            }

            val request = mockWebServer.takeRequest()
            val decodedPath = URLDecoder.decode(request.path, StandardCharsets.UTF_8)
            assertThat(decodedPath).contains("""updated_on >= "2024-01-01T00:00:00Z"""")
        }

        @Test
        fun `fetchAllPullRequests returns pull requests with their metadata`() {
            mockWebServer.enqueue(pullRequestsPage(pullRequests = listOf(42), nextUrl = null))

            val result = runBlocking { bitbucketClient.fetchAllPullRequests(connection()) }

            assertThat(result).hasSize(1)
            val pullRequest = result.first()
            assertThat(pullRequest.id).isEqualTo(42)
            assertThat(pullRequest.title).isEqualTo("PR 42")
            assertThat(pullRequest.state).isEqualTo("MERGED")
            assertThat(pullRequest.author?.nickname).isEqualTo("author")
            assertThat(pullRequest.url).isEqualTo("https://bitbucket.org/owner/repo/pullrequests/42")
        }

        @Test
        fun `fetchAllPullRequests follows the next link across pages`() {
            val secondPageUrl = mockWebServer.url("/repositories/owner/repo/pullrequests?page=2").toString()
            mockWebServer.enqueue(pullRequestsPage(pullRequests = listOf(1), nextUrl = secondPageUrl))
            mockWebServer.enqueue(pullRequestsPage(pullRequests = listOf(2), nextUrl = null))

            val result = runBlocking { bitbucketClient.fetchAllPullRequests(connection()) }

            assertThat(result.map { it.id }).containsExactly(1, 2)
            mockWebServer.takeRequest() // discard first
            val secondRequest = mockWebServer.takeRequest()
            assertThat(secondRequest.path).isEqualTo("/repositories/owner/repo/pullrequests?page=2")
            assertThat(secondRequest.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }
    }

    @Nested
    inner class FetchPullRequest {
        @Test
        fun `fetchPullRequest requests the single pull request endpoint`() {
            mockWebServer.enqueue(singlePullRequestResponse(prNumber = 42))

            val result = runBlocking { bitbucketClient.fetchPullRequest(connection(), pullRequestId = 42) }

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(42)
            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/repositories/owner/repo/pullrequests/42")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }

        @Test
        fun `fetchPullRequest returns null when the pull request does not exist`() {
            mockWebServer.enqueue(notFoundResponse())

            val result = runBlocking { bitbucketClient.fetchPullRequest(connection(), pullRequestId = 99) }

            assertThat(result).isNull()
        }

        @Test
        fun `fetchPullRequest propagates exception on non-404 error`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

            assertThatThrownBy {
                runBlocking { bitbucketClient.fetchPullRequest(connection(), pullRequestId = 42) }
            }.hasMessageContaining("500")
        }
    }

    @Nested
    inner class FetchAllPullRequestComments {
        @Test
        fun `fetchAllPullRequestComments returns comments including the deleted marker`() {
            mockWebServer.enqueue(
                commentsPage(comments = listOf(commentJson(1, deleted = false), commentJson(2, deleted = true))),
            )

            val result = runBlocking { bitbucketClient.fetchAllPullRequestComments(connection(), pullRequestId = 42) }

            assertThat(result).hasSize(2)
            assertThat(result.first().user?.nickname).isEqualTo("commenter-1")
            assertThat(result.first().deleted).isFalse()
            assertThat(result.last().deleted).isTrue()
            assertThat(result.first().content?.raw).isEqualTo("Comment 1")
            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/repositories/owner/repo/pullrequests/42/comments?pagelen=100")
        }

        @Test
        fun `fetchAllPullRequestComments follows the next link across pages`() {
            val secondPageUrl = mockWebServer.url("/repositories/owner/repo/pullrequests/42/comments?page=2").toString()
            mockWebServer.enqueue(commentsPage(comments = listOf(commentJson(1)), nextUrl = secondPageUrl))
            mockWebServer.enqueue(commentsPage(comments = listOf(commentJson(2)), nextUrl = null))

            val result = runBlocking { bitbucketClient.fetchAllPullRequestComments(connection(), pullRequestId = 42) }

            assertThat(result.map { it.id }).containsExactly(1, 2)
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun accountJson(nickname: String) =
        """
        {
            "uuid": "{uuid-$nickname}",
            "display_name": "${nickname.replaceFirstChar { it.uppercase() }}",
            "nickname": "$nickname",
            "links": { "html": { "href": "https://bitbucket.org/$nickname" } }
        }
        """.trimIndent()

    private fun workspaceMetadataResponse() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
                "uuid": "{workspace-uuid}",
                "slug": "team",
                "name": "Team",
                "is_private": false,
                "created_on": "2020-01-01T00:00:00+00:00",
                "links": { "html": { "href": "https://bitbucket.org/team" } }
            }
            """.trimIndent(),
        )

    private fun membersPage(members: List<String>, nextUrl: String?) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
                "values": [${members.joinToString(",") { """{ "user": ${accountJson(it)} }""" }}],
                "next": ${if (nextUrl != null) "\"$nextUrl\"" else "null"}
            }
            """.trimIndent(),
        )

    private fun repositoriesPage(repositories: List<String>) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(repositoriesJson(repositories))

    private fun repositoriesJson(repositories: List<String>): String =
        repositories.joinToString(
            separator = ",",
            prefix = """{ "values": [""",
            postfix = """], "next": null }""",
        ) { name ->
            repositoryJson(name)
        }

    private fun repositoryJson(name: String) =
        """
        {
            "name": "$name",
            "slug": "$name",
            "full_name": "team/$name",
            "is_private": false,
            "links": { "html": { "href": "https://bitbucket.org/team/$name" } }
        }
        """.trimIndent()

    private fun pullRequestJson(prNumber: Int) =
        """
        {
            "id": $prNumber,
            "title": "PR $prNumber",
            "description": "PR body",
            "state": "MERGED",
            "author": ${accountJson("author")},
            "created_on": "2024-01-01T00:00:00+00:00",
            "updated_on": "2024-01-03T00:00:00+00:00",
            "closed_on": "2024-01-04T00:00:00+00:00",
            "merge_commit": { "hash": "abc123" },
            "links": { "html": { "href": "https://bitbucket.org/owner/repo/pullrequests/$prNumber" } }
        }
        """.trimIndent()

    private fun pullRequestsPage(pullRequests: List<Int>, nextUrl: String?) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
                "values": [${pullRequests.joinToString(",") { pullRequestJson(it) }}],
                "next": ${if (nextUrl != null) "\"$nextUrl\"" else "null"}
            }
            """.trimIndent(),
        )

    private fun singlePullRequestResponse(prNumber: Int) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(pullRequestJson(prNumber))

    private fun commentJson(commentId: Int, deleted: Boolean = false) =
        """
        {
            "id": $commentId,
            "created_on": "2024-01-02T00:00:00+00:00",
            "updated_on": "2024-01-02T00:00:00+00:00",
            "content": { "raw": "Comment $commentId", "markup": "markdown" },
            "user": ${accountJson("commenter-$commentId")},
            "deleted": $deleted
        }
        """.trimIndent()

    private fun commentsPage(comments: List<String>, nextUrl: String? = null) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
                "values": [${comments.joinToString(",")}],
                "next": ${if (nextUrl != null) "\"$nextUrl\"" else "null"}
            }
            """.trimIndent(),
        )

    private fun notFoundResponse() = MockResponse()
        .setResponseCode(404)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"error": {"message": "Not found"}}""")
}
