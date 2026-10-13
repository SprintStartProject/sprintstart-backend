package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request

/**
 * Represents a request to discover the repositories of one Bitbucket workspace.
 *
 * Discovery is always scoped to a single workspace, because Bitbucket retired cross-workspace
 * repository listing, so there is no counterpart to a per-user discovery request. The credential is
 * named rather than passed, because the token is resolved from the caller's stored Atlassian
 * credentials at request time and never travels through this request.
 *
 * @property workspace The workspace whose repositories should be listed.
 * @property authId The authenticated caller whose stored credential is resolved.
 * @property credentialName The name of the caller's stored Atlassian credential to read with.
 * @property page The zero-based index of the page to fetch.
 * @property pageSize The number of repositories to fetch per page.
 */
internal data class DiscoverBitbucketRepositoriesRequest(
    val workspace: String,
    val authId: String,
    val credentialName: String,
    val page: Int,
    val pageSize: Int,
)
