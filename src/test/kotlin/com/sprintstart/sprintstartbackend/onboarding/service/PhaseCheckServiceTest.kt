package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.PhaseCheckAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerItem
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerResult
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckAttempt
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckOption
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckReviewItem
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.SubmitCheckAnswerRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.SubmitPhaseCheckAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.SubmitReviewCheckRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdateCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdateCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdatePhaseCheckRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckAttemptRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckReviewItemRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhaseCheckServiceTest {
    private val onboardingPhaseRepository: OnboardingPhaseRepository = mockk()
    private val phaseCheckAttemptRepository: PhaseCheckAttemptRepository = mockk()
    private val phaseCheckQuestionRepository: PhaseCheckQuestionRepository = mockk()
    private val phaseCheckReviewItemRepository: PhaseCheckReviewItemRepository = mockk()
    private val userApi: UserApi = mockk()
    private val phaseCheckAiClient: PhaseCheckAiClient = mockk()
    private val service =
        PhaseCheckService(
            onboardingPhaseRepository,
            phaseCheckAttemptRepository,
            phaseCheckQuestionRepository,
            phaseCheckReviewItemRepository,
            userApi,
            phaseCheckAiClient,
        )

    private val userId = UUID.randomUUID()
    private val phaseId = UUID.randomUUID()
    private val authId = "auth|test-user"

    @BeforeEach
    fun setUp() {
        // Default: the AI grades short text like a trimmed, case-insensitive exact match,
        // so existing assertions hold. Individual tests override this for semantic grading.
        coEvery { phaseCheckAiClient.gradeAnswers(any()) } answers {
            firstArg<List<GradeAnswerItem>>().map { item ->
                GradeAnswerResult(
                    id = item.id,
                    correct = item.userAnswer.trim().equals(item.referenceAnswer.trim(), ignoreCase = true),
                    feedback = "",
                )
            }
        }
        // Default: an empty review pool that no question has entered yet. Review tests override these.
        every { phaseCheckReviewItemRepository.findAllByUserIdAndResolvedFalseOrderByCreatedAtAsc(any()) } returns
            mutableListOf()
        every { phaseCheckReviewItemRepository.countByUserIdAndResolvedFalse(any()) } returns 0L
        every { phaseCheckReviewItemRepository.existsByUserIdAndQuestionId(any(), any()) } returns false
        every { phaseCheckReviewItemRepository.save(any()) } answers { firstArg() }
        every { phaseCheckReviewItemRepository.saveAll(any<List<PhaseCheckReviewItem>>()) } answers { firstArg() }
        every { phaseCheckQuestionRepository.findAllById(any()) } returns mutableListOf()
        every { phaseCheckAttemptRepository.findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(any(), any()) } returns
            mutableListOf()
        // Default: the completion gate finds no path, so onboarding is never completed.
        // Tests about completion wire it explicitly via givenPathForCompletion().
        every { onboardingPhaseRepository.findAllByPathUserId(any()) } returns mutableListOf()
        every { phaseCheckAttemptRepository.existsByPhaseIdAndUserIdAndPassedTrue(any(), any()) } returns false
        // Default: promoting the user on onboarding completion is a no-op side effect.
        // Tests that assert on it verify the call explicitly.
        every { userApi.markOnboardingCompleted(any()) } just Runs
    }

    /**
     * Wires the onboarding-completion gate, which reads the path and pass state from the
     * repositories rather than from the in-memory entities.
     *
     * @param phases The phases making up the user's path.
     * @param passedPhaseIds Phases the user has a passed attempt for.
     */
    private fun givenPathForCompletion(vararg phases: OnboardingPhase, passedPhaseIds: Set<UUID>) {
        every { onboardingPhaseRepository.findAllByPathUserId(userId) } returns phases.toMutableList()
        phases.forEach { phase ->
            every { phaseCheckAttemptRepository.existsByPhaseIdAndUserIdAndPassedTrue(phase.id, userId) } returns
                (phase.id in passedPhaseIds)
        }
    }

    private fun makePath(vararg phasePositions: Int): OnboardingPath {
        val path = OnboardingPath(userId = userId)
        phasePositions.forEach { position ->
            path.phases += OnboardingPhase(path = path, position = position, title = "P$position", description = "d")
        }
        return path
    }

    /** A phase with one multiple-choice and one short-text question. */
    private fun makePhaseWithCheck(path: OnboardingPath = makePath()): OnboardingPhase {
        val phase = OnboardingPhase(id = phaseId, path = path, position = 0, title = "Setup", description = "d")

        val mcQuestion = PhaseCheckQuestion(
            phase = phase,
            position = 0,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "Which is correct?",
            explanation = "Because.",
        )
        mcQuestion.options += PhaseCheckOption(question = mcQuestion, position = 0, label = "Right", correct = true)
        mcQuestion.options += PhaseCheckOption(question = mcQuestion, position = 1, label = "Wrong", correct = false)

        val textQuestion = PhaseCheckQuestion(
            phase = phase,
            position = 1,
            type = CheckQuestionType.SHORT_TEXT,
            question = "Start command?",
            correctAnswer = "gradlew bootRun",
        )

        phase.checkQuestions += mcQuestion
        phase.checkQuestions += textQuestion
        return phase
    }

    private fun correctOptionId(phase: OnboardingPhase) =
        phase.checkQuestions
            .first { it.type == CheckQuestionType.MULTIPLE_CHOICE }
            .options
            .first { it.correct }
            .id

    private fun mcQuestionId(phase: OnboardingPhase) =
        phase.checkQuestions.first { it.type == CheckQuestionType.MULTIPLE_CHOICE }.id

    private fun textQuestionId(phase: OnboardingPhase) =
        phase.checkQuestions.first { it.type == CheckQuestionType.SHORT_TEXT }.id

    /** A phase whose check consists of [count] multiple-choice questions, each with one correct option. */
    private fun makePhaseWithMcQuestions(count: Int): OnboardingPhase {
        val phase = OnboardingPhase(id = phaseId, path = makePath(), position = 0, title = "Setup", description = "d")
        repeat(count) { index ->
            val question = PhaseCheckQuestion(
                phase = phase,
                position = index,
                type = CheckQuestionType.MULTIPLE_CHOICE,
                question = "q$index",
            )
            question.options += PhaseCheckOption(question = question, position = 0, label = "right", correct = true)
            question.options += PhaseCheckOption(question = question, position = 1, label = "wrong", correct = false)
            phase.checkQuestions += question
        }
        return phase
    }

    /** Answers the first [correctCount] questions correctly and the rest incorrectly. */
    private fun answersFor(phase: OnboardingPhase, correctCount: Int): SubmitPhaseCheckAttemptRequest {
        val answers = phase.checkQuestions.sortedBy { it.position }.mapIndexed { index, question ->
            val option = if (index < correctCount) {
                question.options.first { it.correct }
            } else {
                question.options.first { !it.correct }
            }
            SubmitCheckAnswerRequest(questionId = question.id, selectedOptionIds = listOf(option.id))
        }
        return SubmitPhaseCheckAttemptRequest(answers = answers)
    }

    @Nested
    inner class GetPhaseCheckForMe {
        @Test
        fun `returns questions without exposing correct answers`() {
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)

            val result = service.getPhaseCheckForMe(authId, phaseId)

            assertEquals(2, result.questions.size)
            assertTrue(result.required)
            // The user-facing options DTO has no `correct` field at all; assert both options are returned.
            assertEquals(
                2,
                result.questions
                    .first { it.options.isNotEmpty() }
                    .options.size,
            )
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getPhaseCheckForMe(authId, phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when phase not found for user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getPhaseCheckForMe(authId, phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class SubmitPhaseCheckAttemptForMe {
        @Test
        fun `passes when every answer is correct and reveals correct answers`() {
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            val saved = slot<PhaseCheckAttempt>()
            every { phaseCheckAttemptRepository.save(capture(saved)) } answers { saved.captured }

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(
                        questionId = mcQuestionId(phase),
                        selectedOptionIds = listOf(correctOptionId(phase)),
                    ),
                    SubmitCheckAnswerRequest(
                        questionId = textQuestionId(phase),
                        textAnswer = "gradlew bootRun",
                    ),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            assertTrue(result.passed)
            assertTrue(result.results.all { it.correct })
            // Correct answers are only revealed here, in the submit result.
            val mcResult = result.results.first { it.correctOptionIds.isNotEmpty() }
            assertEquals(listOf(correctOptionId(phase)), mcResult.correctOptionIds)
            assertTrue(saved.captured.passed)
        }

        @Test
        fun `short text grading is case insensitive and trimmed`() {
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(mcQuestionId(phase), selectedOptionIds = listOf(correctOptionId(phase))),
                    SubmitCheckAnswerRequest(textQuestionId(phase), textAnswer = "  GRADLEW BOOTRUN  "),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            assertTrue(result.results.first { it.questionId == textQuestionId(phase) }.correct)
        }

        @Test
        fun `fails when an answer is wrong and does not pass the check`() {
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(mcQuestionId(phase), selectedOptionIds = listOf(correctOptionId(phase))),
                    SubmitCheckAnswerRequest(textQuestionId(phase), textAnswer = "wrong"),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            assertFalse(result.passed)
            assertFalse(result.nextPhaseUnlocked)
            val textResult = result.results.first { it.questionId == textQuestionId(phase) }
            assertFalse(textResult.correct)
            assertEquals("gradlew bootRun", textResult.correctAnswer)
        }

        @Test
        fun `a missing answer counts as incorrect`() {
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }

            // Only answers the MC question, leaves the text question unanswered.
            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(mcQuestionId(phase), selectedOptionIds = listOf(correctOptionId(phase))),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            assertFalse(result.passed)
        }

        @Test
        fun `marks onboarding completed when the final knowledge check is passed`() {
            // Single-phase path with an empty review pool: passing its check finishes onboarding.
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            givenPathForCompletion(phase, passedPhaseIds = setOf(phase.id))

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(mcQuestionId(phase), selectedOptionIds = listOf(correctOptionId(phase))),
                    SubmitCheckAnswerRequest(textQuestionId(phase), textAnswer = "gradlew bootRun"),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            assertTrue(result.passed)
            assertTrue(result.onboardingCompleted)
            verify(exactly = 1) { userApi.markOnboardingCompleted(userId) }
        }

        @Test
        fun `does not mark onboarding completed while the review pool is not empty`() {
            // The final check is passed, but an earlier question is still waiting to be re-answered.
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            givenPathForCompletion(phase, passedPhaseIds = setOf(phase.id))
            every { phaseCheckReviewItemRepository.countByUserIdAndResolvedFalse(userId) } returns 1L

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(mcQuestionId(phase), selectedOptionIds = listOf(correctOptionId(phase))),
                    SubmitCheckAnswerRequest(textQuestionId(phase), textAnswer = "gradlew bootRun"),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            assertTrue(result.passed)
            assertFalse(result.onboardingCompleted)
            assertEquals(1, result.openReviewCount)
            verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
        }

        @Test
        fun `does not mark onboarding completed when a later phase still has a check`() {
            // Path with two phases both carrying a check; passing the first is not the last check.
            val path = makePath(0, 1)
            val phase = path.phases.first { it.position == 0 }
            val laterPhase = path.phases.first { it.position == 1 }
            listOf(phase, laterPhase).forEach { p ->
                val mc = PhaseCheckQuestion(
                    phase = p,
                    position = 0,
                    type = CheckQuestionType.MULTIPLE_CHOICE,
                    question = "q",
                )
                mc.options += PhaseCheckOption(question = mc, position = 0, label = "ok", correct = true)
                mc.options += PhaseCheckOption(question = mc, position = 1, label = "no", correct = false)
                p.checkQuestions += mc
            }
            val firstQuestion = phase.checkQuestions.first()
            val correct = firstQuestion.options.first { it.correct }

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phase.id, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            // The later phase carries the final check and has not been passed.
            givenPathForCompletion(phase, laterPhase, passedPhaseIds = setOf(phase.id))

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(firstQuestion.id, selectedOptionIds = listOf(correct.id)),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phase.id, request)

            assertTrue(result.passed)
            assertFalse(result.onboardingCompleted)
            verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
        }

        @Test
        fun `does not mark onboarding completed when the final check is failed`() {
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(mcQuestionId(phase), selectedOptionIds = listOf(correctOptionId(phase))),
                    SubmitCheckAnswerRequest(textQuestionId(phase), textAnswer = "wrong"),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            assertFalse(result.passed)
            verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
        }

        @Test
        fun `unlocks the next phase when passed and steps complete`() {
            // Phase 0 (under test) has no steps -> steps count as complete; phase 1 exists as "next".
            val path = makePath(0, 1)
            val phase = path.phases.first { it.position == 0 }
            val mcQuestion = PhaseCheckQuestion(
                phase = phase,
                position = 0,
                type = CheckQuestionType.MULTIPLE_CHOICE,
                question = "q",
            )
            val correct = PhaseCheckOption(question = mcQuestion, position = 0, label = "ok", correct = true)
            mcQuestion.options += correct
            mcQuestion.options += PhaseCheckOption(question = mcQuestion, position = 1, label = "no", correct = false)
            phase.checkQuestions += mcQuestion

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phase.id, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(SubmitCheckAnswerRequest(mcQuestion.id, selectedOptionIds = listOf(correct.id))),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phase.id, request)

            assertTrue(result.passed)
            assertTrue(result.nextPhaseUnlocked)
        }

        @Test
        fun `passes when at least 80 percent of the questions are correct`() {
            val phase = makePhaseWithMcQuestions(count = 5)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }

            // 4 of 5 correct = 80%.
            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, answersFor(phase, correctCount = 4))

            assertTrue(result.passed)
            assertEquals(4, result.correctCount)
            assertEquals(5, result.questionCount)
            assertEquals(80, result.requiredPercent)
        }

        @Test
        fun `fails when fewer than 80 percent of the questions are correct`() {
            val phase = makePhaseWithMcQuestions(count = 5)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }

            // 3 of 5 correct = 60%.
            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, answersFor(phase, correctCount = 3))

            assertFalse(result.passed)
            assertEquals(3, result.correctCount)
        }

        @Test
        fun `accepts a semantically correct short text answer and passes through AI feedback`() {
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            // The AI accepts a paraphrase that a plain exact match would have rejected.
            coEvery { phaseCheckAiClient.gradeAnswers(any()) } answers {
                firstArg<List<GradeAnswerItem>>().map {
                    GradeAnswerResult(id = it.id, correct = true, feedback = "Right idea.")
                }
            }

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(mcQuestionId(phase), selectedOptionIds = listOf(correctOptionId(phase))),
                    SubmitCheckAnswerRequest(
                        textQuestionId(phase),
                        textAnswer = "you run the gradle wrapper boot task",
                    ),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            val textResult = result.results.first { it.questionId == textQuestionId(phase) }
            assertTrue(textResult.correct)
            assertEquals("Right idea.", textResult.feedback)
        }

        @Test
        fun `falls back to exact match when the AI grading service is unavailable`() {
            val phase = makePhaseWithCheck()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            coEvery { phaseCheckAiClient.gradeAnswers(any()) } throws RuntimeException("AI down")

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(
                    SubmitCheckAnswerRequest(mcQuestionId(phase), selectedOptionIds = listOf(correctOptionId(phase))),
                    // Exact reference answer -> fallback comparison still marks it correct.
                    SubmitCheckAnswerRequest(textQuestionId(phase), textAnswer = "gradlew bootRun"),
                ),
            )

            val result = service.submitPhaseCheckAttemptForMe(authId, phaseId, request)

            assertTrue(result.results.first { it.questionId == textQuestionId(phase) }.correct)
        }

        @Test
        fun `throws 404 when the phase has no knowledge check`() {
            val phase = OnboardingPhase(id = phaseId, path = makePath(), position = 0, title = "t", description = "d")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)

            assertThrows<ResponseStatusException> {
                service.submitPhaseCheckAttemptForMe(authId, phaseId, SubmitPhaseCheckAttemptRequest())
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetPhaseCheck {
        @Test
        fun `returns questions including correct answers for admins`() {
            val phase = makePhaseWithCheck()
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)

            val result = service.getPhaseCheck(phaseId)

            val mc = result.questions.first { it.options.isNotEmpty() }
            assertTrue(mc.options.any { it.correct })
            assertEquals("gradlew bootRun", result.questions.first { it.correctAnswer != null }.correctAnswer)
        }

        @Test
        fun `throws 404 when phase not found`() {
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getPhaseCheck(phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class ReplacePhaseCheck {
        @Test
        fun `replaces questions and returns stored check`() {
            val phase = makePhaseWithCheck()
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)
            every { onboardingPhaseRepository.save(any()) } answers { firstArg() }

            val request = UpdatePhaseCheckRequest(
                questions = listOf(
                    UpdateCheckQuestionRequest(
                        position = 0,
                        type = CheckQuestionType.SHORT_TEXT,
                        question = "New?",
                        correctAnswer = "yes",
                    ),
                ),
            )

            val result = service.replacePhaseCheck(phaseId, request)

            assertEquals(1, result.questions.size)
            assertEquals("New?", result.questions.first().question)
            assertEquals(1, phase.checkQuestions.size)
        }

        @Test
        fun `throws 400 when a multiple choice question has fewer than two options`() {
            val phase = makePhaseWithCheck()
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)

            val request = UpdatePhaseCheckRequest(
                questions = listOf(
                    UpdateCheckQuestionRequest(
                        position = 0,
                        type = CheckQuestionType.MULTIPLE_CHOICE,
                        question = "q",
                        options = listOf(UpdateCheckOptionRequest(0, "only one", true)),
                    ),
                ),
            )

            assertThrows<ResponseStatusException> {
                service.replacePhaseCheck(phaseId, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 400 when a multiple choice question has no correct option`() {
            val phase = makePhaseWithCheck()
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)

            val request = UpdatePhaseCheckRequest(
                questions = listOf(
                    UpdateCheckQuestionRequest(
                        position = 0,
                        type = CheckQuestionType.MULTIPLE_CHOICE,
                        question = "q",
                        options = listOf(
                            UpdateCheckOptionRequest(0, "a", false),
                            UpdateCheckOptionRequest(1, "b", false),
                        ),
                    ),
                ),
            )

            assertThrows<ResponseStatusException> {
                service.replacePhaseCheck(phaseId, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 400 when a short text question has no correct answer`() {
            val phase = makePhaseWithCheck()
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)

            val request = UpdatePhaseCheckRequest(
                questions = listOf(
                    UpdateCheckQuestionRequest(position = 0, type = CheckQuestionType.SHORT_TEXT, question = "q"),
                ),
            )

            assertThrows<ResponseStatusException> {
                service.replacePhaseCheck(phaseId, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetPhaseCheckAttemptsForUser {
        @Test
        fun `returns attempts for a user and phase`() {
            val phase = makePhaseWithCheck()
            val attempt = PhaseCheckAttempt(phase = phase, userId = userId, passed = false)
            every { userApi.exists(userId) } returns true
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every {
                phaseCheckAttemptRepository.findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(phaseId, userId)
            } returns mutableListOf(attempt)

            val result = service.getPhaseCheckAttemptsForUser(userId, phaseId)

            assertEquals(1, result.attempts.size)
            assertEquals(2, result.attempts.first().questionCount)
        }

        @Test
        fun `throws 404 when user does not exist`() {
            every { userApi.exists(userId) } returns false

            assertThrows<ResponseStatusException> {
                service.getPhaseCheckAttemptsForUser(userId, phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class ReplacePhaseCheckDropsShortTextOptions {
        @Test
        fun `does not persist options or answer mismatch for short text`() {
            val phase = makePhaseWithCheck()
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)
            every { onboardingPhaseRepository.save(any()) } answers { firstArg() }

            val request = UpdatePhaseCheckRequest(
                questions = listOf(
                    UpdateCheckQuestionRequest(
                        position = 0,
                        type = CheckQuestionType.SHORT_TEXT,
                        question = "cmd?",
                        correctAnswer = "run",
                    ),
                ),
            )

            service.replacePhaseCheck(phaseId, request)

            val stored = phase.checkQuestions.single()
            assertTrue(stored.options.isEmpty())
            assertEquals("run", stored.correctAnswer)
            assertNull(
                phase.checkQuestions
                    .first()
                    .options
                    .firstOrNull(),
            )
        }
    }

    private fun addMcQuestion(phase: OnboardingPhase, position: Int): PhaseCheckQuestion {
        val question = PhaseCheckQuestion(
            phase = phase,
            position = position,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "q$position",
        )
        question.options += PhaseCheckOption(question = question, position = 0, label = "ok", correct = true)
        question.options += PhaseCheckOption(question = question, position = 1, label = "no", correct = false)
        phase.checkQuestions += question
        return question
    }

    private fun answerCorrect(question: PhaseCheckQuestion) =
        SubmitCheckAnswerRequest(question.id, selectedOptionIds = listOf(question.options.first { it.correct }.id))

    private fun answerWrong(question: PhaseCheckQuestion) =
        SubmitCheckAnswerRequest(question.id, selectedOptionIds = listOf(question.options.first { !it.correct }.id))

    private fun reviewItemFor(question: PhaseCheckQuestion) = PhaseCheckReviewItem(
        userId = userId,
        questionId = question.id,
        sourcePhaseId = question.phase.id,
        targetPhaseId = question.phase.id,
    )

    /** Makes [items] the user's open review pool, resolving their questions from the repository. */
    private fun givenOpenReviewPool(vararg items: Pair<PhaseCheckReviewItem, PhaseCheckQuestion>) {
        every { phaseCheckReviewItemRepository.findAllByUserIdAndResolvedFalseOrderByCreatedAtAsc(userId) } returns
            items.map { it.first }.toMutableList()
        every { phaseCheckQuestionRepository.findAllById(items.map { it.first.questionId }) } returns
            items.map { it.second }.toMutableList()
        every { phaseCheckReviewItemRepository.countByUserIdAndResolvedFalse(userId) } returns items.size.toLong()
    }

    @Nested
    inner class ReviewPoolCollection {
        @Test
        fun `collects a question wrong in an earlier attempt even if it is now correct`() {
            val path = makePath(0, 1)
            val phase = path.phases.first { it.position == 0 }
            val questions = (0 until 5).map { addMcQuestion(phase, it) }

            // An earlier attempt got question index 2 wrong; the current attempt is all-correct.
            val priorAttempt = PhaseCheckAttempt(phase = phase, userId = userId, passed = false)
            questions.forEachIndexed { index, question ->
                priorAttempt.answers += PhaseCheckAnswer(
                    attempt = priorAttempt,
                    questionId = question.id,
                    correct = index != 2,
                )
            }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phase.id, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            every {
                phaseCheckAttemptRepository.findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(phase.id, userId)
            } returns mutableListOf(priorAttempt)
            val saved = mutableListOf<PhaseCheckReviewItem>()
            every { phaseCheckReviewItemRepository.save(capture(saved)) } answers { firstArg() }

            val request = SubmitPhaseCheckAttemptRequest(answers = questions.map { answerCorrect(it) })
            val result = service.submitPhaseCheckAttemptForMe(authId, phase.id, request)

            assertTrue(result.passed)
            assertEquals(1, saved.size)
            assertEquals(questions[2].id, saved.single().questionId)
            assertEquals(phase.id, saved.single().sourcePhaseId)
        }

        @Test
        fun `collects wrong questions on the path's final phase as well`() {
            // Regression: the pool used to only be filled when a following phase existed, so a
            // question missed in the last phase was silently dropped and never re-tested.
            val path = makePath(0)
            val phase = path.phases.single()
            val questions = (0 until 5).map { addMcQuestion(phase, it) }

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phase.id, userId) } returns Optional.of(phase)
            // Models the real flush order: the attempt is stored before the pool is filled from
            // the attempt history, so the question missed in this very attempt is found.
            val storedAttempts = mutableListOf<PhaseCheckAttempt>()
            every { phaseCheckAttemptRepository.save(capture(storedAttempts)) } answers { firstArg() }
            every {
                phaseCheckAttemptRepository.findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(phase.id, userId)
            } answers { storedAttempts.toMutableList() }
            val saved = mutableListOf<PhaseCheckReviewItem>()
            every { phaseCheckReviewItemRepository.save(capture(saved)) } answers { firstArg() }
            givenPathForCompletion(phase, passedPhaseIds = setOf(phase.id))
            // The freshly collected question keeps the pool non-empty.
            every { phaseCheckReviewItemRepository.countByUserIdAndResolvedFalse(userId) } returns 1L

            // 4 of 5 correct still passes at 80 percent, but the missed question must be collected.
            val answers = questions.mapIndexed { index, question ->
                if (index == 4) answerWrong(question) else answerCorrect(question)
            }
            val result = service.submitPhaseCheckAttemptForMe(
                authId,
                phase.id,
                SubmitPhaseCheckAttemptRequest(answers = answers),
            )

            assertTrue(result.passed)
            assertEquals(1, saved.size)
            assertEquals(questions[4].id, saved.single().questionId)
            // Onboarding stays open until the collected question is answered correctly.
            assertFalse(result.onboardingCompleted)
            verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
        }

        @Test
        fun `does not collect a question that already entered the pool`() {
            val path = makePath(0, 1)
            val phase = path.phases.first { it.position == 0 }
            val question = addMcQuestion(phase, 0)

            val priorAttempt = PhaseCheckAttempt(phase = phase, userId = userId, passed = false)
            priorAttempt.answers += PhaseCheckAnswer(
                attempt = priorAttempt,
                questionId = question.id,
                correct = false,
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phase.id, userId) } returns Optional.of(phase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            every {
                phaseCheckAttemptRepository.findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(phase.id, userId)
            } returns mutableListOf(priorAttempt)
            // The question was already collected once, and may even be resolved by now.
            every { phaseCheckReviewItemRepository.existsByUserIdAndQuestionId(userId, question.id) } returns true

            service.submitPhaseCheckAttemptForMe(
                authId,
                phase.id,
                SubmitPhaseCheckAttemptRequest(answers = listOf(answerCorrect(question))),
            )

            verify(exactly = 0) { phaseCheckReviewItemRepository.save(any()) }
        }

        @Test
        fun `getPhaseCheckForMe returns only the phase's own questions`() {
            val path = makePath(0, 1)
            val sourcePhase = path.phases.first { it.position == 0 }
            val currentPhase = path.phases.first { it.position == 1 }
            val ownQuestion = addMcQuestion(currentPhase, 0)
            val pooledQuestion = addMcQuestion(sourcePhase, 0)
            givenOpenReviewPool(reviewItemFor(pooledQuestion) to pooledQuestion)

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(currentPhase.id, userId) } returns
                Optional.of(currentPhase)

            val result = service.getPhaseCheckForMe(authId, currentPhase.id)

            assertEquals(1, result.questions.size)
            assertEquals(ownQuestion.id, result.questions.single().id)
            assertFalse(result.questions.single().review)
        }
    }

    @Nested
    inner class ReviewCheck {
        @Test
        fun `returns the open pool with the originating phase title`() {
            val path = makePath(0)
            val sourcePhase = path.phases.single()
            val question = addMcQuestion(sourcePhase, 0)
            givenOpenReviewPool(reviewItemFor(question) to question)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

            val result = service.getReviewCheckForMe(authId)

            assertEquals(1, result.openCount)
            val pooled = result.questions.single()
            assertEquals(question.id, pooled.id)
            assertTrue(pooled.review)
            assertEquals("P0", pooled.reviewSourcePhaseTitle)
        }

        @Test
        fun `returns another user's pool for reviewers without exposing answers`() {
            val path = makePath(0)
            val sourcePhase = path.phases.single()
            val question = addMcQuestion(sourcePhase, 0)
            givenOpenReviewPool(reviewItemFor(question) to question)
            every { userApi.exists(userId) } returns true

            val result = service.getReviewCheckForUser(userId)

            assertEquals(1, result.openCount)
            val pooled = result.questions.single()
            assertEquals(question.id, pooled.id)
            assertTrue(pooled.review)
            assertEquals("P0", pooled.reviewSourcePhaseTitle)
            // Reviewers get the same shape as the user, whose option type carries no
            // correct flag at all — answers stay behind the separate editing endpoint.
            assertEquals(2, pooled.options.size)
        }

        @Test
        fun `throws 404 when loading the pool of an unknown user`() {
            every { userApi.exists(userId) } returns false

            assertThrows<ResponseStatusException> { service.getReviewCheckForUser(userId) }
        }

        @Test
        fun `resolves a correctly answered question and leaves a wrong one open`() {
            val path = makePath(0)
            val sourcePhase = path.phases.single()
            val correctQuestion = addMcQuestion(sourcePhase, 0)
            val wrongQuestion = addMcQuestion(sourcePhase, 1)
            val correctItem = reviewItemFor(correctQuestion)
            val wrongItem = reviewItemFor(wrongQuestion)
            givenOpenReviewPool(correctItem to correctQuestion, wrongItem to wrongQuestion)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

            val result = service.submitReviewCheckForMe(
                authId,
                SubmitReviewCheckRequest(
                    answers = listOf(answerCorrect(correctQuestion), answerWrong(wrongQuestion)),
                ),
            )

            assertTrue(correctItem.resolved)
            assertFalse(wrongItem.resolved)
            assertEquals(2, result.answeredCount)
            assertEquals(1, result.correctCount)
            assertTrue(result.results.all { it.review })
        }

        @Test
        fun `ignores answers for questions that are not in the pool`() {
            val path = makePath(0)
            val sourcePhase = path.phases.single()
            val pooledQuestion = addMcQuestion(sourcePhase, 0)
            val unrelatedQuestion = addMcQuestion(sourcePhase, 1)
            givenOpenReviewPool(reviewItemFor(pooledQuestion) to pooledQuestion)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

            val result = service.submitReviewCheckForMe(
                authId,
                SubmitReviewCheckRequest(
                    answers = listOf(answerCorrect(pooledQuestion), answerCorrect(unrelatedQuestion)),
                ),
            )

            assertEquals(1, result.answeredCount)
            assertEquals(pooledQuestion.id, result.results.single().questionId)
        }

        @Test
        fun `completes onboarding when clearing the pool after the final check was passed`() {
            val path = makePath(0)
            val phase = path.phases.single()
            val question = addMcQuestion(phase, 0)
            givenOpenReviewPool(reviewItemFor(question) to question)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            givenPathForCompletion(phase, passedPhaseIds = setOf(phase.id))
            // The answered question leaves the pool empty.
            every { phaseCheckReviewItemRepository.countByUserIdAndResolvedFalse(userId) } returns 0L

            val result = service.submitReviewCheckForMe(
                authId,
                SubmitReviewCheckRequest(answers = listOf(answerCorrect(question))),
            )

            assertEquals(0, result.remainingCount)
            assertTrue(result.onboardingCompleted)
            verify(exactly = 1) { userApi.markOnboardingCompleted(userId) }
        }

        @Test
        fun `does not complete onboarding while questions remain in the pool`() {
            val path = makePath(0)
            val phase = path.phases.single()
            val answeredQuestion = addMcQuestion(phase, 0)
            val remainingQuestion = addMcQuestion(phase, 1)
            givenOpenReviewPool(
                reviewItemFor(answeredQuestion) to answeredQuestion,
                reviewItemFor(remainingQuestion) to remainingQuestion,
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            givenPathForCompletion(phase, passedPhaseIds = setOf(phase.id))

            val result = service.submitReviewCheckForMe(
                authId,
                SubmitReviewCheckRequest(answers = listOf(answerCorrect(answeredQuestion))),
            )

            assertFalse(result.onboardingCompleted)
            verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
        }
    }
}
