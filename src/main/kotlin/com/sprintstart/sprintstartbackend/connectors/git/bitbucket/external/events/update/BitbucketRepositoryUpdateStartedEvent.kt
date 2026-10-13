package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update

import java.util.UUID

/**
 * Announces that an update of one connected Bitbucket repository has been accepted.
 *
 * Published before the collectors start, which is what makes the ingestion run exist from the moment
 * the update is taken on rather than from the first collector that manages to report in. A collector
 * that fails before publishing its own started event — a credential that can no longer be resolved,
 * above all — would otherwise leave the run unattributed or absent.
 *
 * Carries the repository id directly, unlike the GitHub connector's counterpart, which has to
 * resolve it from the owner and name it already holds. The id is known here anyway, and passing it
 * on means the listener needs no module API to open the run.
 *
 * @property transactionId The ingestion run this update reports under.
 * @property repositoryId The internal repository connection id being updated.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 */
data class BitbucketRepositoryUpdateStartedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
)
