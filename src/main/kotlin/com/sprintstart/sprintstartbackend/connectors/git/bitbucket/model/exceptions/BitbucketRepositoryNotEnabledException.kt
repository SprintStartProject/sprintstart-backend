package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions

/**
 * Raised when an update is requested for a repository whose source is disabled.
 *
 * Disabling a source is how a caller pauses ingestion while keeping the connection and its already
 * collected artifacts. An explicit update of such a repository is therefore refused rather than
 * silently performed, so the caller learns that nothing was ingested instead of having to infer it
 * from an unchanged run history.
 *
 * Deliberately not a failure of the source: the refusal is the caller's request being declined, not
 * the repository being broken, so no ingestion run is opened for it.
 */
class BitbucketRepositoryNotEnabledException(
    val workspace: String,
    val slug: String,
) : RuntimeException(
        "Bitbucket repository '$workspace/$slug' is disabled and cannot be updated",
    )
