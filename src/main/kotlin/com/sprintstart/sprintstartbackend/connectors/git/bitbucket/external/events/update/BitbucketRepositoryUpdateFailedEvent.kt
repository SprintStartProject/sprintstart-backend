package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update

import java.util.UUID

/**
 * Announces that an accepted update of one connected Bitbucket repository could not be started.
 *
 * This covers the window between accepting an update and launching its collectors. A failure inside
 * a collector is reported by that collector's own failed event instead, which is why this one is
 * deliberately narrow: it exists so a run that was opened by
 * [BitbucketRepositoryUpdateStartedEvent] is closed rather than left open forever when no collector
 * ever starts.
 *
 * @property transactionId The ingestion run that is being failed.
 * @property repositoryId The internal repository connection id that was being updated.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property reason A human-readable explanation of why the update could not start.
 */
data class BitbucketRepositoryUpdateFailedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val reason: String,
)
