package com.sprintstart.sprintstartbackend.github.models.exceptions

/**
 * Exception thrown when a repository was fetched but is not connected.
 */
class RepositoryNotConnectedException(
    val owner: String,
    val name: String,
) : RuntimeException(
        "Repository $owner/$name is not connected.",
    )
