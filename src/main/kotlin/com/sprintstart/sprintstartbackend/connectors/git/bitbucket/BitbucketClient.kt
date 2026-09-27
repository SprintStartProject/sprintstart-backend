package com.sprintstart.sprintstartbackend.connectors.git.bitbucket

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketPage
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.DiscoverRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.DiscoveredRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.PullRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.PullRequestComment
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMemberResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMembersResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMetadataResponse
import com.sprintstart.sprintstartbackend.shared.web.RequestBuilder
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Talks to the Bitbucket Cloud REST API, version 2.0.
 *
 * While Bitbucket is somewhat similar to GitHub (in this application's sense), some differences
 * to [com.sprintstart.sprintstartbackend.connectors.git.github.GithubClient] shaped this client deliberately:
 *
 * * **No issue tracker.** Atlassian removed the native Issue tracker and its API endpoints on
 *   20 August 2026, so there is deliberately no `fetchIssues` here. Issue-shaped work for a
 *   Bitbucket project has to come from Jira.
 * * **No cross-workspace repository listing.** Atlassian retired that endpoint in April 2026, so
 *   discovery is workspace-scoped only and there is no counterpart to `discoverRepositoriesOfUser`.
 * * **Rate limiting is handled in here.** Bitbucket answers `429` with a `Retry-After` header, so
 *   every request is wrapped in a small bounded retry rather than being pushed onto callers.
 *
 * Tokens are passed per call rather than read off a connection entity: this client owns transport,
 * and the caller that resolved the credential is the one that knows which token applies.
 *
 * @constructor Creates the client from the shared transport and the application configuration.
 * @param webClient Performs the outbound HTTP calls. Owns transport only.
 * @param applicationConfig Supplies `sprintstart.bitbucket.base-url`, normally `https://api.bitbucket.org/2.0`.
 */
@Component
@Suppress("TooManyFunctions")
class BitbucketClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    private val apiBaseUrl = applicationConfig.bitbucket.baseUrl.trimEnd('/')
    private val logger = LoggerFactory.getLogger(BitbucketClient::class.java)

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
        executeGet(
            uri = "${workspaceUri(workspace)}?fields=$WORKSPACE_FIELDS",
            token = token,
            requestContext = "workspace metadata of '$workspace'",
        )

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
            firstPageUri = "${workspaceUri(workspace)}/members?pagelen=$PAGE_LENGTH&fields=$WORKSPACE_MEMBER_FIELDS",
            token = token,
            requestContext = "workspace members of '$workspace'",
        )
        return WorkspaceMembersResponse(members = members)
    }

    /**
     * Checks whether a repository exists and the token can read it.
     *
     * A 404 means the repository is not visible to the caller, which is deliberately reported as
     * `false` rather than thrown: Bitbucket answers 404 both for a repository that does not exist
     * and for one the token cannot see, and the two are indistinguishable to us by design. Any
     * other non-2xx status is rethrown, because that is an outage rather than an answer.
     *
     * @param workspace The workspace owning the repository.
     * @param slug The repository's slug within that workspace.
     * @param token The Bitbucket API token used to authenticate the request.
     * @return true when the repository is readable with this token, false otherwise.
     * @throws WebClientException if the request fails with a status other than 404.
     */
    suspend fun repositoryExists(workspace: String, slug: String, token: String): Boolean =
        try {
            executeRawGet(repositoryUri(workspace, slug), token, "repository '$workspace/$slug'")
            true
        } catch (exception: WebClientException) {
            if (exception.statusCode == 404) {
                false
            } else {
                throw exception
            }
        }

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
        val response: BitbucketPage<DiscoveredRepository> = executeGet(
            uri = "$apiBaseUrl/repositories/${urlEncode(workspace)}" +
                "?pagelen=$pageSize&page=${page + 1}&fields=$REPOSITORY_FIELDS",
            token = token,
            requestContext = "repository discovery of '$workspace'",
        )
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
     * @param workspace The workspace owning the repository.
     * @param slug The repository's slug within that workspace.
     * @param token The Bitbucket API token used to authenticate the request.
     * @param sinceTimestamp Optional ISO 8601 instant. When given, only pull requests updated on or
     * after it are returned.
     * @return The matching pull requests.
     * @throws WebClientException if a request fails with a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if a response body cannot be deserialized.
     */
    suspend fun fetchAllPullRequests(
        workspace: String,
        slug: String,
        token: String,
        sinceTimestamp: String? = null,
    ): List<PullRequest> =
        fetchAllPages<PullRequest>(
            firstPageUri = buildPullRequestsUri(workspace, slug, sinceTimestamp),
            token = token,
            requestContext = "pull requests of '$workspace/$slug'",
        )

    /**
     * Fetches every comment on one pull request.
     *
     * Traverses all pages, since a single pull request routinely carries more than one page of
     * comments. Callers deriving "when did somebody other than the author first respond" have to
     * filter the result themselves: Bitbucket returns deleted comments too, marked as such, and a
     * deleted comment is not a response.
     *
     * @param workspace The workspace owning the repository.
     * @param slug The repository's slug within that workspace.
     * @param pullRequestId The pull request whose comments should be read.
     * @param token The Bitbucket API token used to authenticate the request.
     * @return The pull request's comments.
     * @throws WebClientException if a request fails with a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if a response body cannot be deserialized.
     */
    suspend fun fetchAllPullRequestComments(
        workspace: String,
        slug: String,
        pullRequestId: Int,
        token: String,
    ): List<PullRequestComment> =
        fetchAllPages<PullRequestComment>(
            firstPageUri = "${repositoryUri(workspace, slug)}/pullrequests/$pullRequestId" +
                "/comments?pagelen=$PAGE_LENGTH&fields=$PULL_REQUEST_COMMENT_FIELDS",
            token = token,
            requestContext = "comments of pull request $pullRequestId in '$workspace/$slug'",
        )

    /**
     * Builds the collection URI for a repository's pull requests, with states, filter and fields applied.
     *
     * @param workspace The workspace owning the repository.
     * @param slug The repository's slug within that workspace.
     * @param sinceTimestamp Optional ISO 8601 instant to filter on `updated_on`.
     * @return The absolute URI of the first page.
     */
    private fun buildPullRequestsUri(
        workspace: String,
        slug: String,
        sinceTimestamp: String?,
    ): String =
        buildString {
            append(repositoryUri(workspace, slug))
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
            append("&fields=")
            append(PULL_REQUEST_FIELDS)
        }

    /**
     * Walks a Bitbucket collection to its end and returns every item.
     *
     * Bitbucket's collection responses carry a `next` link that is already the complete, correctly
     * cursor-ed URL of the following page. Following it verbatim is what makes this loop correct
     * for every collection without knowing that collection's paging parameters, and the loop ends
     * where Bitbucket omits the link.
     *
     * Every collection field list must therefore request `next` explicitly: the `fields` parameter
     * decides the whole response envelope, so a list that forgets it would silently stop after the
     * first page.
     *
     * @param firstPageUri The absolute URI of the first page, including any filter parameters.
     * @param token The Bitbucket API token used to authenticate each request.
     * @param requestContext Describes the collection for retry log messages.
     * @return All items across all pages, in the order the API returned them.
     * @throws WebClientException if any request fails with a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if a response body cannot be deserialized.
     */
    private suspend inline fun <reified T> fetchAllPages(
        firstPageUri: String,
        token: String,
        requestContext: String,
    ): List<T> {
        val items = mutableListOf<T>()
        var nextUri: String? = firstPageUri

        while (nextUri != null) {
            val page: BitbucketPage<T> = executeGet(nextUri, token, requestContext)
            items += page.values
            nextUri = page.next
        }

        return items
    }

    /**
     * Sends one request and decodes its JSON body, retrying while Bitbucket is unavailable.
     *
     * @param uri The absolute request URI. Collection URLs taken from a `next` link are passed
     * through unchanged.
     * @param token The Bitbucket API token to authenticate with.
     * @param requestContext Describes the resource for retry log messages.
     * @return The decoded response body.
     * @throws WebClientException if the request still fails after the retry budget is spent.
     * @throws kotlinx.serialization.SerializationException if the response body cannot be deserialized.
     */
    private suspend inline fun <reified T> executeGet(
        uri: String,
        token: String,
        requestContext: String,
    ): T =
        withRateLimitRetry(requestContext) {
            authorizedGet(uri, token).sync().perform<T>()
        }

    /**
     * Sends one request, discarding the body.
     *
     * Used where only the status code carries meaning, so nothing is deserialized.
     *
     * @param uri The absolute request URI.
     * @param token The Bitbucket API token to authenticate with.
     * @param requestContext Describes the resource for retry log messages.
     * @throws WebClientException if the request still fails after the retry budget is spent.
     */
    private suspend fun executeRawGet(uri: String, token: String, requestContext: String) {
        withRateLimitRetry(requestContext) {
            authorizedGet(uri, token).sync().performRaw()
        }
    }

    /**
     * Runs [request], retrying it while Bitbucket is rate limiting or failing.
     *
     * Only `429` and `5xx` are retried: those are the statuses where the same request can plausibly
     * succeed later. A `404` or a `401` is an answer, and retrying it would only delay the caller's
     * error.
     *
     * Bitbucket's `Retry-After` header wins over the local backoff when present, because Bitbucket
     * knows better than we do when the rate limit window closes; the value is capped so a
     * nonsensical header cannot stall a sync indefinitely. Both the header and the backoff are
     * bounded, and the attempt counter is shared, so a request that keeps being rate limited fails
     * with the last `WebClientException` rather than looping.
     *
     * @param requestContext Describes the resource for retry log messages.
     * @param request The request to run and possibly re-run.
     * @return The result of the first successful attempt.
     * @throws WebClientException if the request is not retryable or the budget is spent.
     */
    private suspend inline fun <T> withRateLimitRetry(
        requestContext: String,
        crossinline request: suspend () -> T,
    ): T {
        var attempt = 1

        while (true) {
            try {
                return request()
            } catch (exception: WebClientException) {
                if (!exception.isRetryableHttpStatus() || attempt >= MAX_ATTEMPTS) {
                    throw exception
                }
                val waitMillis = exception.retryAfterMillis() ?: backoffMillis(attempt)
                logger.warn(
                    "Retrying Bitbucket request '{}' after attempt {} with status {}; delay={}ms",
                    requestContext,
                    attempt,
                    exception.statusCode,
                    waitMillis,
                )
                delay(waitMillis)
                attempt++
            }
        }
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

    /** Builds the URI of a workspace resource. */
    private fun workspaceUri(workspace: String): String =
        "$apiBaseUrl/workspaces/${urlEncode(workspace)}"

    /** Builds the URI of a repository resource. */
    private fun repositoryUri(workspace: String, slug: String): String =
        "$apiBaseUrl/repositories/${urlEncode(workspace)}/${urlEncode(slug)}"

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

        /**
         * Fields requested per request, the REST counterpart to a GraphQL selection set.
         *
         * Sub-objects are named whole (`values.user`, `values.participants`) rather than peeled
         * down to their individual members: Bitbucket drops a misspelled field silently instead of
         * failing, so a deep path buys a little bandwidth in exchange for a null that is easy to
         * miss. Every collection list also names `next` explicitly, because it drives the paging
         * loop in `fetchAllPages`.
         */
        const val WORKSPACE_FIELDS = "uuid,slug,name,is_private,created_on,links"
        const val WORKSPACE_MEMBER_FIELDS = "next,values.user"
        const val REPOSITORY_FIELDS = "next,values.name,values.slug,values.full_name,values.is_private,values.links"
        const val PULL_REQUEST_FIELDS =
            "next,values.id,values.title,values.description,values.state,values.author,values.created_on," +
                "values.updated_on,values.closed_on,values.merge_commit,values.participants,values.links"
        const val PULL_REQUEST_COMMENT_FIELDS =
            "next,values.id,values.created_on,values.updated_on,values.content.raw,values.content.markup," +
                "values.user,values.deleted"
    }
}

/** Attempts made per request, including the first one. */
private const val MAX_ATTEMPTS = 4

/** Backoff before the second attempt; doubled for each attempt after it. */
private const val INITIAL_BACKOFF_MILLIS = 1_000L

/** Upper bound for both the backoff and a server-supplied `Retry-After`, so a sync cannot park for long. */
private const val MAX_RETRY_DELAY_MILLIS = 60_000L

/**
 * Whether the same request can plausibly succeed later.
 *
 * `429` is Bitbucket's rate limit and `5xx` is Bitbucket failing to answer. Everything else is an
 * answer about the resource.
 */
private fun WebClientException.isRetryableHttpStatus(): Boolean = statusCode == 429 || statusCode in 500..599

/**
 * The delay Bitbucket asked for in its `Retry-After` header, or null when it sent none we understand.
 *
 * The header may be either a number of seconds or an HTTP date, both of which appear in the wild.
 * A value we cannot parse, cannot represent, or that has already passed yields null rather than a
 * guess, and the caller falls back to its own backoff.
 *
 * @return a non-negative delay in milliseconds, capped at the maximum, or null.
 */
private fun WebClientException.retryAfterMillis(): Long? {
    val value = retryAfter?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    value.toLongOrNull()?.let { seconds ->
        return runCatching { Duration.ofSeconds(seconds).toMillis() }
            .getOrNull()
            ?.takeIf { millis -> millis >= 0 }
            ?.coerceAtMost(MAX_RETRY_DELAY_MILLIS)
    }

    return runCatching {
        val retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        Duration.between(Instant.now(), retryAt).toMillis()
    }.getOrNull()
        ?.takeIf { millis -> millis > 0 }
        ?.coerceAtMost(MAX_RETRY_DELAY_MILLIS)
}

/**
 * Exponential backoff for a failed attempt, capped at the maximum.
 *
 * @param failedAttempt The attempt that just failed, counting from one.
 * @return the delay before the next attempt, in milliseconds.
 */
private fun backoffMillis(failedAttempt: Int): Long =
    (INITIAL_BACKOFF_MILLIS shl (failedAttempt - 1)).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
