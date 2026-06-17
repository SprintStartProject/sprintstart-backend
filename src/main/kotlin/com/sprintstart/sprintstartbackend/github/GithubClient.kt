package com.sprintstart.sprintstartbackend.github

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.github.models.client.AiIngestRequest
import com.sprintstart.sprintstartbackend.github.models.client.AiIngestResponse
import com.sprintstart.sprintstartbackend.github.models.client.graphql.GithubIssuesResponse
import com.sprintstart.sprintstartbackend.github.models.client.graphql.GithubPrSearchResponse
import com.sprintstart.sprintstartbackend.github.models.client.graphql.GithubSinglePrResponse
import com.sprintstart.sprintstartbackend.github.models.client.graphql.Issue
import com.sprintstart.sprintstartbackend.github.models.client.graphql.PageableResponse
import com.sprintstart.sprintstartbackend.github.models.client.graphql.PrNode
import com.sprintstart.sprintstartbackend.github.models.client.graphql.PullRequest
import com.sprintstart.sprintstartbackend.github.models.exceptions.IngestionResponseException
import com.sprintstart.sprintstartbackend.github.util.GithubQueryLoader
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
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
@Component
class GithubClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
    private val queryLoader: GithubQueryLoader,
) {
    private val objectMapper = jacksonObjectMapper()

    /**
     * TEMP: Patches AI ingestion directly into the GitHub module, for time sake.
     *
     * ABSOLUTELY NOT GOOD PRACTICE.
     *
     * NEEDS TO BE REFACTORED INTO A CLEAN INGESTION MODULE.
     */
    suspend fun ingest(
        body: AiIngestRequest,
    ): AiIngestResponse =
        try {
            val baseUrl = applicationConfig.ai.baseUrl
            webClient
                .post()
                .uri("$baseUrl/api/v1/ingest")
                .body(body)
                .sync()
                .perform<AiIngestResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw IngestionResponseException("Failed to ingest github resource (HTTP ${e.statusCode}): ${e.body}")
        }

    /**
     * Checks if a repository exists on GitHub.
     *
     * @param owner the username or organization name of the repository owner
     * @param name the name of the repository
     * @return true if the repository exists, false otherwise
     * @throws WebClientException if the server returns a non-2xx status
     * @throws kotlinx.serialization.SerializationException if the response body cannot be deserialized
     */
    @Suppress("MagicNumber")
    suspend fun repositoryExists(owner: String, name: String): Boolean {
        val baseUrl = applicationConfig.github.repoBaseUrl
        return try {
            webClient
                .get()
                .uri("$baseUrl/$owner/$name")
                .header("Authorization", "Bearer ${applicationConfig.github.token}")
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
     * Fetches issues from a GitHub repository.
     *
     * This method retrieves all issues for the specified repository. If a `sinceTimestamp`
     * is provided, only issues updated since the given timestamp are fetched.
     *
     * @param owner the username or organization name of the repository owner.
     * @param name the name of the repository.
     * @param sinceTimestamp an optional ISO 8601 formatted timestamp string. When provided, only
     * issues updated since this timestamp will be fetched.
     * @return a list of issues associated with the specified repository.
     * @throws WebClientException if there is an issue with the network or server response.
     * @throws kotlinx.serialization.SerializationException if the response data cannot be deserialized.
     */
    suspend fun fetchIssues(owner: String, name: String, sinceTimestamp: String? = null): List<Issue> {
        return if (sinceTimestamp != null) {
            fetchAllIssuesSince(owner, name, sinceTimestamp)
        } else {
            fetchAllIssues(owner, name)
        }
    }

    /**
     * Fetches all issues from a GitHub repository.
     *
     * This method retrieves all the issues for the specified repository, traversing through
     * paginated results to collect all available issues.
     *
     * @param owner the username or organization name of the repository owner.
     * @param name the name of the repository.
     * @return a list of issues associated with the specified repository.
     */
    private suspend fun fetchAllIssues(owner: String, name: String): List<Issue> {
        val query = queryLoader.load("github/graphql/100-issues.graphql")

        return doFetchAll<Issue, GithubIssuesResponse>(query) { cursor ->
            mapOf("owner" to owner, "name" to name, "cursor" to cursor)
        }
    }

    /**
     * Fetches all issues from a GitHub repository updated since a specific timestamp.
     *
     * This method retrieves all the issues for the specified repository that have been updated
     * on or after the given timestamp. It traverses through paginated results to collect all
     * available issues.
     *
     * @param owner the username or organization name of the repository owner.
     * @param name the name of the repository.
     * @param sinceTimestamp an ISO 8601 formatted timestamp string. Only issues updated
     * after this timestamp will be fetched.
     * @return a list of issues associated with the specified repository that have been updated
     * since the given timestamp.
     */
    private suspend fun fetchAllIssuesSince(owner: String, name: String, sinceTimestamp: String): List<Issue> {
        val query = queryLoader.load("github/graphql/issues-since.graphql")

        return doFetchAll<Issue, GithubIssuesResponse>(query) { cursor ->
            mapOf("owner" to owner, "name" to name, "cursor" to cursor, "since" to sinceTimestamp)
        }
    }

    /**
     * Fetches all pull requests from a GitHub repository.
     *
     * This method retrieves all pull requests for the specified repository, optionally filtering by
     * a provided timestamp. If a `sinceTimestamp` is specified, only pull requests updated on or
     * after that timestamp will be retrieved.
     *
     * @param owner the username or organization name of the repository owner.
     * @param name the name of the repository.
     * @param sinceTimestamp an optional ISO 8601 formatted timestamp string. When provided, only
     * pull requests updated since this timestamp will be fetched.
     * @return a list of pull requests associated with the specified repository.
     * @throws WebClientException if there is an issue with the network or server response.
     * @throws kotlinx.serialization.SerializationException if the response data cannot be deserialized.
     */
    suspend fun fetchAllPullRequests(owner: String, name: String, sinceTimestamp: String? = null): List<PullRequest> {
        val listQuery = queryLoader.load("github/graphql/pullrequests-since.graphql")

        val searchQueryString = buildString {
            append("repo:$owner/$name is:pr")
            if (sinceTimestamp != null) {
                append(" updated:>=$sinceTimestamp")
            }
        }

        val prNumbers = doFetchAll<PrNode, GithubPrSearchResponse>(listQuery) { cursor ->
            mapOf("searchQuery" to searchQueryString, "cursor" to cursor)
        }.map { it.number }

        val detailsQuery = queryLoader.load("github/graphql/100-pullrequests-deep.graphql")

        val pullRequests = mutableListOf<PullRequest>()

        for (prNumber in prNumbers) {
            val prDetails = fetchSinglePullRequest(owner, name, prNumber, detailsQuery)
            if (prDetails != null) {
                pullRequests.add(prDetails)
            }
        }

        return pullRequests
    }

    /**
     * Fetches a single pull request from a GitHub repository.
     *
     * This method queries the GitHub API to retrieve details of a specific pull request
     * identified by its number within the specified repository.
     *
     * @param owner the username or organization name of the repository owner.
     * @param name the name of the repository.
     * @param prNumber the number of the pull request to fetch.
     * @param query the GraphQL query used to retrieve the pull request data.
     * @return the details of the requested pull request as a [PullRequest] object, or null if
     *         the pull request does not exist or the API response is incomplete.
     */
    private suspend fun fetchSinglePullRequest(
        owner: String,
        name: String,
        prNumber: Int,
        query: String,
    ): PullRequest? {
        val body = mapOf(
            "query" to query,
            "variables" to mapOf(
                "owner" to owner,
                "name" to name,
                "prNumber" to prNumber,
            ),
        )

        val response = webClient
            .post()
            .uri(applicationConfig.github.baseUrl)
            .header("Authorization", "Bearer ${applicationConfig.github.token}")
            .header("Content-Type", "application/json")
            .rawBody(objectMapper.writeValueAsString(body))
            .sync()
            .perform<GithubSinglePrResponse>()

        return response.pullRequest
    }

    /**
     * Fetches all paginated data using the provided GraphQL query and variables.
     *
     * This method sends a GraphQL request repeatedly until all pages of data have been fetched
     * by leveraging the pagination information available in the response.
     *
     * @param query the GraphQL query used to fetch data.
     * @param variablesBuilder a function that builds the variables map for the query. The function takes
     * a cursor as input and returns a map of variables. The cursor is used to navigate through
     * the paginated results.
     * @return a list of all fetched entities of type [S].
     */
    private suspend inline fun <S, reified T : PageableResponse<S>> doFetchAll(
        query: String,
        variablesBuilder: (cursor: String?) -> Map<String, Any?>,
    ): List<S> {
        val entities = mutableListOf<S>()
        var cursor: String? = null

        do {
            val body = mapOf(
                "query" to query,
                "variables" to variablesBuilder(cursor).filterValues { it != null },
            )

            val response = webClient
                .post()
                .uri(applicationConfig.github.baseUrl)
                .header("Authorization", "Bearer ${applicationConfig.github.token}")
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
}
