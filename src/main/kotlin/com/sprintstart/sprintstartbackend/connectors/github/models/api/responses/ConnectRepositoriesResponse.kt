package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import java.util.UUID

/**
 * Response for the bulk connect endpoint.
 *
 * @property transactionIdsByRepositoryId Connection transaction per `owner/name`.
 * @property reusedRepositoryIds The `owner/name` entries that reused an existing connection and
 * therefore started no ingestion run. Carries no information about the projects those connections
 * already belong to.
 */
data class ConnectRepositoriesResponse(
    val transactionIdsByRepositoryId: Map<String, UUID>,
    val reusedRepositoryIds: Set<String> = emptySet(),
)
