package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.exceptions

data class BitbucketRepositoryDoesNotExistException(
    val workspace: String,
    val slug: String,
    val credentialName: String,
) : RuntimeException(
        "Bitbucket repository '$workspace/$slug' either does not exist or is unreachable using the given credential: '$credentialName'",
    )
