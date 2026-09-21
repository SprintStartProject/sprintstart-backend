package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.request

internal data class ConnectBitbucketRepositoryRequest(
    val workspace: String,
    val slug: String,
    val credentialName: String,
)
