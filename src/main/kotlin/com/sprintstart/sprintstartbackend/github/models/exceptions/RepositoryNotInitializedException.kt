package com.sprintstart.sprintstartbackend.github.models.exceptions

/**
 * Exception thrown when a repository is connected but not initialized yet.
 */
class RepositoryNotInitializedException(
    val owner: String,
    val name: String,
) : RuntimeException(
        "Repository $owner/$name is connected but not initialized.",
    )
