package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events

import java.util.UUID

/**
 * Announces that connecting a Bitbucket repository failed before any ingestion began.
 *
 * The coordinates are the ones that were requested, not ones that were confirmed: the two failure
 * causes — a missing credential and an unreachable repository — both happen before the repository
 * is verified, and the run history still has to show which repository was asked for.
 *
 * @property transactionId The ingestion run that is being failed.
 * @property workspace The requested Bitbucket workspace.
 * @property slug The requested repository slug.
 * @property reason A human-readable explanation of the failure.
 */
data class BitbucketRepositoryConnectionFailedEvent(
    val transactionId: UUID,
    val workspace: String,
    val slug: String,
    val reason: String,
)
