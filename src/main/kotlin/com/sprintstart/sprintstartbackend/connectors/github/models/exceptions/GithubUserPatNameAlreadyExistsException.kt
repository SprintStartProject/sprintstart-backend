package com.sprintstart.sprintstartbackend.connectors.github.models.exceptions

/**
 * Exception thrown when a GitHub user pat with the same name already exists.
 */
class GithubUserPatNameAlreadyExistsException(
    val name: String,
) : RuntimeException(
        "Github user pat with name $name already exists.",
    )
