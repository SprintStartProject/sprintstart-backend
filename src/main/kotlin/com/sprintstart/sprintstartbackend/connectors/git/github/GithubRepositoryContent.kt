package com.sprintstart.sprintstartbackend.connectors.git.github

import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.GithubIssuesResponse
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.GithubPrSearchResponse
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.GithubSinglePrResponse
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.Issue
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.PrNode
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.PullRequest
import com.sprintstart.sprintstartbackend.connectors.git.github.util.GithubQueryLoader

/** Fetches what a connected repository holds that is worth ingesting: its issues and its pull requests. */
internal class GithubRepositoryContent(
    private val graphql: GithubGraphql,
    private val queryLoader: GithubQueryLoader,
) {
    /**
     * Fetches the issues of [repository], all of them or only those updated on or after [sinceTimestamp]
     * (an ISO 8601 instant), traversing the paginated result.
     */
    suspend fun fetchIssues(repository: GithubRepositoryConnection, sinceTimestamp: String? = null): List<Issue> {
        val query = queryLoader.load(
            if (sinceTimestamp == null) "github/graphql/100-issues.graphql" else "github/graphql/issues-since.graphql",
        )

        return graphql.fetchAll<Issue, GithubIssuesResponse>(query, repository.user.token) { cursor ->
            val variables = mapOf("owner" to repository.owner, "name" to repository.name, "cursor" to cursor)
            if (sinceTimestamp == null) variables else variables + ("since" to sinceTimestamp)
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

        val prs = graphql.fetchAll<PrNode, GithubPrSearchResponse>(listQuery, repository.user.token) { cursor ->
            mapOf("searchQuery" to searchQueryString, "cursor" to cursor)
        }
        val prNumbers = prs.map { it.number }

        val detailsQuery = queryLoader.load("github/graphql/100-pullrequests-deep.graphql")

        return prNumbers.mapNotNull { prNumber -> fetchSinglePullRequest(repository, prNumber, detailsQuery) }
    }

    /**
     * Fetches a single pull request from a GitHub repository, or null if the pull request does not
     * exist or the API response is incomplete.
     */
    private suspend fun fetchSinglePullRequest(
        repository: GithubRepositoryConnection,
        prNumber: Int,
        query: String,
    ): PullRequest? {
        val variables = mapOf("owner" to repository.owner, "name" to repository.name, "prNumber" to prNumber)

        return graphql.post<GithubSinglePrResponse>(query, variables, repository.user.token).pullRequest
    }
}
