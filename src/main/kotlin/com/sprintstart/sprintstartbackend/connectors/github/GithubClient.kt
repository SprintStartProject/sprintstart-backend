package com.sprintstart.sprintstartbackend.connectors.github

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoverRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoveredRepository
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.PullRequestFileResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMemberResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMembersResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMetadataResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.GithubIssuesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.GithubPrSearchResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.GithubSinglePrResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.Issue
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.Member
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.MemberConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.OrganizationTeamMembersResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.OrganizationTeamsResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PageableResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PrNode
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PullRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.Team
import com.sprintstart.sprintstartbackend.connectors.github.util.GithubQueryLoader
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Handles interactions with the GitHub API, including repository existence checks,
 * issue fetching, and pull request fetching.
 *
 * This class uses [WebClient] for making HTTP requests, leverages a GitHub-specific
 * query loader for GraphQL queries, and relies on application configuration parameters.
 *
 * Primary functionality includes:
 * - Repository existence validation.
 * - Fetching issues with optional time constraints.
 * - Fetching pull requests with optional time constraints.
 *
 * @constructor Initializes the GitHub client with dependencies for HTTP requests,
 * configuration values, and GraphQL query resolutions.
 *
 * @param webClient A component for making HTTP requests to the GitHub API.
 * @param applicationConfig Application-level configuration parameters, including GitHub-specific configurations.
 * @param queryLoader Responsible for loading pre-defined GitHub GraphQL queries.
 */
@Suppress("TooManyFunctions")
@Component
// One function per GitHub resource this app reads, plus the paging helpers underneath them. Splitting
// by resource would put two or three methods in each of four classes that all share the same
// WebClient, base URL and error translation.
@Suppress("TooManyFunctions")
class GithubClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
    private val queryLoader: GithubQueryLoader,
) {
    private val objectMapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(GithubClient::class.java)

    /**
     * Retrieves the list of members belonging to the specified GitHub organization.
     *
     * @param org The name of the GitHub organization whose members are to be fetched.
     * @param token The authorization token to authenticate the request.
     * @return The response containing the members of the specified organization.
     * @throws WebClientException if there is an issue with the network or server response, such as a non-2xx status
     * code.
     * @throws kotlinx.serialization.SerializationException if the response body cannot be deserialized.
     */
    suspend fun getOrgMembers(org: String, token: String): OrgMembersResponse {
        val baseUrl = "${applicationConfig.github.baseUrl.trimEnd('/')}/orgs/$org/members"
        val members = mutableListOf<OrgMemberResponse>()
        var page = 1

        do {
            val pageMembers = webClient
                .get()
                .uri("$baseUrl?per_page=100&page=$page")
                .header("Authorization", "Bearer $token")
                .sync()
                .perform<Array<OrgMemberResponse>>()

            members += pageMembers
            page++
        } while (pageMembers.size == 100)

        return OrgMembersResponse(members = members)
    }

    /**
     * Retrieves all teams belonging to the specified GitHub organization.
     *
     * The team connection is traversed using GitHub's cursor-based GraphQL pagination.
     * Team members included in each page are returned as part of each [Team].
     *
     * @param org the name of the GitHub organization whose teams are fetched.
     * @param token the personal access token used to authenticate the request.
     * @return all teams belonging to the organization.
     */
    suspend fun getOrgTeams(org: String, token: String): List<Team> {
        val query = queryLoader.load("github/graphql/org-teams.graphql")

        val teams = doFetchAll<Team, OrganizationTeamsResponse>(query, token) { cursor ->
            mapOf("org" to org, "cursor" to cursor)
        }

        return teams.map { team ->
            if (!team.members.pageInfo.hasNextPage) {
                team
            } else {
                team.copy(
                    members = MemberConnection(
                        nodes = fetchAllTeamMembers(org, team, token),
                        pageInfo = team.members.pageInfo.copy(hasNextPage = false, endCursor = null),
                    ),
                )
            }
        }
    }

    /**
     * Fetches metadata of a GitHub organization.
     *
     * This method retrieves metadata information for the specified organization by querying the GitHub API.
     *
     * @param org the name of the GitHub organization whose metadata will be fetched.
     * @param token the personal access token (PAT) used to authenticate the request to the GitHub API.
     * @return an [OrgMetadataResponse] object containing metadata details of the specified organization.
     * @throws WebClientException if there is an issue with the network or server response, such as a non-2xx status
     * code.
     * @throws kotlinx.serialization.SerializationException if the response body cannot be deserialized.
     */
    suspend fun fetchOrgMetadata(org: String, token: String): OrgMetadataResponse {
        val url = "${applicationConfig.github.baseUrl.trimEnd('/')}/orgs/$org"
        return webClient
            .get()
            .uri(url)
            .header("Authorization", "Bearer $token")
            .sync()
            .perform<OrgMetadataResponse>()
    }

    /**
     * Checks whether the given GitHub login refers to an organization.
     *
     * The `/orgs/{org}` endpoint only resolves for organizations; for user accounts it responds
     * with 404. This method therefore treats a 404 as "not an organization" and rethrows any
     * other non-2xx status.
     *
     * @param org the GitHub login to check.
     * @param token the personal access token used to authenticate the request.
     * @return true if the login is a GitHub organization, false otherwise.
     * @throws WebClientException if the request fails with a status other than 404.
     */
    suspend fun isOrganization(org: String, token: String): Boolean {
        val url = "${applicationConfig.github.baseUrl.trimEnd('/')}/orgs/$org"
        return try {
            webClient
                .get()
                .uri(url)
                .header("Authorization", "Bearer $token")
                .sync()
                .performRaw()
            true
        } catch (e: WebClientException) {
            if (e.statusCode == 404) {
                false
            } else {
                throw e
            }
        }
    }

    /**
     * Checks if a repository exists on GitHub.
     *
     * @param repository the repository to check for existence
     * @return true if the repository exists, false otherwise
     * @throws WebClientException if the server returns a non-2xx status
     * @throws kotlinx.serialization.SerializationException if the response body cannot be deserialized
     */
    suspend fun repositoryExists(repository: GithubRepositoryConnection): Boolean {
        val baseUrl = applicationConfig.github.baseUrl + "/repos"
        return try {
            webClient
                .get()
                .uri("$baseUrl/${repository.owner}/${repository.name}")
                .header("Authorization", "Bearer ${repository.user.token}")
                .sync()
                .performRaw()
            true // 2xx means it exists
        } catch (e: WebClientException) {
            if (e.statusCode == 404) {
                false
            } else {
                throw e // propagate unexpected errors
            }
        }
    }

    /**
     * Whether a GitHub account with this login exists — or `null` when GitHub would not say.
     *
     * Three-valued, and unauthenticated — no token is threaded through here. The cost is
     * GitHub's unauthenticated rate limit (60/hour per IP), affordable only because the answer is
     * *stored* against the user and re-checked when their login changes or a previous check could
     * not run.
     *
     * Only a 404 means "no such account". A rate limit, a 5xx or a dropped connection all
     * return null, never false — an outage is not evidence about the world.
     *
     * @return true when it exists, false when GitHub says it does not, null when GitHub would not
     * answer.
     */
    suspend fun userExists(login: String): Boolean? {
        return try {
            webClient
                .get()
                .uri("${applicationConfig.github.baseUrl}/users/$login")
                .sync()
                .performRaw()
            true
        } catch (e: WebClientException) {
            if (e.statusCode == 404) false else null
        }
    }

    /**
     * Fetches issues from a GitHub repository.
     *
     * This method retrieves all issues for the specified repository. If a `sinceTimestamp`
     * is provided, only issues updated since the given timestamp are fetched.
     *
     * @param repository the repository to fetch issues from.
     * @param sinceTimestamp an optional ISO 8601 formatted timestamp string. When provided, only
     * issues updated since this timestamp will be fetched.
     * @return a list of issues associated with the specified repository.
     * @throws WebClientException if there is an issue with the network or server response.
     * @throws kotlinx.serialization.SerializationException if the response data cannot be deserialized.
     */
    suspend fun fetchIssues(repository: GithubRepositoryConnection, sinceTimestamp: String? = null): List<Issue> {
        return if (sinceTimestamp != null) {
            fetchAllIssuesSince(repository, sinceTimestamp)
        } else {
            fetchAllIssues(repository)
        }
    }

    /**
     * Fetches all pull requests from a GitHub repository.
     *
     * This method retrieves all pull requests for the specified repository, optionally filtering by
     * a provided timestamp. If a `sinceTimestamp` is specified, only pull requests updated on or
     * after that timestamp will be retrieved.
     *
     * @param repository the repository to fetch pull requests from.
     * @param sinceTimestamp an optional ISO 8601 formatted timestamp string. When provided, only
     * pull requests updated since this timestamp will be fetched.
     * @return a list of pull requests associated with the specified repository.
     * @throws WebClientException if there is an issue with the network or server response.
     * @throws kotlinx.serialization.SerializationException if the response data cannot be deserialized.
     */
    suspend fun fetchAllPullRequests(
        repository: GithubRepositoryConnection,
        sinceTimestamp: String? = null,
    ): List<PullRequest> {
        val listQuery = queryLoader.load("github/graphql/pullrequests-since.graphql")

        val searchQueryString = buildString {
            append("repo:${repository.owner}/${repository.name} is:pr")
            if (sinceTimestamp != null) {
                append(" updated:>=$sinceTimestamp")
            }
        }

        val prNumbers = doFetchAll<PrNode, GithubPrSearchResponse>(listQuery, repository.user.token) { cursor ->
            mapOf("searchQuery" to searchQueryString, "cursor" to cursor)
        }.map { it.number }

        val detailsQuery = queryLoader.load("github/graphql/100-pullrequests-deep.graphql")

        val pullRequests = mutableListOf<PullRequest>()

        for (prNumber in prNumbers) {
            val prDetails = fetchSinglePullRequest(repository, prNumber, detailsQuery)
            if (prDetails != null) {
                pullRequests.add(prDetails)
            }
        }

        return pullRequests
    }

    /**
     * Fetches one pull request's full detail (title, body, state, changed files, CI status,
     * commit messages) on demand, by number -- unlike [fetchAllPullRequests], this doesn't crawl
     * a repository's whole PR list first. Used by artifact verification to gather live evidence
     * for a hire-submitted PR number at grading time.
     *
     * @param repository the repository containing the pull request.
     * @param prNumber the number of the pull request to fetch.
     * @return the pull request's details, or null if it does not exist.
     * @throws WebClientException if there is an issue with the network or server response.
     * @throws kotlinx.serialization.SerializationException if the response data cannot be deserialized.
     */
    suspend fun fetchPullRequest(repository: GithubRepositoryConnection, prNumber: Int): PullRequest? {
        val query = queryLoader.load("github/graphql/100-pullrequests-deep.graphql")
        return fetchSinglePullRequest(repository, prNumber, query)
    }

    /**
     * The changed files of one pull request, with their diffs.
     *
     * A second call, and it cannot be joined into the GraphQL one: GraphQL's
     * `PullRequestChangedFile` has a path and counts but no patch. Patch text is REST-only.
     *
     * Capped at [MAX_FILES_PER_PAGE] files, one page.
     *
     * Returns an empty list rather than throwing when GitHub will not answer: an unavailable
     * diff is not evidence of an empty one, and the caller says so to the model rather than
     * failing somebody's work on a network error.
     */
    suspend fun fetchPullRequestFiles(
        repository: GithubRepositoryConnection,
        prNumber: Int,
    ): List<PullRequestFileResponse> {
        val uri = "${applicationConfig.github.baseUrl}/repos/${repository.owner}/${repository.name}" +
            "/pulls/$prNumber/files?per_page=$MAX_FILES_PER_PAGE"
        return try {
            webClient
                .get()
                .uri(uri)
                .header("Authorization", "Bearer ${repository.user.token}")
                .sync()
                .perform<Array<PullRequestFileResponse>>()
                .toList()
        } catch (e: WebClientException) {
            logger.warn("Could not read the diff of pull request #{}: {}", prNumber, e.message)
            emptyList()
        }
    }

    /**
     * Discovers repositories of a given GitHub organization.
     *
     * This method fetches the list of repositories belonging to the specified GitHub organization by
     * querying the GitHub API. Authentication is performed using the provided personal access token (PAT).
     *
     * @param org the name of the GitHub organization whose repositories are to be discovered.
     * @param token the personal access token (PAT) used for authenticating the request to the GitHub API.
     * @return a [DiscoverRepositoriesResponse] object containing the list of repositories belonging to the
     * organization.
     * @throws WebClientException if there is an issue with the network or server response, such as a non-2xx status
     * code.
     */
    suspend fun discoverRepositoriesOfOrg(
        org: String,
        token: String,
        page: Int,
        pageSize: Int,
    ): DiscoverRepositoriesResponse {
        val uri = "${applicationConfig.github.baseUrl}/orgs/$org/repos?per_page=$pageSize&page=${page + 1}"
        return discoverRepositories(uri, token)
    }

    /**
     * Discovers repositories of a given GitHub user, authenticating with [token].
     *
     * @param page the zero-based index of the page to fetch.
     * @throws WebClientException on a network problem or a non-2xx status code.
     */
    suspend fun discoverRepositoriesOfUser(
        user: String,
        token: String,
        page: Int,
        pageSize: Int,
    ): DiscoverRepositoriesResponse {
        val uri = "${applicationConfig.github.baseUrl}/users/$user/repos?per_page=$pageSize&page=${page + 1}"
        return discoverRepositories(uri, token)
    }

    private suspend fun fetchAllTeamMembers(org: String, team: Team, token: String): List<Member> {
        val query = queryLoader.load("github/graphql/org-team-members.graphql")
        val initialMembers = team.members.nodes

        return initialMembers + doFetchAll<Member, OrganizationTeamMembersResponse>(
            query = query,
            token = token,
            initialCursor = team.members.pageInfo.endCursor,
        ) { cursor ->
            mapOf("org" to org, "slug" to team.slug, "cursor" to cursor)
        }
    }

    private suspend fun discoverRepositories(uri: String, token: String): DiscoverRepositoriesResponse {
        val result = webClient
            .get()
            .uri(uri)
            .header("Authorization", "Bearer $token")
            .sync()
            .perform<Array<DiscoveredRepository>>()
        return DiscoverRepositoriesResponse(result.toList())
    }

    /**
     * Fetches all issues from a GitHub repository.
     *
     * This method retrieves all the issues for the specified repository, traversing through
     * paginated results to collect all available issues.
     *
     * @param repository the repository to fetch issues from.
     * @return a list of issues associated with the specified repository.
     */
    private suspend fun fetchAllIssues(repository: GithubRepositoryConnection): List<Issue> {
        val query = queryLoader.load("github/graphql/100-issues.graphql")

        return doFetchAll<Issue, GithubIssuesResponse>(query, repository.user.token) { cursor ->
            mapOf("owner" to repository.owner, "name" to repository.name, "cursor" to cursor)
        }
    }

    /**
     * Fetches all issues from a GitHub repository updated since a specific timestamp.
     *
     * This method retrieves all the issues for the specified repository that have been updated
     * on or after the given timestamp. It traverses through paginated results to collect all
     * available issues.
     *
     * @param repository the repository to fetch issues from.
     * @param sinceTimestamp an ISO 8601 formatted timestamp string. Only issues updated
     * after this timestamp will be fetched.
     * @return a list of issues associated with the specified repository that have been updated
     * since the given timestamp.
     */
    private suspend fun fetchAllIssuesSince(
        repository: GithubRepositoryConnection,
        sinceTimestamp: String,
    ): List<Issue> {
        val query = queryLoader.load("github/graphql/issues-since.graphql")

        return doFetchAll<Issue, GithubIssuesResponse>(query, repository.user.token) { cursor ->
            mapOf("owner" to repository.owner, "name" to repository.name, "cursor" to cursor, "since" to sinceTimestamp)
        }
    }

    /**
     * Fetches a single pull request from a GitHub repository.
     *
     * @return the pull request, or null if it does not exist or the API response is incomplete.
     */
    private suspend fun fetchSinglePullRequest(
        repository: GithubRepositoryConnection,
        prNumber: Int,
        query: String,
    ): PullRequest? {
        val body = mapOf(
            "query" to query,
            "variables" to mapOf(
                "owner" to repository.owner,
                "name" to repository.name,
                "prNumber" to prNumber,
            ),
        )

        val response = webClient
            .post()
            .uri(applicationConfig.github.baseUrl + "/graphql")
            .header("Authorization", "Bearer ${repository.user.token}")
            .header("Content-Type", "application/json")
            .rawBody(objectMapper.writeValueAsString(body))
            .sync()
            .perform<GithubSinglePrResponse>()

        return response.pullRequest
    }

    /**
     * Sends [query] repeatedly until every page has been fetched, following the pagination
     * information in each response.
     *
     * @param variablesBuilder Builds the variables map for one page; the cursor it is given is null
     * for the first page and the previous response's end cursor thereafter.
     */
    private suspend inline fun <S, reified T : PageableResponse<S>> doFetchAll(
        query: String,
        token: String,
        initialCursor: String? = null,
        variablesBuilder: (cursor: String?) -> Map<String, Any?>,
    ): List<S> {
        val entities = mutableListOf<S>()
        var cursor: String? = initialCursor

        do {
            val body = mapOf(
                "query" to query,
                "variables" to variablesBuilder(cursor).filterValues { it != null },
            )

            val response = webClient
                .post()
                .uri(applicationConfig.github.baseUrl + "/graphql")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .rawBody(objectMapper.writeValueAsString(body))
                .sync()
                .perform<T>()

            entities.addAll(response.results)

            cursor = if (response.hasNextPage) {
                response.endCursor
            } else {
                null
            }
        } while (cursor != null)

        return entities
    }

    private companion object {
        // One page. A pull request touching more files than this is not a starter task, and paging
        // it would spend a hire's verification latency gathering evidence nobody should read whole.
        const val MAX_FILES_PER_PAGE = 100
    }
}
