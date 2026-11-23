package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.path.external.PhaseCheckAiClient
import com.sprintstart.sprintstartbackend.path.external.model.GradeAnswerItem
import com.sprintstart.sprintstartbackend.path.external.model.GradeAnswerResult
import com.sprintstart.sprintstartbackend.path.model.request.question.SubmitQuestionAttemptRequest
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.UUID

/** Grading outcome of one question: whether it was correct plus optional AI feedback. */
internal data class Graded(
    val correct: Boolean,
    val feedback: String? = null,
)

/**
 * Everything grading and the response need from a question, copied out while the read
 * transaction is open.
 *
 * Question relationships are lazy JPA collections, so they would be detached by the time
 * grading runs — grading deliberately happens outside any transaction, see
 * [QuestionAttemptService.submitQuestionAttemptForMe].
 */
internal data class QuestionSnapshot(
    val id: UUID,
    val type: CheckQuestionType,
    val question: String,
    val correctOptionIds: Set<UUID>,
    val correctAnswer: String?,
    val explanation: String?,
)

/** Decides whether an answer to a knowledge-check question is correct. */
internal class AnswerGrader(
    private val phaseCheckAiClient: PhaseCheckAiClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Grades one answer. Multiple choice is decided in process (the exact set of correct
     * options); short text is delegated to the AI service for semantic grading, because users
     * rarely type the reference answer verbatim.
     */
    fun grade(question: QuestionSnapshot, request: SubmitQuestionAttemptRequest): Graded {
        return when (question.type) {
            CheckQuestionType.MULTIPLE_CHOICE -> Graded(gradeMultipleChoice(question, request))
            CheckQuestionType.SHORT_TEXT -> gradeShortText(question, request)
        }
    }

    /** True when the selected options are exactly the correct ones. */
    private fun gradeMultipleChoice(
        question: QuestionSnapshot,
        request: SubmitQuestionAttemptRequest,
    ): Boolean {
        return question.correctOptionIds.isNotEmpty() &&
            request.selectedOptionIds.toSet() == question.correctOptionIds
    }

    /**
     * Grades a short-text answer semantically via the AI service.
     *
     * A blank answer (or a question without a reference answer) is wrong without calling the
     * AI. If the AI service is unavailable, grading falls back to a trimmed, case-insensitive
     * comparison so submitting an answer never fails on grading alone.
     */
    private fun gradeShortText(
        question: QuestionSnapshot,
        request: SubmitQuestionAttemptRequest,
    ): Graded {
        val answer = request.textAnswer?.trim()
        val reference = question.correctAnswer?.trim()
        if (answer.isNullOrBlank() || reference.isNullOrBlank()) return Graded(correct = false)

        val item = GradeAnswerItem(
            id = question.id.toString(),
            question = question.question,
            referenceAnswer = reference,
            userAnswer = answer,
        )
        val ai = gradeWithAi(listOf(item))?.get(item.id)

        return if (ai != null) {
            Graded(correct = ai.correct, feedback = ai.feedback.ifBlank { null })
        } else {
            Graded(correct = answer.equals(reference, ignoreCase = true))
        }
    }

    /**
     * Calls the AI grading service, returning results keyed by correlation id, or `null`
     * when the service is unavailable so the caller can fall back.
     */
    private fun gradeWithAi(items: List<GradeAnswerItem>): Map<String, GradeAnswerResult>? =
        try {
            runBlocking { phaseCheckAiClient.gradeAnswers(items) }.associateBy { it.id }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("AI short-text grading unavailable, falling back to exact match: {}", e.message)
            null
        }
}
