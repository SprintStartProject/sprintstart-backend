package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.PhaseCheckAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerItem
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerResult
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckOption
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.entity.QuestionAttempt
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdatePhaseQuestionsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdateQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetQuestionAttemptsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.SubmitQuestionAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Manages knowledge-check questions and the attempts users make on them.
 *
 * Questions are first-class nodes of the onboarding subgraph, on the same footing as
 * steps: each one is answered on its own, owns its attempt history, and counts as passed
 * once any attempt was correct. A wrong answer never closes anything — it simply leaves
 * the question open for another try. Correct answers are only ever revealed in the result
 * of a submitted attempt, never when the question is loaded.
 */
@Suppress("TooManyFunctions")
@Service
class QuestionAttemptService(
    private val onboardingPhaseRepository: OnboardingPhaseRepository,
    private val phaseCheckQuestionRepository: PhaseCheckQuestionRepository,
    private val questionAttemptRepository: QuestionAttemptRepository,
    private val onboardingCompletionService: OnboardingCompletionService,
    private val userApi: UserApi,
    private val phaseCheckAiClient: PhaseCheckAiClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Grading outcome of one question: whether it was correct plus optional AI feedback. */
    private data class Graded(
        val correct: Boolean,
        val feedback: String? = null,
    )

//  ========================== Methods for users ==========================

    /**
     * Grades and stores one answer to a question in the authenticated user's path.
     *
     * The response reveals the correct answer, the explanation, and — for short text — the
     * AI's feedback, so the user learns from the attempt either way. A correct answer marks
     * the question passed for good and may unblock steps or questions that were waiting on
     * it, which is also when the whole journey can end.
     *
     * @param authId External authentication identifier.
     * @param questionId Identifier of the question being answered.
     * @param request The user's answer.
     * @return The graded attempt including the question's new status.
     * @throws ResponseStatusException When the user or question does not exist.
     */
    @Transactional
    @Tracked("Submitting onboarding question attempt")
    fun submitQuestionAttemptForMe(
        authId: String,
        questionId: UUID,
        request: SubmitQuestionAttemptRequest,
    ): SubmitQuestionAttemptResponse {
        val userId = resolveUserId(authId)
        val question = findQuestionForUser(questionId, userId)

        val graded = gradeQuestion(question, request)
        val attempt = QuestionAttempt(
            questionId = question.id,
            userId = userId,
            correct = graded.correct,
            selectedOptionIds = request.selectedOptionIds.toMutableList(),
            textAnswer = request.textAnswer,
        )
        val savedAttempt = questionAttemptRepository.save(attempt)

        // Only a pass can finish anything, and the attempt above is already persisted, so the
        // completion check sees it.
        val onboardingCompleted = if (graded.correct) {
            onboardingCompletionService.completeIfFinished(userId)
        } else {
            false
        }

        return SubmitQuestionAttemptResponse(
            attemptId = savedAttempt.id,
            questionId = question.id,
            correct = graded.correct,
            createdAt = savedAttempt.createdAt,
            correctOptionIds = question.options.filter { it.correct }.map { it.id },
            correctAnswer = question.correctAnswer,
            explanation = question.explanation,
            feedback = graded.feedback,
            status = if (graded.correct) QuestionStatus.PASSED else QuestionStatus.RETRY,
            onboardingCompleted = onboardingCompleted,
        )
    }

//  ========================== Methods for admins ==========================

    /**
     * Returns the questions of a phase including correct answers, for admin-facing
     * editing screens.
     *
     * @param phaseId Identifier of the phase whose questions should be loaded.
     * @return The questions including correct answers.
     * @throws ResponseStatusException When the phase does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding phase questions")
    fun getPhaseQuestions(phaseId: UUID): GetPhaseQuestionsResponse {
        return findPhase(phaseId).toPhaseQuestionsResponse()
    }

    /**
     * Replaces the questions of a phase, keeping the identity of the questions that
     * survive the edit.
     *
     * Questions carrying a known [UpdateQuestionRequest.id] are updated in place; everything
     * else in the request is created, and questions the request no longer mentions are
     * deleted. Identity is preserved rather than recreating the whole set, because stored
     * attempts reference questions by plain UUID: a question recreated instead of updated
     * takes its whole history with it, and any blocker edge pointing at it is dropped.
     *
     * @param phaseId Identifier of the phase whose questions should be replaced.
     * @param request The new questions.
     * @return The stored questions including correct answers.
     * @throws ResponseStatusException When the phase does not exist or a question is
     * invalid for its type.
     */
    @Transactional
    @Tracked("Replacing onboarding phase questions")
    fun replacePhaseQuestions(phaseId: UUID, request: UpdatePhaseQuestionsRequest): GetPhaseQuestionsResponse {
        val phase = findPhase(phaseId)

        validateQuestions(request)

        val existingById = phase.checkQuestions.associateBy { it.id }
        val keptIds = request.questions
            .mapNotNull { it.id }
            .filter { it in existingById }
            .toSet()
        val removedIds = existingById.keys - keptIds

        // Blocker edges are a many-to-many between subgraph nodes, so a removed question has to
        // be unlinked from the nodes it blocks before it disappears — otherwise the join rows
        // outlive it and steps stay blocked by a question nobody can answer any more.
        unlinkRemovedQuestions(phase, removedIds)
        phase.checkQuestions.removeIf { it.id in removedIds }

        request.questions.forEach { questionRequest ->
            val existing = questionRequest.id?.let { existingById[it] }
            if (existing == null) {
                phase.checkQuestions += questionRequest.toNewQuestion(phase)
            } else {
                existing.applyFrom(questionRequest)
            }
        }

        return onboardingPhaseRepository.save(phase).toPhaseQuestionsResponse()
    }

    /**
     * Returns every attempt a user made on one question so admins, PMs, or HR can review
     * how the answer was reached.
     *
     * @param userId Identifier of the user whose attempts should be loaded.
     * @param questionId Identifier of the question whose attempts should be loaded.
     * @return The user's attempts, newest first.
     * @throws ResponseStatusException When the user or question does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding question attempts")
    fun getQuestionAttemptsForUser(userId: UUID, questionId: UUID): GetQuestionAttemptsResponse {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }
        findQuestionForUser(questionId, userId)

        val attempts = questionAttemptRepository.findAllByQuestionIdAndUserIdOrderByCreatedAtDesc(questionId, userId)

        return GetQuestionAttemptsResponse(
            userId = userId,
            questionId = questionId,
            attempts = attempts.map { it.toResponse() },
        )
    }

//  ========================== Helper Methods ==========================

    /**
     * Drops the blocker edges pointing at questions that are about to be deleted.
     *
     * Both directions are cleared: the nodes a removed question blocked, and the nodes that
     * blocked it. Leaving either behind would keep a dangling join row for a node that no
     * longer exists.
     */
    private fun unlinkRemovedQuestions(phase: OnboardingPhase, removedIds: Set<UUID>) {
        if (removedIds.isEmpty()) return

        val removed = phase.checkQuestions.filter { it.id in removedIds }
        val remainingNodes = phase.steps + phase.checkQuestions.filterNot { it.id in removedIds }
        remainingNodes.forEach { node -> node.blockedBy.removeIf { it.id in removedIds } }
        removed.forEach { it.blockedBy.clear() }
    }

    /** Builds a brand new question, including its options for multiple choice. */
    private fun UpdateQuestionRequest.toNewQuestion(phase: OnboardingPhase): PhaseCheckQuestion {
        val created = PhaseCheckQuestion(
            phase = phase,
            position = position,
            type = type,
            question = question,
            explanation = explanation,
            correctAnswer = correctAnswer.takeIf { type == CheckQuestionType.SHORT_TEXT },
        )
        created.applyOptionsFrom(this)
        return created
    }

    /** Updates an existing question in place, so its ID — and with it its history — survives. */
    private fun PhaseCheckQuestion.applyFrom(request: UpdateQuestionRequest) {
        position = request.position
        type = request.type
        question = request.question
        explanation = request.explanation
        correctAnswer = request.correctAnswer.takeIf { request.type == CheckQuestionType.SHORT_TEXT }
        applyOptionsFrom(request)
    }

    /**
     * Merges a question's options the same way questions themselves are merged: known IDs are
     * updated, unknown ones created, and options the request dropped are deleted. Stored
     * attempts reference the selected options by UUID, so an untouched option must keep its ID.
     *
     * A question that is no longer multiple choice loses all of its options.
     */
    private fun PhaseCheckQuestion.applyOptionsFrom(request: UpdateQuestionRequest) {
        if (request.type != CheckQuestionType.MULTIPLE_CHOICE) {
            options.clear()
            return
        }

        val existingById = options.associateBy { it.id }
        val keptIds = request.options
            .mapNotNull { it.id }
            .filter { it in existingById }
            .toSet()

        options.removeIf { it.id !in keptIds }
        request.options.forEach { optionRequest ->
            val existing = optionRequest.id?.let { existingById[it] }
            if (existing == null) {
                options += PhaseCheckOption(
                    question = this,
                    position = optionRequest.position,
                    label = optionRequest.label,
                    correct = optionRequest.correct,
                )
            } else {
                existing.position = optionRequest.position
                existing.label = optionRequest.label
                existing.correct = optionRequest.correct
            }
        }
    }

    private fun resolveUserId(authId: String): UUID {
        return userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
    }

    private fun findPhase(phaseId: UUID): OnboardingPhase {
        return onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }
    }

    /**
     * Loads a question and asserts it belongs to the given user's own path, so one user can
     * never answer — or have reviewed — a question out of somebody else's onboarding.
     */
    private fun findQuestionForUser(questionId: UUID, userId: UUID): PhaseCheckQuestion {
        val question = phaseCheckQuestionRepository
            .findById(questionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No question found with id: $questionId") }

        if (question.phase.path.userId != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No question found with id: $questionId")
        }
        return question
    }

    /**
     * Grades one answer. Multiple choice is decided in process (the exact set of correct
     * options); short text is delegated to the AI service for semantic grading, because users
     * rarely type the reference answer verbatim.
     */
    private fun gradeQuestion(question: PhaseCheckQuestion, request: SubmitQuestionAttemptRequest): Graded {
        return when (question.type) {
            CheckQuestionType.MULTIPLE_CHOICE -> Graded(gradeMultipleChoice(question, request))
            CheckQuestionType.SHORT_TEXT -> gradeShortText(question, request)
        }
    }

    /** True when the selected options are exactly the correct ones. */
    private fun gradeMultipleChoice(
        question: PhaseCheckQuestion,
        request: SubmitQuestionAttemptRequest,
    ): Boolean {
        val correctOptionIds = question.options
            .filter { it.correct }
            .map { it.id }
            .toSet()
        return correctOptionIds.isNotEmpty() && request.selectedOptionIds.toSet() == correctOptionIds
    }

    /**
     * Grades a short-text answer semantically via the AI service.
     *
     * A blank answer (or a question without a reference answer) is wrong without calling the
     * AI. If the AI service is unavailable, grading falls back to a trimmed, case-insensitive
     * comparison so submitting an answer never fails on grading alone.
     */
    private fun gradeShortText(
        question: PhaseCheckQuestion,
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

    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    /** Rejects questions that cannot be answered as posed, per type. */
    private fun validateQuestions(request: UpdatePhaseQuestionsRequest) {
        request.questions.forEach { question ->
            when (question.type) {
                CheckQuestionType.MULTIPLE_CHOICE -> {
                    if (question.options.size < 2) {
                        badRequest("Multiple choice questions need at least 2 options")
                    }
                    if (question.options.none { it.correct }) {
                        badRequest("Multiple choice questions need at least 1 correct option")
                    }
                }

                CheckQuestionType.SHORT_TEXT -> {
                    if (question.correctAnswer.isNullOrBlank()) {
                        badRequest("Short text questions need a correctAnswer")
                    }
                }
            }
        }
    }
}
