package com.sprintstart.sprintstartbackend.github

import com.fasterxml.jackson.databind.ObjectMapper
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.github.models.client.Commit
import com.sprintstart.sprintstartbackend.github.models.client.GithubCommitsResponse
import com.sprintstart.sprintstartbackend.github.models.client.GithubIssuesResponse
import com.sprintstart.sprintstartbackend.github.models.client.Issue
import com.sprintstart.sprintstartbackend.github.models.client.PageableResponse
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import kotlinx.coroutines.coroutineScope
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import kotlinx.coroutines.async
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.collections.mapOf

@Component
class GithubClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    private val objectMapper = jacksonObjectMapper()

    suspend fun connectGithub(owner: String, name: String) {
        coroutineScope {
            val commits = async { fetchAndIngestAllGithubCommits(owner, name) }
            val issues = async { fetchAndIngestAllIssues(owner, name) }

            commits.await()
            issues.await()
        }
    }

    private suspend fun fetchAndIngestAllGithubCommits(owner: String, name: String) {
        val query = ClassPathResource("github/graphql/last-100-commits.graphql")
            .inputStream
            .bufferedReader()
            .readText()
        val response = doFetchAll<Commit, GithubCommitsResponse>(owner, name, query)

        println("Issues: ${objectMapper.writeValueAsString(response)}")
    }

    private suspend fun fetchAndIngestAllIssues(owner: String, name: String) {
        val query = ClassPathResource("github/graphql/last-100-issues.graphql")
            .inputStream
            .bufferedReader()
            .readText()

        val response = doFetchAll<Issue, GithubIssuesResponse>(owner, name, query)

        println("Issues: ${objectMapper.writeValueAsString(response)}")
    }

    private suspend inline fun <S, reified T : PageableResponse<S>> doFetchAll(owner: String, name: String, query: String): List<S> {
        val entities = mutableListOf<S>()
        var cursor: String? = null

        do {
            val body = mapOf(
                "query" to query,
                "variables" to mapOf(
                    "owner" to owner,
                    "name" to name,
                    "cursor" to cursor
                ).filterValues { it != null }
            )

            println("Body is $body")

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