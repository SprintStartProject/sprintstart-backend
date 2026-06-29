package com.sprintstart.sprintstartbackend.ingestion.model.mapper

/**
 * Builds GitHub source URLs stored on ingestion artifacts and failed artifact entries.
 *
 * These URLs are user-facing references back to the upstream GitHub resource. They are separate
 * from source ids, which are internal stable keys used for deduplication.
 */
object GithubSourceUrlFactory {
    fun buildCommitUrl(repositoryOwner: String, repositoryName: String, sha: String?) =
        sha?.let {
            "https://github.com/$repositoryOwner/$repositoryName/commit/$sha"
        }

    fun buildFileUrl(repositoryOwner: String, repositoryName: String, sha: String) =
        "https://github.com/$repositoryOwner/$repositoryName/blob/$sha/"
}
