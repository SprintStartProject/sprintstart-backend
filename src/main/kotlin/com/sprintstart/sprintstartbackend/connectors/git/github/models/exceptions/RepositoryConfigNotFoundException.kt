package com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions

data class RepositoryConfigNotFoundException(
    val owner: String,
    val name: String,
) : RuntimeException("No config for GitHub repository $owner/$name found.")
