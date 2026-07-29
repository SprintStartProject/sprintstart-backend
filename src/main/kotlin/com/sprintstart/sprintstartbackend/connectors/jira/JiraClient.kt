package com.sprintstart.sprintstartbackend.connectors.jira

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueResponse
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraProjectResponse
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraServerCapabilitiesResponse
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredential
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraAuthException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraResourceNotFoundException
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.util.Base64

@Component
internal class JiraClient(
    private val webClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Searches for issues in a Jira instance based on a JQL query and specified fields.
     *
     * @param baseUrl The base URL of the Jira instance.
     * @param credentials The credentials required to authenticate with the Jira instance.
     * @param jql The JQL (Jira Query Language) string used to filter issues.
     * @param fields A list of fields to be included in the search results. Defaults to a predefined set of fields.
     * @param expand A list of expandable properties to include in the response. Defaults to a predefined set of
     *        expansions.
     * @param extraFields Additional fields to include in the search results, which will be appended to the fields
     *        list.
     * @return A list of issues matching the JQL query, represented as `JiraIssueResponse` objects.
     */
    suspend fun searchIssues(
        baseUrl: String,
        credentials: JiraCredential,
        jql: String,
        fields: List<String> = DEFAULT_FIELDS,
        expand: List<String> = DEFAULT_EXPAND,
        extraFields: List<String> = emptyList(),
    ): List<JiraIssueResponse> {
        val requestFields = (fields + extraFields).joinToString(",")
        val requestExpand = expand.joinToString(",")

        return fetchIssuePages(
            baseUrl = baseUrl,
            credentials = credentials,
            jql = jql,
            fields = requestFields,
            expand = requestExpand,
        ).flatMap { it.issues }
    }

    /**
     * Retrieves a list of Jira projects from a specified Jira instance.
     *
     * @param baseUrl The base URL of the Jira instance.
     * @param credentials The credentials required to authenticate with the Jira instance.
     * @return A list of projects available in the Jira instance, represented as `JiraProjectResponse` objects.
     */
    suspend fun searchProjects(
        baseUrl: String,
        credentials: JiraCredential,
    ): List<JiraProjectResponse> = fetchProjectPages(baseUrl, credentials).flatMap { it.values }

    /**
     * Checks the capabilities of a Jira instance to determine if it is valid and reachable.
     * Invokes the server information endpoint of the specified Jira instance and validates the response.
     *
     * @param url The base URL of the Jira instance to be checked.
     * @return `true` if the Jira instance is reachable and recognized as a valid Jira instance; `false` otherwise.
     */
    @Suppress("SwallowedException")
    suspend fun checkInstanceCapabilities(url: String): Boolean {
        val serverInfo = try {
            webClient
                .get()
                .uri("$url/rest/api/3/serverInfo")
                .sync()
                .perform<JiraServerCapabilitiesResponse>()
        } catch (e: WebClientException) {
            logger.warn("Jira instance at $url is not reachable", e)
            return false
        } catch (e: kotlinx.serialization.SerializationException) {
            logger.warn("Jira instance at $url returned unexpected response", e)
            return false
        }
        return serverInfo.serverTitle == "Jira"
    }

    /**
     * Fetches multiple pages of issue search results from a Jira instance using pagination.
     *
     * This method continuously requests pages of issues from the Jira API based on a provided JQL query,
     * until all pages are retrieved or the `isLast` flag is encountered in the response.
     *
     * @param baseUrl The base URL of the Jira instance.
     * @param credentials The credentials required to authenticate with the Jira instance.
     * @param jql The JQL (Jira Query Language) string used to filter issues.
     * @param fields A comma-separated list of fields to be included in the search results.
     * @param expand A comma-separated list of expandable properties to include in the response.
     * @return A list of `PaginatedIssuesSearchResponse` objects representing the paged search results.
     */
    private suspend fun fetchIssuePages(
        baseUrl: String,
        credentials: JiraCredential,
        jql: String,
        fields: String,
        expand: String,
    ): List<PaginatedIssuesSearchResponse> {
        val results = mutableListOf<PaginatedIssuesSearchResponse>()

        do {
            val query = buildMap {
                put("jql", jql)
                put("maxResults", DEFAULT_MAX_RESULTS.toString())
                put("fields", fields)
                put("expand", expand)
                results.lastOrNull()?.nextPageToken?.let { put("nextPageToken", it) }
            }
            val page: PaginatedIssuesSearchResponse = performGet(
                buildUri("$baseUrl/rest/api/3/search/jql", query),
                credentials,
            )
            results.add(page)
        } while (results.last().let { !it.isLast && it.nextPageToken != null })

        return results
    }

    /**
     * Fetches multiple pages of project search results from a Jira instance using pagination.
     *
     * This method continuously retrieves pages of projects from the Jira API until all pages
     * are retrieved or the `isLast` flag is encountered in the response.
     *
     * @param baseUrl The base URL of the Jira instance.
     * @param credentials The credentials required to authenticate with the Jira instance.
     * @return A list of `PaginatedProjectsSearchResponse` objects representing the paged search results.
     */
    private suspend fun fetchProjectPages(
        baseUrl: String,
        credentials: JiraCredential,
    ): List<PaginatedProjectsSearchResponse> {
        val results = mutableListOf<PaginatedProjectsSearchResponse>()
        var startAt = 0

        while (true) {
            val query = buildMap {
                put("startAt", startAt.toString())
                put("maxResults", DEFAULT_MAX_RESULTS.toString())
            }
            val page: PaginatedProjectsSearchResponse = performGet(
                buildUri("$baseUrl/rest/api/3/project/search", query),
                credentials,
            )
            results.add(page)

            if (page.isLast) {
                break
            }
            startAt += DEFAULT_MAX_RESULTS
        }

        return results
    }

    /**
     * Performs an HTTP GET request to the specified URI and deserializes the response into the specified type [T].
     *
     * In case of specific HTTP error responses, it throws domain-specific exceptions such as [JiraAuthException]
     * for authentication errors or [JiraResourceNotFoundException] for missing resources.
     *
     * @param uri The URI of the resource to fetch.
     * @param credentials The Jira credentials required for authentication.
     * @return The deserialized response of type [T].
     * @throws JiraAuthException If authentication fails (e.g., HTTP 401 or 403).
     * @throws JiraResourceNotFoundException If the resource is not found (HTTP 404).
     * @throws WebClientException For other HTTP errors or transport-related issues.
     */
    private suspend inline fun <reified T> performGet(
        uri: String,
        credentials: JiraCredential,
    ): T = try {
        webClient
            .get()
            .uri(uri)
            .header("Authorization", credentials.basicAuthorizationHeader())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "SprintStart-Jira-Client/1.0")
            .sync()
            .perform<T>()
    } catch (e: WebClientException) {
        when (e.statusCode) {
            401 -> throw JiraAuthException(HttpStatus.UNAUTHORIZED, e.body, e)

            403 -> throw JiraAuthException(HttpStatus.FORBIDDEN, e.body, e)

            404 -> throw JiraResourceNotFoundException(
                "Jira resource not found at $uri: ${e.body}",
                e,
            )

            else -> throw e
        }
    }

    /**
     * Constructs a URI by appending encoded query parameters to the specified base URL.
     *
     * @param baseUrl The base URL to which query parameters will be appended.
     * @param query A map of query parameters where the keys and values represent the parameter names and values.
     *              Both keys and values will be URL-encoded.
     * @return A full URI string composed of the base URL and the encoded query parameters.
     */
    private fun buildUri(baseUrl: String, query: Map<String, String>): String {
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${key.encodePath()}=${value.encodePath()}"
        }
        return "$baseUrl?$queryString"
    }

    /**
     * Constructs a Basic Authorization header value using the encoded credentials of the Jira user.
     *
     * The method combines the user's email and authentication token from the JiraCredential instance,
     * encodes the combination in Base64, and formats it as a Basic Authorization header.
     *
     * @return A string representation of the Basic Authorization header.
     */
    private fun JiraCredential.basicAuthorizationHeader(): String {
        val credentials = "${this.id.userEmail.trim()}:${this.authToken.trim()}"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    /**
     * Encodes the string as a URL path segment using the UTF-8 character set.
     *
     * This method applies URL encoding to the string, ensuring that special characters
     * are correctly encoded for use in a URL path. For example, spaces are replaced
     * with `%20` and other reserved characters are properly escaped.
     *
     * @return The URL-encoded representation of the string, suitable for inclusion in a URL path.
     */
    private fun String.encodePath(): String = URLEncoder.encode(this, Charsets.UTF_8)

    companion object {
        private const val DEFAULT_MAX_RESULTS = 100
        private val DEFAULT_FIELDS = listOf(
            "issuetype",
            "project",
            "parent",
            "resolution",
            "resolutiondate",
            "created",
            "priority",
            "labels",
            "versions",
            "assignee",
            "updated",
            "status",
            "components",
            "issuekey",
            "description",
            "timetracking",
            "summary",
            "creator",
            "subtasks",
            "reporter",
            "duedate",
            "comment",
            "environment",
            "issuelinks",
        )
        private val DEFAULT_EXPAND = listOf("changelog")
    }
}

@Serializable
data class PaginatedIssuesSearchResponse(
    val issues: List<JiraIssueResponse>,
    val isLast: Boolean = false,
    val nextPageToken: String? = null,
)

@Serializable
data class PaginatedProjectsSearchResponse(
    val isLast: Boolean,
    val values: List<JiraProjectResponse>,
)
