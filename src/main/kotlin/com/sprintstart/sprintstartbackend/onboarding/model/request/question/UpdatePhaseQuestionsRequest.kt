package com.sprintstart.sprintstartbackend.onboarding.model.request.question

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import java.util.UUID

/**
 * The full set of knowledge-check questions of a phase, for admin-facing editing.
 *
 * Questions and options carrying a known ID are updated in place so their identity — and
 * with it the attempt history pointing at them — survives. New questions/options are
 * created, omitted ones are deleted.
 */
data class UpdatePhaseQuestionsRequest(
    val questions: List<UpdateQuestionRequest> = emptyList(),
)

data class UpdateQuestionRequest(
    /** Identifies an existing question so it keeps its ID across the update; null creates one. */
    val id: UUID? = null,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val explanation: String? = null,
    // Only used for SHORT_TEXT questions
    val correctAnswer: String? = null,
    // Only used for MULTIPLE_CHOICE questions
    val options: List<UpdateOptionRequest> = emptyList(),
)

data class UpdateOptionRequest(
    /** Identifies an existing option so it keeps its ID; null creates one. */
    val id: UUID? = null,
    val position: Int,
    val label: String,
    val correct: Boolean,
)
