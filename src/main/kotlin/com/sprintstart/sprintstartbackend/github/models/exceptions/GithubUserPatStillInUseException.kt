package com.sprintstart.sprintstartbackend.github.models.exceptions

/**
 * Thrown when a user tries to delete a pat that's still in use.
 */
class GithubUserPatStillInUseException(
    val name: String,
) : RuntimeException(
        "Github user pat with name $name is still in use.",
    )
