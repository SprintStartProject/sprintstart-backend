package com.sprintstart.sprintstartbackend.connectors.bitbucket

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.RequestBuilder
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Talks to the Bitbucket Cloud REST API, version 2.0.
 *
 * ### While Bitbucket is somewhat similar to Github (in this application's sense), some differences to [GithubClient]
 *
 * * **No issue tracker.** Atlassian removed the native Issue tracker and its API endpoints on
 *   20 August 2026, so there is deliberately no `fetchIssues` here. Issue-shaped work for a
 *   Bitbucket project has to come from Jira.
 * * **No teams.** GitHub's organisation teams have no counterpart in the public 2.0 API. Only
 *   workspace membership is read here; nothing team-shaped was invented to fill the gap.
 * * **No cross-workspace repository listing.** Atlassian retired that endpoint in April 2026, so
 *   discovery is workspace-scoped only and there is no counterpart to `discoverRepositoriesOfUser`.
 *
 * @constructor Creates the client from the shared transport and the application configuration.
 * @param webClient Performs the outbound HTTP calls. Owns transport only.
 * @param applicationConfig Supplies `sprintstart.bitbucket.base-url`, normally
 *   `https://api.bitbucket.org/2.0`.
 */
@Component
@Suppress("TooManyFunctions")
class BitbucketClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    private val apiBaseUrl = applicationConfig.bitbucket.baseUrl.trimEnd('/')

    /**
     * Fetches the metadata of one workspace.
     *
     * A workspace is Bitbucket's counterpart to a GitHub organisation, and this is the data behind
     * the workspace metadata artifact.
     *
     * @param workspace The workspace id, as it appears in a repository URL.
     * @param token The Bitbucket API token used to authenticate the request.
     * @return The workspace's metadata.
     * @throws WebClientException if the workspace does not exist or the token cannot read it.
     * @throws kotlinx.serialization.SerializationException if the response body cannot be deserialized.
     */
    suspend fun fetchWorkspaceMetadata(workspace: String, token: String): WorkspaceMetadataResponse =
        authorizedGet("$apiBaseUrl/workspaces/${urlEncode(workspace)}", token)
            .sync()
            .perform()

    /**
     * Fetches every member of a workspace.
     *
     * Traverses all pages of the membership collection, so the result is the complete member list
     * rather than one page of it.
     *
     * @param workspace The workspace whose members should be read.
     * @param token The Bitbucket API token used to authenticate the request.
     * @return The workspace's members.
     * @throws WebClientException if the request fails with a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if a response body cannot be deserialized.
     */
    suspend fun getWorkspaceMembers(workspace: String, token: String): WorkspaceMembersResponse {
        val members = fetchAllPages<WorkspaceMemberResponse>(
            firstPageUri = "$apiBaseUrl/workspaces/${urlEncode(workspace)}/members?pagelen=$PAGE_LENGTH",
            token = token,
        )
        return WorkspaceMembersResponse(members = members)
    }

    /**
     * Checks whether a workspace exists and the token can read it.
     *
     * A 404 means the workspace is not visible to the caller, which is deliberately reported as
     * `false` rather than thrown: Bitbucket answers 404 both for a workspace that does not exist
     * and for one the token cannot see, and the two are indistinguishable to us by design. Any
     * other non-2xx status is rethrown, because that is an outage rather than an answer.
     *
     * @param workspace The workspace id to check.
     * @param token The Bitbucket API token used to authenticate the request.
     * @return true when the workspace is readable with this token, false otherwise.
     * @throws WebClientException if the request fails with a status other than 404.
     */
    suspend fun workspaceExists(workspace: String, token: String): Boolean =
        authorizedGet("$apiBaseUrl/workspaces/${urlEncode(workspace)}", token)
            .isReadable()

    /**
     * Checks whether a connected repository still exists at its source.
     *
     * Uses the credential of whoever connected the repository, not the caller's, so a visibility
     * check reflects what the connection can actually reach.
     *
     * @param repository The connected repository to check.
     * @return true when the repository is readable with the connection's token, false otherwise.
     * @throws WebClientException if the request fails with a status other than 404.
     */
    suspend fun repositoryExists(repository: BitbucketRepositoryConnection): Boolean =
        authorizedGet(repositoryUri(repository), repository.user.token)
            .isReadable()

    /**
     * Discovers the repositories of a workspace.
     *
     * Returns a single page, because the connector's discovery API pages for the caller and mirrors
     * the page/pageSize contract rather than exposing Bitbucket's `next` link. Pages are one-based
     * in the API and zero-based in the connector, hence the `+ 1`.
     *
     * @param workspace The workspace whose repositories should be listed.
     * @param token The Bitbucket API token used to authenticate the request.
     * @param page The zero-based index of the page to fetch.
     * @param pageSize The number of repositories to fetch per page.
     * @return The repositories on that page.
     * @throws WebClientException if the request fails with a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if the response body cannot be deserialized.
     */
    suspend fun discoverRepositoriesOfWorkspace(
        workspace: String,
        token: String,
        page: Int,
        pageSize: Int,
    ): DiscoverRepositoriesResponse {
        val response: BitbucketPage<DiscoveredRepository> = authorizedGet(
            "$apiBaseUrl/repositories/${urlEncode(workspace)}?pagelen=$pageSize&page=${page + 1}",
            token,
        ).sync().perform()
        return DiscoverRepositoriesResponse(response.values)
    }

    /**
     * Fetches every pull request of a repository, optionally only those touched recently.
     *
     * Unlike the GitHub client, which had to search for pull request numbers and then fetch each
     * one, the collection endpoint already returns full representations, so the traversal is one
     * pass over the pages.
     *
     * The endpoint defaults to open pull requests only, so every state is requested explicitly —
     * a merged or declined pull request still carries the merge and first-response timestamps that
     * onboarding reads. When [sinceTimestamp] is given, the filter is pushed to the server as a
     * Bitbucket query-language expression on `updated_on`, so an incremental sync never downloads
     * the whole history to discard most of it.
     *
     * @param repository The connected repository to read pull requests from.
     * @param sinceTimestamp Optional ISO 8601 instant. When given, only pull requests updated on or
     * after it are returned.
     * @return The matching pull requests.
     * @throws WebClientException if a request fails with a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if a response body cannot be deserialized.
     */
    suspend fun fetchAllPullRequests(
        repository: BitbucketRepositoryConnection,
        sinceTimestamp: String? = null,
    ): List<PullRequest> =
        fetchAllPages(
            firstPageUri = buildPullRequestsUri(repository, sinceTimestamp),
            token = repository.user.token,
        )

    /**
     * Fetches one pull request of a repository.
     *
     * The single-resource counterpart to [fetchAllPullRequests], for the fields a collection
     * response may not carry in full.
     *
     * @param repository The connected repository holding the pull request.
     * @param pullRequestId The pull request's id within that repository.
     * @return The pull request, or null when no such pull request exists.
     * @throws WebClientException if the request fails with a status other than 404.
     * @throws kotlinx.serialization.SerializationException if the response body cannot be deserialized.
     */
    suspend fun fetchPullRequest(
        repository: BitbucketRepositoryConnection,
        pullRequestId: Int,
    ): PullRequest? =
        authorizedGet("${repositoryUri(repository)}/pullrequests/$pullRequestId", repository.user.token)
            .fetchOrNullOnMissing()

    /**
     * Fetches every comment on one pull request.
     *
     * Traverses all pages, since a single pull request routinely carries more than one page of
     * comments. Callers deriving "when did somebody other than the author first respond" have to
     * filter the result themselves: Bitbucket returns deleted comments too, marked as such, and a
     * deleted comment is not a response.
     *
     * @param repository The connected repository holding the pull request.
     * @param pullRequestId The pull request whose comments should be read.
     * @return The pull request's comments.
     * @throws WebClientException if a request fails with a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if a response body cannot be deserialized.
     */
    suspend fun fetchAllPullRequestComments(
        repository: BitbucketRepositoryConnection,
        pullRequestId: Int,
    ): List<PullRequestComment> =
        fetchAllPages(
            firstPageUri = "${repositoryUri(repository)}/pullrequests/$pullRequestId/comments?pagelen=$PAGE_LENGTH",
            token = repository.user.token,
        )

    /**
     * Builds the collection URI for a repository's pull requests, with states and filter applied.
     *
     * @param repository The connected repository whose pull requests should be listed.
     * @param sinceTimestamp Optional ISO 8601 instant to filter on `updated_on`.
     * @return The absolute URI of the first page.
     */
    private fun buildPullRequestsUri(
        repository: BitbucketRepositoryConnection,
        sinceTimestamp: String?,
    ): String =
        buildString {
            append(repositoryUri(repository))
            append("/pullrequests?pagelen=")
            append(PAGE_LENGTH)
            PULL_REQUEST_STATES.forEach { state ->
                append("&state=")
                append(state)
            }
            if (sinceTimestamp != null) {
                append("&q=")
                append(urlEncode("""updated_on >= "$sinceTimestamp""""))
            }
        }

    /**
     * Walks a Bitbucket collection to its end and returns every item.
     *
     * Bitbucket's collection responses carry a `next` link that is already the complete, correctly
     * cursor-ed URL of the following page. Following it verbatim is what makes this loop correct
     * for every collection without knowing that collection's paging parameters, and the loop ends
     * where Bitbucket omits the link.
     *
     * @param firstPageUri The absolute URI of the first page, including any filter parameters.
     * @param token The Bitbucket API token used to authenticate each request.
     * @return All items across all pages, in the order the API returned them.
     * @throws WebClientException if any request fails with a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if a response body cannot be deserialized.
     */
    private suspend inline fun <reified T> fetchAllPages(
        firstPageUri: String,
        token: String,
    ): List<T> {
        val items = mutableListOf<T>()
        var nextUri: String? = firstPageUri

        while (nextUri != null) {
            val page: BitbucketPage<T> = authorizedGet(nextUri, token).sync().perform()
            items += page.values
            nextUri = page.next
        }

        return items
    }

    /**
     * Builds an authenticated `GET` request for a Bitbucket URI.
     *
     * Every call in this client goes through here, so the transport concerns — the authorization
     * scheme and the accepted content type — are decided in exactly one place.
     *
     * Authentication uses bearer credentials, which Bitbucket Cloud accepts for API tokens and
     * which need only the token itself. The shared `AtlassianCredentialApi` also hands back the
     * account email, but bearer does not use it; switching to Bitbucket's alternative of
     * `Basic <email:api_token>` changes only the header built here.
     *
     * @param uri The absolute request URI. Collection URLs taken from a `next` link are passed
     * through unchanged.
     * @param token The Bitbucket API token to authenticate with.
     * @return The prepared request, ready for `.sync()`.
     */
    private fun authorizedGet(uri: String, token: String): RequestBuilder =
        webClient
            .get()
            .uri(uri)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")

    /**
     * Sends the request and reports absence as `null` instead of an exception.
     *
     * @return The decoded body, or null when the resource does not exist.
     * @throws WebClientException if the request fails with a status other than 404.
     */
    private suspend inline fun <reified T> RequestBuilder.fetchOrNullOnMissing(): T? =
        try {
            sync().perform()
        } catch (e: WebClientException) {
            if (e.statusCode == 404) {
                null
            } else {
                throw e
            }
        }

    /**
     * Sends the request and reports absence as `false` instead of an exception.
     *
     * @return true when the resource is readable, false when the server answered 404.
     * @throws WebClientException if the request fails with a status other than 404.
     */
    private suspend fun RequestBuilder.isReadable(): Boolean =
        try {
            sync().performRaw()
            true
        } catch (e: WebClientException) {
            if (e.statusCode == 404) {
                false
            } else {
                throw e
            }
        }

    /** Builds the URI of a repository resource from the connection's stored coordinates. */
    private fun repositoryUri(repository: BitbucketRepositoryConnection): String =
        "$apiBaseUrl/repositories/${urlEncode(repository.workspace)}/${urlEncode(repository.slug)}"

    /**
     * Percent-encodes one URI component.
     *
     * Applied to path segments and to query values alike, because Bitbucket has no id that may
     * contain a reserved character and treating a path segment as anything but opaque is how a
     * workspace or repository whose name contains a dot or an at-sign breaks a request. Space is
     * encoded as `%20` rather than `+` so the result is valid in a path as well as a query string.
     *
     * @param value The raw component to encode.
     * @return The encoded component.
     */
    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private companion object {
        /**
         * Items requested per page. Bitbucket's default page is far smaller than this, and every
         * collection here is walked in full, so asking for a larger page saves round trips.
         */
        const val PAGE_LENGTH = 100

        /**
         * Every pull request state Bitbucket can report. Requested together so a sync sees merges
         * and declines and not just open work.
         */
        val PULL_REQUEST_STATES = listOf("OPEN", "MERGED", "DECLINED", "SUPERSEDED")
    }
}
