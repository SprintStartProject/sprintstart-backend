package com.sprintstart.sprintstartbackend.insights.model.dto.response

import java.time.Instant
import java.util.UUID

/**
 * One finished project analysis, complete enough to show its results again.
 *
 * Its findings and checks have response types of their own rather than reusing the request's
 * payloads, so a validation change on what may be sent never reshapes what is read back.
 *
 * @property score The health score, or null when [failedChecks] is above 0.
 * @property counts Findings per severity: `critical`, `warning`, `info`, `good`.
 */
data class ProjectAnalysisRunResponse(
    val id: UUID,
    val at: Instant,
    val score: Int?,
    val counts: Map<String, Int>,
    val failedChecks: Int,
    val findings: List<AnalysisFindingResponse>,
    val tasks: List<AnalysisTaskResponse>,
)

/** One finding of a stored analysis, as the client produced it. */
data class AnalysisFindingResponse(
    val id: String,
    val severity: String,
    val area: String,
    val title: String,
    val detail: String,
    val to: String?,
)

/** One check of a stored analysis and how it ended. */
data class AnalysisTaskResponse(
    val id: String,
    val label: String,
    val status: String,
    val note: String?,
)
