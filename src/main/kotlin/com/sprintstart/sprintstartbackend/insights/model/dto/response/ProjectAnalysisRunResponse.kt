package com.sprintstart.sprintstartbackend.insights.model.dto.response

import com.sprintstart.sprintstartbackend.insights.model.dto.request.AnalysisFindingPayload
import com.sprintstart.sprintstartbackend.insights.model.dto.request.AnalysisTaskPayload
import java.time.Instant
import java.util.UUID

/**
 * One finished project analysis, complete enough to show its results again.
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
    val findings: List<AnalysisFindingPayload>,
    val tasks: List<AnalysisTaskPayload>,
)
