package com.sprintstart.sprintstartbackend.ingestion.external.events

import java.util.UUID

/**
 * Published once an ingestion run's artifacts are searchable in the AI service.
 *
 * Deliberately later than `RunFinishedEvent`, which only says the run stopped fetching: at that
 * point the artifacts are stored locally but not yet indexed, so anything that asks the AI service
 * about them would be answered from a corpus that does not contain them yet. Consumers that need
 * the documents to actually exist over there — the knowledge-gap scan above all — must wait for
 * this one.
 *
 * @property projectIds every project the run's artifacts belong to. An artifact can belong to
 * several, and a run is not scoped to a project itself, so this is derived from what was ingested.
 */
data class ArtifactsIndexedEvent(
    val runId: UUID,
    val projectIds: Set<UUID>,
)
