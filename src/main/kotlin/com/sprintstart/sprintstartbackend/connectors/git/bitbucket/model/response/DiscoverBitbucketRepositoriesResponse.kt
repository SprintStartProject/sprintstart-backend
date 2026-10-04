package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response

/**
 * Response for the workspace discovery endpoint.
 *
 * @property repositories The repositories on the requested page.
 */
data class DiscoverBitbucketRepositoriesResponse(
    val repositories: List<DiscoveredBitbucketRepository>,
)

/**
 * One repository of a workspace as reported by discovery.
 *
 * Discovery exists to feed a connect request, so the coordinates a connect needs are always
 * present: [workspace] and [slug]. [alreadyConnected] and [enabled] are filled in after the page is
 * fetched, which is why they are mutable and defaulted rather than part of the constructor's
 * required shape.
 *
 * @property workspace The workspace owning the repository.
 * @property slug The repository's slug within its workspace.
 * @property name The repository's display name.
 * @property isPrivate Whether the repository is private.
 * @property url The browser URL of the repository, or null when Bitbucket reported no link.
 * @property alreadyConnected Whether the application already has a connection for these coordinates.
 * @property enabled Whether that connection is enabled, or null when the repository is not connected.
 */
data class DiscoveredBitbucketRepository(
    val workspace: String,
    val slug: String,
    val name: String,
    val isPrivate: Boolean,
    val url: String?,
    var alreadyConnected: Boolean = false,
    var enabled: Boolean? = null,
)
