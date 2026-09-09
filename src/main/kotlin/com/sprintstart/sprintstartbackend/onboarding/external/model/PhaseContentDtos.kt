package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the AI service's `POST /api/v1/onboarding/phase[+/stream]` endpoints — filling an
 * `AI_ENHANCED` blueprint phase with content generated from the project's corpus.
 *
 * The AI service is a stateless reasoner scoped to [AssemblePhaseRequest.projectId]: it retrieves
 * the project's own material, then assembles grounded steps (with tasks/resources) and a small
 * knowledge check. The backend owns persistence and copies the assembled content onto the user's
 * onboarding phase.
 */
@Serializable
data class AssemblePhaseRequest(
    @SerialName("phase_title") val phaseTitle: String,
    @SerialName("phase_description") val phaseDescription: String = "",
    @SerialName("phase_prompt") val phasePrompt: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("last_fingerprint") val lastFingerprint: String? = null,
)

@Serializable
data class PhaseTaskDto(
    val title: String,
    val description: String = "",
)

@Serializable
data class PhaseResourceDto(
    val title: String,
    val url: String,
)

@Serializable
data class PhaseStepDto(
    val title: String,
    val description: String = "",
    val tasks: List<PhaseTaskDto> = emptyList(),
    val resources: List<PhaseResourceDto> = emptyList(),
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
    @SerialName("expected_outcome") val expectedOutcome: String = "",
)

@Serializable
data class PhaseCheckOptionDto(
    val position: Int = 0,
    val label: String,
    val correct: Boolean = false,
)

@Serializable
data class PhaseCheckQuestionDto(
    val position: Int = 0,
    val type: String,
    val question: String,
    val explanation: String? = null,
    @SerialName("correct_answer") val correctAnswer: String? = null,
    val options: List<PhaseCheckOptionDto> = emptyList(),
)

@Serializable
data class AiProvenanceDto(
    @SerialName("corpus_fingerprint") val corpusFingerprint: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    val model: String? = null,
    val notes: List<String> = emptyList(),
)

@Serializable
data class PhaseContentOutcome(
    val status: String,
    val steps: List<PhaseStepDto> = emptyList(),
    @SerialName("check_questions") val checkQuestions: List<PhaseCheckQuestionDto> = emptyList(),
    val provenance: AiProvenanceDto? = null,
    @SerialName("chunks_retrieved") val chunksRetrieved: Int = 0,
    @SerialName("chunks_collapsed") val chunksCollapsed: Int = 0,
    @SerialName("steps_dropped") val stepsDropped: Int = 0,
    @SerialName("questions_dropped") val questionsDropped: Int = 0,
    val notes: List<String> = emptyList(),
)
