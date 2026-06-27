package com.sprintstart.sprintstartbackend.canonical.model.mapper

object GithubSourceUrlFactory {
    fun buildCommitUrl(repositoryOwner: String, repositoryName: String, sha: String?) =
        sha?.let {
            "https://github.com/$repositoryOwner/$repositoryName/commit/$sha"
        }

    fun buildFileUrl(repositoryOwner: String, repositoryName: String, sha: String) =
        "https://github.com/$repositoryOwner/$repositoryName/blob/$sha/"
}
