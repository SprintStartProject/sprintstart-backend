package com.sprintstart.sprintstartbackend.canonical.model.mapper

object GithubSourceUrlFactory {
    fun commitUrl(repositoryOwner: String, repositoryName: String, sha: String) =
        "https://github.com/${repositoryOwner}/${repositoryName}/commit/${sha}"

    fun commitUrl(repositoryOwner: String, repositoryName: String, sha: String) =
        "https://github.com/${repositoryOwner}/${repositoryName}/commit/${sha}"
}