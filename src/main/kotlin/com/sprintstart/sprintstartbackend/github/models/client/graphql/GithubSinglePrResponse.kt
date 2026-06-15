package com.sprintstart.sprintstartbackend.github.models.client.graphql

import kotlinx.serialization.Serializable

@Serializable
data class GithubSinglePrResponse(
    val data: SinglePrData?,
) {
    @Serializable
    data class SinglePrData(
        val repository: RepositoryNode?,
    )

    @Serializable
    data class RepositoryNode(
        val pullRequest: PullRequest?,
    )

    val pullRequest: PullRequest?
        get() = data?.repository?.pullRequest
}
