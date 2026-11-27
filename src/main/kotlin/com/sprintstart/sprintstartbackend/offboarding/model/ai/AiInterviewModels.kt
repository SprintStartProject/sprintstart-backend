package com.sprintstart.sprintstartbackend.offboarding.model.ai

import kotlinx.serialization.Serializable

/**
 * What the AI service needs to plan an exit interview: the files at risk with their findings.
 *
 * The request names no person. The reasons of the risk map name nobody either, so nothing about who
 * leaves or who stays reaches the AI service.
 */
@Serializable
data class AiInterviewRequest(
    val projectId: String,
    val files: List<AiInterviewFile>,
)

/** One file at risk as the AI service reads it; see the risk map for what each value means. */
@Serializable
data class AiInterviewFile(
    val repository: String,
    val path: String,
    val level: String,
    val score: Double,
    val kind: String? = null,
    val hotspot: Double? = null,
    val abandonedShare: Double? = null,
    val coupledWith: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
)

/** The interview plan: questions to ask, and the ones the project's records already answer. */
@Serializable
data class AiInterviewResponse(
    val questions: List<AiInterviewQuestion>,
    val answeredByRecords: List<AiInterviewQuestion> = emptyList(),
)

/**
 * One question of the plan.
 *
 * @property type `rationale`, `operational`, `gotcha` or `handover`.
 * @property triggers The findings of the risk map the question answers.
 * @property coverage `open`, `partial` or `answered`: how much the records already say.
 * @property recordAnswer What the records say, when they say anything.
 * @property sources The documents the record answer comes from.
 */
@Serializable
data class AiInterviewQuestion(
    val component: String,
    val text: String,
    val type: String,
    val triggers: List<String>,
    val files: List<String>,
    val priority: Double,
    val coverage: String,
    val recordAnswer: String? = null,
    val sources: List<String> = emptyList(),
)
