package com.sprintstart.sprintstartbackend.github

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.github.models.client.Commit
import com.sprintstart.sprintstartbackend.github.models.client.FileResponse
import com.sprintstart.sprintstartbackend.github.models.client.FileTreeResponse
import com.sprintstart.sprintstartbackend.github.models.client.GithubCommitsResponse
import com.sprintstart.sprintstartbackend.github.models.client.GithubIssuesResponse
import com.sprintstart.sprintstartbackend.github.models.client.GithubPullRequestsResponse
import com.sprintstart.sprintstartbackend.github.models.client.Issue
import com.sprintstart.sprintstartbackend.github.models.client.PageableResponse
import com.sprintstart.sprintstartbackend.github.models.client.PullRequest
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonObjectMapper

@Component
class GithubClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    private val objectMapper = jacksonObjectMapper()

    suspend fun fetchFileTree(owner: String, name: String): FileTreeResponse {
        val uri = "https://api.github.com/repos/$owner/$name/git/trees/HEAD?recursive=1"
        return webClient
            .get()
            .uri(uri)
            .sync()
            .perform<FileTreeResponse>()
    }

    suspend fun fetchFile(owner: String, name: String, path: String): FileResponse {
        val uri = "https://api.github.com/repos/$owner/$name/contents/$path"
        return webClient
            .get()
            .uri(uri)
            .sync()
            .perform<FileResponse>()
    }

    suspend fun fetchAllCommits(owner: String, name: String): List<Commit> {
        val query = ClassPathResource("github/graphql/100-commits.graphql")
            .inputStream
            .bufferedReader()
            .readText()
        return doFetchAll<Commit, GithubCommitsResponse>(owner, name, query)
    }

    suspend fun fetchAllIssues(owner: String, name: String): List<Issue> {
        val query = ClassPathResource("github/graphql/100-issues.graphql")
            .inputStream
            .bufferedReader()
            .readText()
        return doFetchAll<Issue, GithubIssuesResponse>(owner, name, query)
    }

    suspend fun fetchAllPullRequests(owner: String, name: String): List<PullRequest> {
        val query = ClassPathResource("github/graphql/100-pullrequests.graphql")
            .inputStream
            .bufferedReader()
            .readText()
        return doFetchAll<PullRequest, GithubPullRequestsResponse>(owner, name, query)
    }

    private suspend inline fun <S, reified T : PageableResponse<S>> doFetchAll(
        owner: String,
        name: String,
        query: String,
    ): List<S> {
        val entities = mutableListOf<S>()
        var cursor: String? = null

        do {
            val body = mapOf(
                "query" to query,
                "variables" to mapOf(
                    "owner" to owner,
                    "name" to name,
                    "cursor" to cursor,
                ).filterValues { it != null },
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
