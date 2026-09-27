package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions

import java.util.UUID

/**
 * Raised when a repository is addressed but not connected to this application.
 *
 * A caller may know either the connection's id (the scheduled ingest does) or only its coordinates
 * (the config API does), so both are optional and the message names whichever was given. The id is
 * omitted rather than defaulted when a caller looked the repository up by workspace and slug.
 */
data class BitbucketRepositoryNotConnectedException(
    val id: UUID? = null,
    val workspace: String? = null,
    val slug: String? = null,
) : RuntimeException(
        "Repository '${workspace ?: "*"}/${slug ?: "*"}'" +
            (id?.let { " (id: '$it')" } ?: "") +
            " not connected to this application",
    )
