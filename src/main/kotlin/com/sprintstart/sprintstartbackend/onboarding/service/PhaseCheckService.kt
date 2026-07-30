package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.PhaseCheckAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerItem
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerResult
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckAttempt
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckOption
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckReviewItem
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.stepsCompleted
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCheckForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCheckResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCheckSummaryResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.SubmitCheckAnswerRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.SubmitPhaseCheckAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdatePhaseCheckRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckAnswerResultResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckAttemptsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.SubmitPhaseCheckAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckAttemptRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckReviewItemRepository
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
 * Manages phase-level knowledge checks.
 *
 * A knowledge check belongs to an onboarding phase (not to individual steps) and
 * consists of the phase's check questions. A phase counts as "checked" once any
 * submitted attempt passed. Correct answers are only revealed in submit results,
 * never when loading the check.
 */
@Suppress("TooManyFunctions")
@Service
class PhaseCheckService(
    private val onboardingPhaseRepository: OnboardingPhaseRepository,
    private val phaseCheckAttemptRepository: PhaseCheckAttemptRepository,
    private val phaseCheckQuestionRepository: PhaseCheckQuestionRepository,
    private val phaseCheckReviewItemRepository: PhaseCheckReviewItemRepository,
    private val userApi: UserApi,
    private val phaseCheckAiClient: PhaseCheckAiClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private companion object {
        /** Minimum percentage of correct questions required to pass a phase check. */
        const val PASS_PERCENT = 80
        const val PERCENT = 100
    }

    /** Grading outcome of one question: whether it was correct plus optional AI feedback. */
    private data class Graded(
        val correct: Boolean,
        val feedback: String? = null,
    )

//  ========================== Methods for users ==========================

    /**
     * Returns the knowledge check of a phase in the authenticated user's path,
     * without exposing correct answers.
     *
     * @param authId External authentication identifier.
     * @param phaseId Identifier of the phase whose check should be loaded.
     * @return The check questions to render, without correct answers.
     * @throws ResponseStatusException When the user or phase does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding phase check")
    fun getPhaseCheckForMe(authId: String, phaseId: UUID): GetPhaseCheckForUserResponse {
        val userId = resolveUserId(authId)
        val phase = findPhaseForUser(phaseId, userId)

        val base = phase.toCheckForUserResponse()
        val reviewQuestions = loadOpenReviewQuestions(userId, phaseId).map { (_, question) ->
            question.toForUserResponse().copy(review = true, reviewSourcePhaseTitle = question.phase.title)
        }

        return base.copy(questions = base.questions + reviewQuestions)
    }

    /**
     * Grades and stores a knowledge check attempt for a phase in the authenticated
     * user's path.
     *
     * The attempt passes when every question is answered correctly. The response
     * reveals the correct answers per question so the frontend can show the result.
     *
     * @param authId External authentication identifier.
     * @param phaseId Identifier of the phase whose check is being taken.
     * @param request The user's answers.
     * @return The graded attempt including per-question results.
     * @throws ResponseStatusException When the user or phase does not exist or the
     * phase has no knowledge check.
     */
    @Transactional
    @Tracked("Submitting onboarding phase check")
    fun submitPhaseCheckAttemptForMe(
        authId: String,
        phaseId: UUID,
        request: SubmitPhaseCheckAttemptRequest,
    ): SubmitPhaseCheckAttemptResponse {
        val userId = resolveUserId(authId)
        val phase = findPhaseForUser(phaseId, userId)

        if (phase.checkQuestions.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No knowledge check configured for phase: $phaseId")
        }

        val answersByQuestionId = request.answers.associateBy { it.questionId }
        val ownQuestions = phase.checkQuestions.sortedBy { it.position }
        // Carried-over repeat questions from earlier phases the user must also answer here.
        val reviewPairs = loadOpenReviewQuestions(userId, phaseId)
        val graded = gradeQuestions(ownQuestions + reviewPairs.map { it.second }, answersByQuestionId)

        val ownResults = ownQuestions.map { question ->
            val outcome = graded.getValue(question.id)
            question.toResultResponse(correct = outcome.correct, feedback = outcome.feedback)
        }
        val reviewResults = reviewPairs.map { (_, question) ->
            val outcome = graded.getValue(question.id)
            question
                .toResultResponse(correct = outcome.correct, feedback = outcome.feedback)
                .copy(review = true, reviewSourcePhaseTitle = question.phase.title)
        }

        // Only the phase's own questions count toward the pass threshold; repeats are
        // an extra verification and never block passing (integer math avoids float surprises).
        val correctCount = ownResults.count { it.correct }
        val passed = correctCount * PERCENT >= ownQuestions.size * PASS_PERCENT

        val attempt = PhaseCheckAttempt(phase = phase, userId = userId, passed = passed)
        (ownQuestions + reviewPairs.map { it.second }).forEach { question ->
            val submitted = answersByQuestionId[question.id]
            attempt.answers += PhaseCheckAnswer(
                attempt = attempt,
                questionId = question.id,
                selectedOptionIds = submitted?.selectedOptionIds?.toMutableList() ?: mutableListOf(),
                textAnswer = submitted?.textAnswer,
                correct = graded.getValue(question.id).correct,
            )
        }
        // save() merges (the id is client-assigned), so use the returned managed instance.
        // The attempt is NOT added to phase.checkAttempts by hand: that would put a detached
        // copy into a cascade collection and fail the flush with a NonUniqueObjectException.
        // toCheckSummaryResponse() below lazily reloads the collection, which includes this attempt.
        val savedAttempt = phaseCheckAttemptRepository.save(attempt)

        if (passed) {
            applyCarryOver(phase, userId, ownQuestions, reviewPairs, graded)
            // Passing the path's final knowledge check completes the whole onboarding
            // journey: promote the user so the onboarding UI is hidden for them.
            if (phase.isLastCheckPhase()) {
                userApi.markOnboardingCompleted(userId)
            }
        }

        return SubmitPhaseCheckAttemptResponse(
            attemptId = savedAttempt.id,
            phaseId = phase.id,
            passed = passed,
            createdAt = savedAttempt.createdAt,
            correctCount = correctCount,
            questionCount = ownQuestions.size,
            requiredPercent = PASS_PERCENT,
            phaseCheckSummary = phase.toCheckSummaryResponse(),
            nextPhaseUnlocked = passed && phase.stepsCompleted() && phase.hasNextPhase(),
            results = ownResults + reviewResults,
        )
    }

//  ========================== Methods for admins ==========================

    /**
     * Returns the knowledge check of a phase including correct answers, for
     * admin-facing editing screens.
     *
     * @param phaseId Identifier of the phase whose check should be loaded.
     * @return The check questions including correct answers.
     * @throws ResponseStatusException When the phase does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding phase check")
    fun getPhaseCheck(phaseId: UUID): GetPhaseCheckResponse {
        return findPhase(phaseId).toCheckResponse()
    }

    /**
     * Replaces all knowledge check questions of a phase.
     *
     * Existing questions are removed and the submitted questions become the new
     * check. Submitted attempts are kept as history; their answers reference the
     * old question IDs.
     *
     * @param phaseId Identifier of the phase whose check should be replaced.
     * @param request The new check questions.
     * @return The stored check questions including correct answers.
     * @throws ResponseStatusException When the phase does not exist or a question
     * is invalid for its type.
     */
    @Transactional
    @Tracked("Replacing onboarding phase check")
    fun replacePhaseCheck(phaseId: UUID, request: UpdatePhaseCheckRequest): GetPhaseCheckResponse {
        val phase = findPhase(phaseId)

        validateQuestions(request)

        phase.checkQuestions.clear()
        request.questions.sortedBy { it.position }.forEach { questionRequest ->
            val question = PhaseCheckQuestion(
                phase = phase,
                position = questionRequest.position,
                type = questionRequest.type,
                question = questionRequest.question,
                explanation = questionRequest.explanation,
                correctAnswer = questionRequest.correctAnswer
                    .takeIf { questionRequest.type == CheckQuestionType.SHORT_TEXT },
            )
            if (questionRequest.type == CheckQuestionType.MULTIPLE_CHOICE) {
                questionRequest.options.sortedBy { it.position }.forEach { optionRequest ->
                    question.options += PhaseCheckOption(
                        question = question,
                        position = optionRequest.position,
                        label = optionRequest.label,
                        correct = optionRequest.correct,
                    )
                }
            }
            phase.checkQuestions += question
        }

        return onboardingPhaseRepository.save(phase).toCheckResponse()
    }

    /**
     * Returns all submitted knowledge check attempts of a user for one phase so
     * admins, PMs, or HR can review the results.
     *
     * @param userId Identifier of the user whose attempts should be loaded.
     * @param phaseId Identifier of the phase whose attempts should be loaded.
     * @return The user's attempts, newest first.
     * @throws ResponseStatusException When the user or phase does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding phase check attempts")
    fun getPhaseCheckAttemptsForUser(userId: UUID, phaseId: UUID): GetPhaseCheckAttemptsResponse {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }
        val phase = findPhaseForUser(phaseId, userId)

        val attempts = phaseCheckAttemptRepository.findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(phaseId, userId)

        return GetPhaseCheckAttemptsResponse(
            userId = userId,
            phaseId = phaseId,
            attempts = attempts.map { it.toGetResponse(questionCount = phase.checkQuestions.size) },
        )
    }

//  ========================== Helper Methods ==========================

    /**
     * Resolves the user ID corresponding to the provided authentication ID.
     *
     * @param authId The authentication ID used to locate the user.
     * @return The UUID of the user corresponding to the given authentication ID.
     * @throws ResponseStatusException if no user is found for the provided authentication ID.
     */
    private fun resolveUserId(authId: String): UUID {
        return userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
    }

    /**
     * Finds an onboarding phase based on the provided phase ID.
     *
     * @param phaseId the unique identifier of the onboarding phase to retrieve
     * @return the onboarding phase associated with the given phase ID
     * @throws ResponseStatusException if no onboarding phase is found with the specified phase ID
     */
    private fun findPhase(phaseId: UUID): OnboardingPhase {
        return onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }
    }

    /**
     * Finds an onboarding phase associated with a specific user.
     *
     * @param phaseId The unique identifier of the onboarding phase to retrieve.
     * @param userId The unique identifier of the user associated with the onboarding phase.
     * @return The onboarding phase associated with the given phase ID and user ID.
     * @throws ResponseStatusException if no onboarding phase is found for the provided IDs.
     */
    private fun findPhaseForUser(phaseId: UUID, userId: UUID): OnboardingPhase {
        return onboardingPhaseRepository
            .findByIdAndPathUserId(phaseId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }
    }

    /**
     * Grades every question of an attempt, keyed by question id.
     *
     * Multiple choice is graded deterministically in process (exact set of correct
     * options). Short text is delegated to the AI service for semantic grading in a
     * single batch call, because users rarely type the reference answer verbatim.
     */
    private fun gradeQuestions(
        questions: List<PhaseCheckQuestion>,
        answersByQuestionId: Map<UUID, SubmitCheckAnswerRequest>,
    ): Map<UUID, Graded> {
        val multipleChoice = questions
            .filter { it.type == CheckQuestionType.MULTIPLE_CHOICE }
            .associate { it.id to Graded(gradeMultipleChoice(it, answersByQuestionId[it.id])) }

        val shortText = gradeShortText(
            questions.filter { it.type == CheckQuestionType.SHORT_TEXT },
            answersByQuestionId,
        )

        return multipleChoice + shortText
    }

    /**
     * Grades a multiple-choice question by comparing the selected answers with the correct answers.
     *
     * @param question The multiple-choice question containing the list of options and their correctness.
     * @param answer The submitted answer containing the selected option IDs, or null if no answer was submitted.
     * @return A boolean indicating whether the submitted answer matches the correct answers.
     */
    private fun gradeMultipleChoice(question: PhaseCheckQuestion, answer: SubmitCheckAnswerRequest?): Boolean {
        if (answer == null) return false
        val correctOptionIds = question.options
            .filter { it.correct }
            .map { it.id }
            .toSet()
        return answer.selectedOptionIds.toSet() == correctOptionIds
    }

    /**
     * Grades short-text answers semantically via the AI service in one batch.
     *
     * Blank answers (or questions without a reference answer) are marked incorrect
     * without calling the AI. If the AI service is unavailable, grading falls back to
     * a trimmed, case-insensitive comparison so that submitting an attempt never fails
     * on grading alone.
     */
    private fun gradeShortText(
        questions: List<PhaseCheckQuestion>,
        answersByQuestionId: Map<UUID, SubmitCheckAnswerRequest>,
    ): Map<UUID, Graded> {
        if (questions.isEmpty()) return emptyMap()

        val graded = mutableMapOf<UUID, Graded>()
        val toGrade = mutableListOf<GradeAnswerItem>()
        questions.forEach { question ->
            val answer = answersByQuestionId[question.id]?.textAnswer?.trim()
            val reference = question.correctAnswer?.trim()
            if (answer.isNullOrBlank() || reference.isNullOrBlank()) {
                graded[question.id] = Graded(correct = false)
            } else {
                toGrade += GradeAnswerItem(
                    id = question.id.toString(),
                    question = question.question,
                    referenceAnswer = reference,
                    userAnswer = answer,
                )
            }
        }
        if (toGrade.isEmpty()) return graded

        val aiResults = gradeWithAi(toGrade)
        toGrade.forEach { item ->
            val questionId = UUID.fromString(item.id)
            val ai = aiResults?.get(item.id)
            graded[questionId] = if (ai != null) {
                Graded(correct = ai.correct, feedback = ai.feedback.ifBlank { null })
            } else {
                // AI unavailable: fall back to a deterministic comparison.
                Graded(correct = item.userAnswer.equals(item.referenceAnswer, ignoreCase = true))
            }
        }
        return graded
    }

    /**
     * Calls the AI grading service, returning results keyed by correlation id, or
     * `null` when the service is unavailable so the caller can fall back.
     */
    private fun gradeWithAi(items: List<GradeAnswerItem>): Map<String, GradeAnswerResult>? =
        try {
            runBlocking { phaseCheckAiClient.gradeAnswers(items) }.associateBy { it.id }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("AI short-text grading unavailable, falling back to exact match: {}", e.message)
            null
        }

    /**
     * Converts the PhaseCheckQuestion instance into a CheckAnswerResultResponse object.
     *
     * @param correct Indicates whether the answer is correct.
     * @param feedback Optional feedback message associated with the question.
     * @return A CheckAnswerResultResponse object containing the result details.
     */
    private fun PhaseCheckQuestion.toResultResponse(
        correct: Boolean,
        feedback: String? = null,
    ): CheckAnswerResultResponse {
        return CheckAnswerResultResponse(
            questionId = this.id,
            correct = correct,
            correctOptionIds = options.filter { it.correct }.map { it.id },
            correctAnswer = this.correctAnswer,
            explanation = this.explanation,
            feedback = feedback,
        )
    }

    /**
     * Determines if there is a subsequent phase in the onboarding process.
     *
     * @return true if there is a phase with a higher position than the current phase, false otherwise.
     */
    private fun OnboardingPhase.hasNextPhase(): Boolean {
        return path.phases.any { it.position > this.position }
    }

    /**
     * Whether this phase carries the path's final knowledge check, i.e. no later phase
     * (higher position) has its own check. Passing this phase's check therefore
     * completes the entire onboarding journey.
     *
     * @return true if no subsequent phase has a knowledge check, false otherwise.
     */
    private fun OnboardingPhase.isLastCheckPhase(): Boolean {
        return path.phases.none { it.position > this.position && it.checkQuestions.isNotEmpty() }
    }

    /**
     * Determines the next phase in the onboarding process based on the current phase's position.
     *
     * It searches through the phases with a position greater than the current phase's position
     * and selects the one with the smallest position (i.e., the closest subsequent phase).
     *
     * @return The next OnboardingPhase in the sequence, or null if no subsequent phase exists.
     */
    private fun OnboardingPhase.nextPhase(): OnboardingPhase? =
        path.phases.filter { it.position > this.position }.minByOrNull { it.position }

    /**
     * Loads the open (unresolved) carried-over questions the user must re-answer in the
     * given phase, paired with their original [PhaseCheckQuestion]. Items whose question
     * no longer exists are skipped.
     */
    private fun loadOpenReviewQuestions(
        userId: UUID,
        phaseId: UUID,
    ): List<Pair<PhaseCheckReviewItem, PhaseCheckQuestion>> {
        val items = phaseCheckReviewItemRepository
            .findAllByUserIdAndTargetPhaseIdAndResolvedFalseOrderByCreatedAtAsc(userId, phaseId)
        if (items.isEmpty()) return emptyList()

        val questionsById = phaseCheckQuestionRepository
            .findAllById(items.map { it.questionId })
            .associateBy { it.id }

        return items.mapNotNull { item -> questionsById[item.questionId]?.let { item to it } }
    }

    /**
     * Updates carry-over state after a phase was passed.
     *
     * Repeat questions shown in this attempt are resolved when answered correctly and
     * advanced to the next phase when answered incorrectly. The phase's own questions
     * answered incorrectly in any attempt (so understanding is verified even after a
     * lucky retry) are carried over to the next phase. When there is no next phase,
     * nothing new is carried and any remaining repeats are dropped.
     */
    private fun applyCarryOver(
        phase: OnboardingPhase,
        userId: UUID,
        ownQuestions: List<PhaseCheckQuestion>,
        reviewPairs: List<Pair<PhaseCheckReviewItem, PhaseCheckQuestion>>,
        graded: Map<UUID, Graded>,
    ) {
        val nextPhase = phase.nextPhase()

        reviewPairs.forEach { (item, question) ->
            when {
                graded[question.id]?.correct == true -> item.resolved = true
                nextPhase != null -> item.targetPhaseId = nextPhase.id
                else -> item.resolved = true
            }
        }
        phaseCheckReviewItemRepository.saveAll(reviewPairs.map { it.first })

        if (nextPhase == null) return
        everWrongOwnQuestionIds(phase.id, userId, ownQuestions).forEach { questionId ->
            val alreadyOpen = phaseCheckReviewItemRepository
                .findAllByUserIdAndQuestionIdAndResolvedFalse(userId, questionId)
                .isNotEmpty()
            if (!alreadyOpen) {
                phaseCheckReviewItemRepository.save(
                    PhaseCheckReviewItem(
                        userId = userId,
                        questionId = questionId,
                        sourcePhaseId = phase.id,
                        targetPhaseId = nextPhase.id,
                    ),
                )
            }
        }
    }

    /**
     * Returns IDs of the user's own questions that were answered incorrectly
     * in a specific phase.
     *
     * @param phaseId the unique identifier of the phase.
     * @param userId the unique identifier of the user.
     * @param ownQuestions the list of questions that are considered the user's own.
     * @return a set of unique question IDs that were answered incorrectly among the user's own questions.
     */
    private fun everWrongOwnQuestionIds(
        phaseId: UUID,
        userId: UUID,
        ownQuestions: List<PhaseCheckQuestion>,
    ): Set<UUID> {
        val ownIds = ownQuestions.map { it.id }.toSet()
        return phaseCheckAttemptRepository
            .findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(phaseId, userId)
            .flatMap { it.answers }
            .filter { !it.correct && it.questionId in ownIds }
            .map { it.questionId }
            .toSet()
    }

    /**
     * Throws a ResponseStatusException with HttpStatus.BAD_REQUEST and the provided message.
     *
     * @param message The error message to include in the exception.
     * @return This function does not return as it always throws an exception.
     */
    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    /**
     * Validates the questions in the given request to ensure they meet the required criteria
     * based on the type of each question. Throws a bad request error if validation fails.
     *
     * @param request The UpdatePhaseCheckRequest containing the list of questions to be validated.
     */
    private fun validateQuestions(request: UpdatePhaseCheckRequest) {
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
