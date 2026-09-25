package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.exceptions

import java.util.UUID

data class BitbucketRepositoryNotConnectedException(
    val id: UUID,
    val workspace: String? = "*",
    val slug: String? = "*",
) : RuntimeException("Repository '$workspace/$slug' (id: '$id') not connected to this application")
