package com.sprintstart.sprintstartbackend.connectors.git.github

import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.PageableResponse
import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * The transport of GitHub's GraphQL endpoint: posting one query, and walking a paginated one to its end.
 *
 * The properties are not private because the inline functions below are expanded into their callers.
 */
internal class GithubGraphql(
    val webClient: WebClient,
    val applicationConfig: ApplicationConfig,
) {
    val objectMapper = jacksonObjectMapper()

    /** Posts [query] with [variables] on behalf of [token] and decodes the response as [T]. */
    suspend inline fun <reified T> post(query: String, variables: Map<String, Any?>, token: String): T {
        val body = mapOf("query" to query, "variables" to variables)

        return webClient
            .post()
            .uri(applicationConfig.github.baseUrl + "/graphql")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .rawBody(objectMapper.writeValueAsString(body))
            .sync()
            .perform<T>()
    }

    /**
     * Fetches all paginated data using the provided GraphQL query and variables.
     *
     * This method sends a GraphQL request repeatedly until all pages of data have been fetched
     * by leveraging the pagination information available in the response.
     *
     * @param query the GraphQL query that fetches the data.
     * @param token the GitHub PAT used for authentication.
     * @param variablesBuilder a function that builds the variables map for the query. The function takes
     * a cursor as input and returns a map of variables. The cursor pages through
     * the paginated results.
     * @return a list of all fetched entities of type [S].
     */
    suspend inline fun <S, reified T : PageableResponse<S>> fetchAll(
        query: String,
        token: String,
        initialCursor: String? = null,
        variablesBuilder: (cursor: String?) -> Map<String, Any?>,
    ): List<S> {
        val entities = mutableListOf<S>()
        var cursor: String? = initialCursor

        do {
            val response = post<T>(query, variablesBuilder(cursor).filterValues { it != null }, token)

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
