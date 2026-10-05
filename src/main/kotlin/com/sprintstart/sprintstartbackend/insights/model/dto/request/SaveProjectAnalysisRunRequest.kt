package com.sprintstart.sprintstartbackend.insights.model.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

/**
 * How much of a project analysis the server will take.
 *
 * An analysis makes a few dozen findings over seven checks; the numbers are far above that and low
 * enough that one client cannot decide how large a stored row gets.
 */
object ProjectAnalysisLimits {
    const val FINDINGS = 200
    const val TASKS = 20
    const val SHORT_TEXT = 100
    const val TITLE = 300
    const val DETAIL = 5000
    const val LINK = 500

    /** Runs kept per project; older ones are dropped when a new one is stored. */
    const val KEPT_RUNS = 100

    /** Most runs one read returns. */
    const val MAX_READ = 50
}

/**
 * One finished analysis, as the client produced it.
 *
 * The per-severity counts and the number of failed checks are not part of the request: the server
 * derives them from [findings] and [tasks], so they can never disagree with what is stored.
 *
 * @property score The health score, 0–100, or null when a check could not run.
 */
data class SaveProjectAnalysisRunRequest(
    @field:Min(0)
    @field:Max(100)
    val score: Int?,
    @field:Size(max = ProjectAnalysisLimits.FINDINGS)
    @field:Valid
    val findings: List<AnalysisFindingPayload>,
    @field:Size(max = ProjectAnalysisLimits.TASKS)
    @field:Valid
    val tasks: List<AnalysisTaskPayload>,
)

/**
 * One finding: what the analysis noticed, how much it asks of the manager, and where to act on it.
 *
 * [area] and [to] are the client's vocabulary and are stored as given. [severity] is checked,
 * because the server counts findings by it.
 */
@Serializable
data class AnalysisFindingPayload(
    @field:NotBlank
    @field:Size(max = ProjectAnalysisLimits.SHORT_TEXT)
    val id: String,
    @field:Pattern(regexp = "critical|warning|info|good")
    val severity: String,
    @field:NotBlank
    @field:Size(max = ProjectAnalysisLimits.SHORT_TEXT)
    val area: String,
    @field:NotBlank
    @field:Size(max = ProjectAnalysisLimits.TITLE)
    val title: String,
    @field:Size(max = ProjectAnalysisLimits.DETAIL)
    val detail: String,
    @field:Size(max = ProjectAnalysisLimits.LINK)
    val to: String? = null,
)

/**
 * One of the analysis's checks and how it ended. A `failed` check means the run has no score.
 */
@Serializable
data class AnalysisTaskPayload(
    @field:NotBlank
    @field:Size(max = ProjectAnalysisLimits.SHORT_TEXT)
    val id: String,
    @field:NotBlank
    @field:Size(max = ProjectAnalysisLimits.SHORT_TEXT)
    val label: String,
    @field:Pattern(regexp = "pending|running|done|failed|skipped")
    val status: String,
    @field:Size(max = ProjectAnalysisLimits.DETAIL)
    val note: String? = null,
)
