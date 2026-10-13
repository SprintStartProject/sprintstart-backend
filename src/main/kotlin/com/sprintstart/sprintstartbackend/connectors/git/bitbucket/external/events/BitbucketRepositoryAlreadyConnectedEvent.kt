package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events

import java.util.UUID

/**
 * Announces that the addressed repository was already connected and no new fetch was started.
 *
 * Unlike its sibling events this one carries no repository id, only the coordinates: the caller
 * has just looked the connection up by them, so re-resolving them on the ingestion side costs one
 * query and keeps the connector's connect path free of an id that only matters to ingestion.
 *
 * @property transactionId The ingestion run this announcement belongs to.
 * @property workspace The Bitbucket workspace owning the already-connected repository.
 * @property slug The repository's slug within [workspace].
 */
data class BitbucketRepositoryAlreadyConnectedEvent(
    val transactionId: UUID,
    val workspace: String,
    val slug: String,
)
