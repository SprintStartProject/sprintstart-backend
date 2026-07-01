package com.sprintstart.sprintstartbackend.github.models.exceptions

/**
 * Exception thrown when a GitHub user pat is not found.
 */
class GithubUserPatNotFoundException(
    val name: String,
    val authId: String,
) : RuntimeException(
        "Github user pat with name $name not found for user $authId",
    )
