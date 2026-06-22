package com.sprintstart.sprintstartbackend.github.models.exceptions

class GithubUserPatNameAlreadyExistsException(
    val name: String,
) : RuntimeException(
        "Github user pat with name $name already exists.",
    )
