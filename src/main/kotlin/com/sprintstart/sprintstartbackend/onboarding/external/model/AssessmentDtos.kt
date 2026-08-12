package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request/response contract for the AI service's `POST /api/v1/onboarding/assessment/turn`
 * endpoint (Seam 1) — the stateless, per-turn adaptive interviewer. The backend owns the
 * transcript ([AssessmentTurnRequest.history]) and re-sends it on every turn; the AI service
 * either asks the next question (`done=false`) or returns a final per-competency placement
 * (`done=true`).
 */
@Serializable
data class CandidateCompetencySchema(
    val key: String,
    val label: String,
    val description: String = "",
    @SerialName("role_weight")
    val roleWeight: Double = 1.0,
)

@Serializable
data class RepoSignalSchema(
    val languages: List<String> = emptyList(),
    val frameworks: List<String> = emptyList(),
    val notable: List<String> = emptyList(),
)

/**
 * The candidate's own prior involvement in the project's repositories, as counted buckets.
 *
 * Consent-gated: empty unless the user opted in (see `GithubHistoryPriorService`). Distinct from
 * [RepoSignalSchema], which describes the *repository*; this describes the *person*, and the AI
 * service treats it as a calibration prior rather than evidence of proficiency.
 */
@Serializable
data class CandidateSignalSchema(
    val signals: Map<String, Int> = emptyMap(),
)

@Serializable
data class AssessmentHistoryEntrySchema(
    val role: String,
    val content: String,
)

/**
 * The candidate competency keys one past question set out to probe.
 *
 * Accumulated by the backend across the session and re-sent on every turn, because the AI service
 * holds no session state — same arrangement as [CandidateSignalSchema]. The transcript alone can't
 * supply this: a question is prose, and only the response that produced it knows which keys it was
 * aiming at. It is what lets the interviewer be refused a `done=true` that leaves candidates
 * unprobed, instead of merely counting turns.
 */
@Serializable
data class AssessmentTargetsSchema(
    val turn: Int,
    val keys: List<String>,
)

@Serializable
data class AssessmentTurnRequest(
    @SerialName("candidate_competencies")
    val candidateCompetencies: List<CandidateCompetencySchema>,
    @SerialName("repo_signal")
    val repoSignal: RepoSignalSchema = RepoSignalSchema(),
    @SerialName("candidate_signal")
    val candidateSignal: CandidateSignalSchema = CandidateSignalSchema(),
    val history: List<AssessmentHistoryEntrySchema> = emptyList(),
    val targets: List<AssessmentTargetsSchema> = emptyList(),
    val turn: Int,
    @SerialName("max_turns")
    val maxTurns: Int,
    @SerialName("must_finish")
    val mustFinish: Boolean = false,
)

@Serializable
data class AssessmentCoverageSchema(
    val key: String,
    val level: String? = null,
    val confidence: Double? = null,
)

@Serializable
data class AssessmentResultSchema(
    val key: String,
    val level: String = "beginner",
    val confidence: Double = 0.0,
    val evidence: String = "",
)

/**
 * [targets], [coverage], and [assessments] are nullable because the AI service's own schema
 * (`AssessmentTurnResponse` in `sprintstart-ai`) declares them `list[...] | None` by design --
 * `targets`/`coverage` are set only when `done=false` (still interviewing) and `assessments` only
 * when `done=true` (final placement). kotlinx.serialization's field defaults only apply when a key
 * is *absent*, not when it's explicitly `null`, so a non-nullable type here throws
 * `JsonDecodingException` on a real, valid response instead of quietly defaulting (confirmed via
 * a real assessment turn where `assessments` was explicitly `null`).
 */
@Serializable
data class AssessmentTurnResponse(
    val done: Boolean = false,
    val question: String? = null,
    val targets: List<String>? = null,
    val coverage: List<AssessmentCoverageSchema>? = null,
    val assessments: List<AssessmentResultSchema>? = null,
)
