package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions

data class BitbucketRepositoryConfigNotFoundException(
    val workspace: String,
    val slug: String,
) : RuntimeException("No config for Bitbucket repository $workspace/$slug found.")
