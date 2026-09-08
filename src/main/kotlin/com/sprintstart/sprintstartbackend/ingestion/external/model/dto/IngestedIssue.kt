package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import java.time.Instant

/**
 * One ingested tracker issue, with everything a person needs to judge it as starter work.
 *
 * Carries the issue's own text and labels like
 * [com.sprintstart.sprintstartbackend.ingestion.external.TaskSourceArtifact], plus [state] and [hasAssignee].
 *
 * [hasAssignee] is three-valued and null means *we do not know*, never "nobody". GitHub
 * issues have assignees this system does not ingest. A caller rendering it must say so; a caller
 * filtering on it must treat only a definite `true` as "somebody has this".
 *
 * [state] is `"OPEN"` / `"CLOSED"` as the tracker reports it, folded to those two by the mappers,
 * and null on rows ingested before state was captured — unknown, again, rather than open.
 */
data class IngestedIssue(
    val sourceId: String,
    /** Which system it came from, as a `SourceSystem` name — `GITHUB`, `JIRA`. */
    val tracker: String,
    val title: String?,
    val body: String?,
    val labels: List<String>,
    val sourceUrl: String?,
    val state: String?,
    val hasAssignee: Boolean?,
    /** When the issue last changed at its source; null when the source never said. */
    val updatedAtSource: Instant?,
)
