package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files

import java.util.UUID

/**
 * One file that no longer exists in the ingested revision of a Bitbucket repository.
 *
 * Published instead of a [BitbucketFileFetchedEvent] with empty content, so a listener can remove
 * the artifact rather than overwrite it with nothing.
 *
 * @property transactionId The ingestion run this file belongs to.
 * @property repositoryId The connected repository the file was removed from.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property path The repository-relative path of the removed file.
 */
data class BitbucketFileDeletedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val path: String,
)
