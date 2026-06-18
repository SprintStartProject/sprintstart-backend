package com.sprintstart.sprintstartbackend.github.models.exceptions

/**
 * Exception thrown when a repository to connect does not exist on `https://github.com/{owner}/{name}`.
 */
class RepositoryNotFoundException(
    owner: String,
    name: String,
) : RuntimeException(
        "Repository $owner/$name not found",
    )
