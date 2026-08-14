package com.sprintstart.sprintstartbackend.connectors.github

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.UploadConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.util.GithubQueryLoader
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
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
    private lateinit var applicationConfig: ApplicationConfig
    private lateinit var queryLoader: GithubQueryLoader

    @BeforeEach
    fun setUp() {
        mockWebServer.start()

        val baseUrl = mockWebServer.url("").toString()

        applicationConfig = ApplicationConfig(
            ai = AiConfig(baseUrl = "http://unused"),
            github = GithubConfig(
                baseUrl = baseUrl,
                cron = "0 0 * * *",
            ),
            crypto = CryptoConfig(masterKey = "unused", salt = "unused"),
            upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
        )

        queryLoader = mockk {
            every { load("github/graphql/100-issues.graphql") } returns "{ issuesQuery }"
            every { load("github/graphql/issues-since.graphql") } returns "{ issuesSinceQuery }"
            every { load("github/graphql/pullrequests-since.graphql") } returns "{ prListQuery }"
            every { load("github/graphql/100-pullrequests-deep.graphql") } returns "{ prDetailsQuery }"
            every { load("github/graphql/org-teams.graphql") } returns "{ organizationTeamsQuery }"
            every { load("github/graphql/org-team-members.graphql") } returns "{ organizationTeamMembersQuery }"
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

    @Nested
    inner class DiscoverRepositoriesOfOrg {
        @Test
        fun `discoverRepositoriesOfOrg returns empty list when user has no repos`() {
            mockWebServer.enqueue(emptyReposResponse())

            val result = runBlocking {
                githubClient.discoverRepositoriesOfOrg(
                    org = "octocat",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            assertThat(result.repositories).isEmpty()
        }

        @Test
        fun `discoverRepositoriesOfOrg returns repositories from response`() {
            mockWebServer.enqueue(reposResponse(listOf(repoJson(name = "repo-a"), repoJson(name = "repo-b"))))

            val result = runBlocking {
                githubClient.discoverRepositoriesOfOrg(
                    org = "octocat",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            assertThat(result.repositories).hasSize(2)
            assertThat(result.repositories.map { it.name }).containsExactly("repo-a", "repo-b")
        }

        @Test
        fun `discoverRepositoriesOfOrg requests correct path and query params`() {
            mockWebServer.enqueue(emptyReposResponse())

            runBlocking {
                githubClient.discoverRepositoriesOfOrg(
                    org = "octocat",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.path?.drop(1)).isEqualTo("/orgs/octocat/repos?per_page=30&page=1")
        }

        @Test
        fun `discoverRepositoriesOfOrg converts zero-based page to one-based page number in request`() {
            mockWebServer.enqueue(emptyReposResponse())

            runBlocking {
                githubClient.discoverRepositoriesOfOrg(
                    org = "octocat",
                    token = "test-token",
                    page = 2,
                    pageSize = 10,
                )
            }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.path).contains("page=3")
        }

        @Test
        fun `discoverRepositoriesOfOrg sends auth header`() {
            mockWebServer.enqueue(emptyReposResponse())

            runBlocking {
                githubClient.discoverRepositoriesOfOrg(
                    org = "octocat",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }

        @Test
        fun `discoverRepositoriesOfOrg throws WebClientException on non-2xx response`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"message":"Not Found"}"""),
            )

            assertThatThrownBy {
                runBlocking {
                    githubClient.discoverRepositoriesOfOrg(
                        org = "missing-user",
                        token = "test-token",
                        page = 0,
                        pageSize = 30,
                    )
                }
            }.isInstanceOf(WebClientException::class.java)
        }
    }

    @Nested
    inner class DiscoverRepositoriesOfUser {
        @Test
        fun `discoverRepositoriesOfUser returns empty list when user has no repos`() {
            mockWebServer.enqueue(emptyReposResponse())

            val result = runBlocking {
                githubClient.discoverRepositoriesOfUser(
                    user = "octocat",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            assertThat(result.repositories).isEmpty()
        }

        @Test
        fun `discoverRepositoriesOfUser returns repositories from response`() {
            mockWebServer.enqueue(reposResponse(listOf(repoJson(name = "repo-a"), repoJson(name = "repo-b"))))

            val result = runBlocking {
                githubClient.discoverRepositoriesOfUser(
                    user = "octocat",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            assertThat(result.repositories).hasSize(2)
            assertThat(result.repositories.map { it.name }).containsExactly("repo-a", "repo-b")
        }

        @Test
        fun `discoverRepositoriesOfUser requests correct path and query params`() {
            mockWebServer.enqueue(emptyReposResponse())

            runBlocking {
                githubClient.discoverRepositoriesOfUser(
                    user = "octocat",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.path?.drop(1)).isEqualTo("/users/octocat/repos?per_page=30&page=1")
        }

        @Test
        fun `discoverRepositoriesOfUser converts zero-based page to one-based page number in request`() {
            mockWebServer.enqueue(emptyReposResponse())

            runBlocking {
                githubClient.discoverRepositoriesOfUser(
                    user = "octocat",
                    token = "test-token",
                    page = 2,
                    pageSize = 10,
                )
            }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.path).contains("page=3")
        }

        @Test
        fun `discoverRepositoriesOfUser sends auth header`() {
            mockWebServer.enqueue(emptyReposResponse())

            runBlocking {
                githubClient.discoverRepositoriesOfUser(
                    user = "octocat",
                    token = "test-token",
                    page = 0,
                    pageSize = 30,
                )
            }

            val recorded = mockWebServer.takeRequest()
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }

        @Test
        fun `discoverRepositoriesOfUser throws WebClientException on non-2xx response`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"message":"Not Found"}"""),
            )

            assertThatThrownBy {
                runBlocking {
                    githubClient.discoverRepositoriesOfUser(
                        user = "missing-user",
                        token = "test-token",
                        page = 0,
                        pageSize = 30,
                    )
                }
            }.isInstanceOf(WebClientException::class.java)
        }
    }

    @Nested
    inner class OrganizationMetadata {
        @Test
        fun `getOrgMembers requests organization members and wraps response`() {
            mockWebServer.enqueue(orgMembersResponse(listOf(orgMemberJson("alice"), orgMemberJson("bob"))))

            val result = runBlocking { githubClient.getOrgMembers("octocat", "test-token") }

            assertThat(result.members.map { it.login }).containsExactly("alice", "bob")
            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/orgs/octocat/members?per_page=100&page=1")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }

        @Test
        fun `getOrgMembers fetches subsequent pages when the page is full`() {
            mockWebServer.enqueue(
                orgMembersResponse(List(100) { index -> orgMemberJson("member-$index") }),
            )
            mockWebServer.enqueue(orgMembersResponse(listOf(orgMemberJson("member-100"))))

            val result = runBlocking { githubClient.getOrgMembers("octocat", "test-token") }

            assertThat(result.members).hasSize(101)
            val firstPagePath = mockWebServer.takeRequest().path
            val secondPagePath = mockWebServer.takeRequest().path
            assertThat(firstPagePath).isEqualTo("/orgs/octocat/members?per_page=100&page=1")
            assertThat(secondPagePath).isEqualTo("/orgs/octocat/members?per_page=100&page=2")
        }

        @Test
        fun `findOrgMetadata requests organization endpoint and maps response`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                            "login": "octocat",
                            "name": "The Octocats",
                            "description": "A GitHub organization",
                            "company": "GitHub",
                            "blog": "https://github.blog",
                            "location": "San Francisco",
                            "email": "octocat@github.com",
                            "public_repos": 12,
                            "total_private_repos": 4
                        }
                        """.trimIndent(),
                    ),
            )

            val result = runBlocking { githubClient.fetchOrgMetadata("octocat", "test-token") }

            assertThat(result.login).isEqualTo("octocat")
            assertThat(result.publicRepos).isEqualTo(12)
            assertThat(result.privateRepos).isEqualTo(4)
            val request = mockWebServer.takeRequest()
            assertThat(request.path).isEqualTo("/orgs/octocat")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        }
    }

    @Nested
    inner class OrganizationTeams {
        @Test
        fun `getOrgTeams returns teams and paginates with organization and cursor variables`() {
            mockWebServer.enqueue(
                orgTeamsResponse(
                    teamName = "Platform",
                    hasNextPage = true,
                    cursor = "cursor-abc",
                    memberHasNextPage = true,
                    memberCursor = "member-cursor",
                ),
            )
            mockWebServer.enqueue(orgTeamsResponse(teamName = "Product", hasNextPage = false))
            mockWebServer.enqueue(teamMembersResponse(listOf(memberJson("carol")), hasNextPage = false))

            val result = runBlocking { githubClient.getOrgTeams("octocat", "test-token") }

            assertThat(result.map { it.name }).containsExactly("Platform", "Product")
            val firstTeam = result.first()
            val teamMemberLogins = firstTeam.members.nodes.map { it.login }
            assertThat(teamMemberLogins).containsExactly("alice", "carol")
            verify { queryLoader.load("github/graphql/org-teams.graphql") }
            verify { queryLoader.load("github/graphql/org-team-members.graphql") }

            val firstRequest = mockWebServer.takeRequest()
            assertThat(firstRequest.getHeader("Authorization")).isEqualTo("Bearer test-token")
            val firstBody = firstRequest.body.readUtf8()
            assertThat(firstBody).contains("\"org\":\"octocat\"")
            assertThat(firstBody).doesNotContain("cursor-abc")

            val secondRequest = mockWebServer.takeRequest()
            assertThat(secondRequest.body.readUtf8()).contains("cursor-abc")

            val memberRequest = mockWebServer.takeRequest()
            assertThat(memberRequest.body.readUtf8()).contains("member-cursor")
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

    private fun orgTeamsResponse(
        teamName: String,
        hasNextPage: Boolean,
        cursor: String? = null,
        memberHasNextPage: Boolean = false,
        memberCursor: String? = null,
    ) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
                "data": {
                    "organization": {
                        "login": "octocat",
                        "name": "The Octocats",
                        "teams": {
                            "nodes": [
                                {
                                    "name": "$teamName",
                                    "slug": "${teamName.lowercase()}",
                                    "organization": {
                                        "login": "octocat",
                                        "name": "The Octocats"
                                    },
                                    "members": {
                                        "nodes": [
                                            { "login": "alice", "name": "Alice" }
                                        ],
                                        "pageInfo": {
                                            "hasNextPage": $memberHasNextPage,
                                            "endCursor": ${if (memberCursor != null) "\"$memberCursor\"" else "null"}
                                        }
                                    }
                                }
                            ],
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

    private fun orgMembersResponse(members: List<String>) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("[${members.joinToString(",")}]")

    private fun orgMemberJson(login: String) =
        """
        { "login": "$login", "html_url": "https://github.com/$login" }
        """.trimIndent()

    private fun memberJson(login: String) =
        """
        { "login": "$login", "name": "${login.replaceFirstChar { it.uppercase() }}" }
        """.trimIndent()

    private fun teamMembersResponse(
        members: List<String>,
        hasNextPage: Boolean,
        cursor: String? = null,
    ) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
                "data": {
                    "organization": {
                        "team": {
                            "members": {
                                "nodes": [${members.joinToString(",")}],
                                "pageInfo": {
                                    "hasNextPage": $hasNextPage,
                                    "endCursor": ${if (cursor != null) "\"$cursor\"" else "null"}
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
        )

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

    private fun emptyReposResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("[]")

    private fun reposResponse(repoJsons: List<String>): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("[${repoJsons.joinToString(",")}]")

    private fun repoJson(
        id: Long = 1,
        name: String = "repo",
        fullName: String = "owner/$name",
    ): String =
        """
        {
          "id": $id,
          "name": "$name",
          "full_name": "$fullName",
          "private": false,
          "html_url": "https://github.com/$fullName"
        }
        """.trimIndent()
}
