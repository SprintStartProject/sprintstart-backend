package com.sprintstart.sprintstartbackend.offboarding.model.response

/**
 * The questions worth a leaver's time, planned from the risk map of their departure.
 *
 * @property questions What to ask, most urgent first.
 * @property answeredByRecords What the project's records already answer, so it is not asked.
 * @property files How many files at risk the plan was made from.
 */
data class InterviewPlanResponse(
    val questions: List<InterviewQuestionResponse>,
    val answeredByRecords: List<InterviewQuestionResponse>,
    val files: Int,
)

/**
 * One question of an interview plan.
 *
 * @property component The repository and directory the question is about.
 * @property type `rationale`, `operational`, `gotcha` or `handover`.
 * @property triggers The findings of the risk map that make it worth asking.
 * @property files The files it is about.
 * @property priority How urgent it is, from 0 to 1.
 * @property coverage `open`, `partial` or `answered`: how much the project's records already say.
 * @property recordAnswer What the records say, when they say anything.
 * @property sources The documents [recordAnswer] comes from.
 */
data class InterviewQuestionResponse(
    val component: String,
    val text: String,
    val type: String,
    val triggers: List<String>,
    val files: List<String>,
    val priority: Double,
    val coverage: String,
    val recordAnswer: String?,
    val sources: List<String>,
)
