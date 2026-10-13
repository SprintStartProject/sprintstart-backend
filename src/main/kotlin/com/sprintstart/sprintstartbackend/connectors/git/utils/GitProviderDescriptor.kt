package com.sprintstart.sprintstartbackend.connectors.git.utils

/**
 * The provider-specific half of a repository connector: where it is hosted, how a clone
 * authenticates, and how its browser URLs are spelled.
 *
 * Keeping these three together means the host appears once per provider. Before this, the Git host
 * and the clone user name were repeated in the coordinates factory while the browser URLs were
 * hard-coded again in each service that needed one, so pointing a connector at a self-hosted
 * instance meant finding every copy.
 *
 * @property host The Git host clones are read from, e.g. `bitbucket.org`. This is the Git host, not
 *           the REST API host, which is a different name for the same provider.
 * @property cloneUsername The user name HTTPS clones authenticate as. Providers accept a fixed
 *           value for token authentication, which is why a connector does not need to know which
 *           account its credential belongs to.
 * @property sourceUrls Builds the browser URLs shown on artifacts.
 */
data class GitProviderDescriptor(
    val host: String,
    val cloneUsername: String,
    val sourceUrls: GitSourceUrls,
)
