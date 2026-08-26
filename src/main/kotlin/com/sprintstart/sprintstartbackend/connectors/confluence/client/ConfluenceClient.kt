package com.sprintstart.sprintstartbackend.connectors.confluence.client

import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.serialization.SerializationException
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Reads spaces, pages, and page-level read restrictions from Confluence Cloud.
 *
 * API v2 is used for spaces and storage-format pages. API v1 is used only for read restrictions. The client
 * follows server-provided v2 pagination links, while v1 restriction pages use the endpoint's shared
 * `start`/`limit` sequence for users and groups.
 */
@Component
internal class ConfluenceClient(
    private val webClient: WebClient,
) {
    /**
     * Validates credentials by requesting one visible Confluence space.
     *
     * A successful response is sufficient even when the result list is empty.
     */
    suspend fun validateConnection(
        baseUrl: String,
        credentials: ConfluenceClientCredentials,
    ) {
        performGet<ConfluenceSpacesResponse>(
            uri = confluenceValidationUri(baseUrl),
            credentials = credentials,
            requestContext = "validating the connection",
        )
    }

    /** Retrieves every space visible to the supplied credentials. */
    suspend fun discoverSpaces(
        baseUrl: String,
        credentials: ConfluenceClientCredentials,
    ): List<ConfluenceSpace> {
        val spaces = mutableListOf<ConfluenceSpace>()
        val tenantUri = normalizedConfluenceTenantUri(baseUrl)
        var requestUri: URI? = confluenceSpacesUri(baseUrl)

        while (requestUri != null) {
            val currentUri = requestUri
            val response = performGet<ConfluenceSpacesResponse>(
                uri = currentUri,
                credentials = credentials,
                requestContext = "discovering spaces",
            )
            spaces += response.results.map { space -> space.toDomain() }
            requestUri = response.links.next?.let { next ->
                confluencePaginationUri(tenantUri, currentUri, next, "discovering spaces")
            }
        }

        return spaces
    }

    /** Retrieves one Confluence space by its stable numeric ID. */
    suspend fun getSpace(
        baseUrl: String,
        credentials: ConfluenceClientCredentials,
        spaceId: String,
    ): ConfluenceSpace {
        val response = performGet<ConfluenceSpaceResponse>(
            uri = confluenceSpaceUri(baseUrl, spaceId),
            credentials = credentials,
            requestContext = "retrieving space $spaceId",
        )
        return response.toDomain()
    }

    /**
     * Retrieves all pages in a space and attaches complete page-level read restrictions.
     *
     * Pages whose restriction request returns 404 are excluded from [ConfluencePageBatchResult.successfulPages]
     * and represented in [ConfluencePageBatchResult.failures]. Other HTTP failures remain terminal.
     */
    suspend fun getPages(
        baseUrl: String,
        credentials: ConfluenceClientCredentials,
        spaceId: String,
    ): ConfluencePageBatchResult {
        val pageResponses = fetchAllPageResponses(baseUrl, credentials, spaceId)
        val successfulPages = mutableListOf<ConfluencePage>()
        val failures = mutableListOf<ConfluencePageFailure>()

        for (page in pageResponses) {
            try {
                val restrictions = fetchRestrictions(baseUrl, credentials, page.id)
                successfulPages += page.toDomain(restrictions)
            } catch (exception: ConfluenceResourceNotFoundException) {
                failures += ConfluencePageFailure(
                    pageId = page.id,
                    stage = ConfluencePageFetchStage.RESTRICTIONS,
                    httpStatus = exception.httpStatus,
                    message = CONFLUENCE_RESTRICTIONS_NOT_FOUND_MESSAGE,
                )
            }
        }

        return ConfluencePageBatchResult(successfulPages, failures)
    }

    private suspend fun fetchAllPageResponses(
        baseUrl: String,
        credentials: ConfluenceClientCredentials,
        spaceId: String,
    ): List<ConfluencePageResponse> {
        val pages = mutableListOf<ConfluencePageResponse>()
        val tenantUri = normalizedConfluenceTenantUri(baseUrl)
        var requestUri: URI? = confluencePagesUri(baseUrl, spaceId)
        val requestContext = "retrieving pages for space $spaceId"

        while (requestUri != null) {
            val currentUri = requestUri
            val response = performGet<ConfluencePagesResponse>(
                uri = currentUri,
                credentials = credentials,
                requestContext = requestContext,
            )
            pages += response.results
            requestUri = response.links.next?.let { next ->
                confluencePaginationUri(tenantUri, currentUri, next, requestContext)
            }
        }

        return pages
    }

    private suspend fun fetchRestrictions(
        baseUrl: String,
        credentials: ConfluenceClientCredentials,
        pageId: String,
    ): ConfluencePageRestrictions {
        val usersById = linkedMapOf<String, ConfluenceRestrictedUser>()
        val groupsById = linkedMapOf<String, ConfluenceRestrictedGroup>()
        var start = 0
        var hasMore: Boolean
        val requestContext = "retrieving read restrictions for page $pageId"

        do {
            val response = performGet<ConfluenceRestrictionsResponse>(
                uri = confluenceRestrictionsUri(baseUrl, pageId, start),
                credentials = credentials,
                requestContext = requestContext,
            )
            val pageMetadata = response.validatePage(
                requestedStart = start,
                requestedLimit = CONFLUENCE_RESTRICTION_PAGE_LIMIT,
                requestContext = requestContext,
            )
            response.restrictions.user.results.forEach { user ->
                usersById[user.accountId] = ConfluenceRestrictedUser(user.accountId)
            }
            response.restrictions.group.results.forEach { group ->
                groupsById[group.id] = ConfluenceRestrictedGroup(group.id)
            }

            hasMore = pageMetadata.hasMore
            start = pageMetadata.nextStart
        } while (hasMore)

        return ConfluencePageRestrictions(
            users = usersById.values.toList(),
            groups = groupsById.values.toList(),
        )
    }

    private suspend inline fun <reified T> performGet(
        uri: URI,
        credentials: ConfluenceClientCredentials,
        requestContext: String,
    ): T {
        try {
            return webClient
                .get()
                .uri(uri)
                .header("Authorization", credentials.basicAuthorizationHeader())
                .header("Accept", "application/json")
                .sync()
                .perform<T>()
        } catch (exception: WebClientException) {
            throw exception.toSafeConfluenceException(requestContext)
        } catch (@Suppress("SwallowedException") exception: SerializationException) {
            throw ConfluenceInvalidResponseException(requestContext)
        }
    }
}
