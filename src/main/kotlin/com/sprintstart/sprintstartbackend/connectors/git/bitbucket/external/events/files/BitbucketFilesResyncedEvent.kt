package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files

import java.util.UUID

/**
 * A file ingest that fell back to a full read because the stored cursor revision was missing.
 *
 * Published alongside the per-file fetched events of that full read, so a listener can reconcile
 * deletions the incremental path would otherwise have reported: stored file artifacts of the
 * repository whose path is absent from [visitedPaths] were removed while the cursor could not see
 * them.
 *
 * @property transactionId The ingestion run the fallback belongs to.
 * @property repositoryId The connected repository that was re-read in full.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property visitedPaths Every tracked path at the ingested revision, including files that were
 *           read, skipped, or reported as failures — only genuinely absent files reconcile away.
 */
data class BitbucketFilesResyncedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val visitedPaths: Set<String>,
)
