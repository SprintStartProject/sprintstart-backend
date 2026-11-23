package com.sprintstart.sprintstartbackend.path.factory

import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType

/** AI-generated content for one `AI_ENHANCED` blueprint phase, filled at personalization time. */
data class GeneratedPhaseContent(
    val steps: List<GeneratedStep> = emptyList(),
    val checkQuestions: List<GeneratedQuestion> = emptyList(),
)

/**
 * AI-generated step content as returned by the phase-assembly endpoint.
 *
 * [key] and [blockedBy] carry the optional dependency edges the model drew
 * between the assembled items: [blockedBy] holds the keys of steps/questions
 * that must be completed first. Keys are scoped to one assembled phase; a key
 * that resolves to nothing (unknown, self, or an item grounding dropped) is
 * ignored when the edges are wired onto the onboarding phase.
 */
data class GeneratedStep(
    val key: String = "",
    val title: String,
    val description: String = "",
    val tasks: List<GeneratedTask> = emptyList(),
    val resources: List<GeneratedResource> = emptyList(),
    val estimatedMinutes: Int? = null,
    val expectedOutcome: String = "",
    val blockedBy: List<String> = emptyList(),
)

data class GeneratedTask(
    val title: String,
    val description: String = "",
)

data class GeneratedResource(
    val title: String,
    val url: String,
)

/**
 * AI-generated knowledge-check question as returned by the phase-assembly endpoint.
 *
 * [key] and [blockedBy] mirror [GeneratedStep]: optional dependency edges,
 * resolved by key against the phase's assembled items.
 */
data class GeneratedQuestion(
    val key: String = "",
    val type: CheckQuestionType,
    val question: String,
    val explanation: String? = null,
    val correctAnswer: String? = null,
    val options: List<GeneratedOption> = emptyList(),
    val blockedBy: List<String> = emptyList(),
)

data class GeneratedOption(
    val label: String,
    val correct: Boolean = false,
)
