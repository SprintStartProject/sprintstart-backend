package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request

internal data class ConnectBitbucketRepositoryRequest(
    val workspace: String,
    val slug: String,
    val credentialName: String,
)
