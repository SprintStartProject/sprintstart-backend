package com.sprintstart.sprintstartbackend.ingestion.external.events

import java.util.UUID

/**
 * An ingestion run's artifacts have all reached the AI index and are searchable.
 *
 * ⚠️ **Not `RunFinishedEvent`.** Artifacts embed incrementally, so when that one fires the newest
 * material is still queued: generation would retrieve against an index missing the crawl and
 * compute the *old* corpus fingerprint, short-circuiting as `unchanged` and silently doing nothing.
 * The signal is the run's AI-sync roll-up first reaching `SUCCEEDED`.
 *
 * ⚠️ **Fires on that transition, not on every roll-up.** The roll-up recomputes after every drained
 * batch, and publishing on each would start a generation run per batch.
 *
 * @param projectIds Every project the run's artifacts belong to. Competencies are global, but a
 * module is written against one project's corpus, so the module pass needs to know which.
 */
data class RunIndexedEvent(
    val runId: UUID,
    val projectIds: Set<UUID>,
)
