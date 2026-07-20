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
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdateCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdateCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdatePhaseCheckRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckAttemptRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckReviewItemRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
        // Default: no carried-over repeat questions. Carry-over tests override these.
        every {
            phaseCheckReviewItemRepository.findAllByUserIdAndTargetPhaseIdAndResolvedFalseOrderByCreatedAtAsc(
                any(),
                any(),
            )
        } returns mutableListOf()
        every { phaseCheckReviewItemRepository.findAllByUserIdAndQuestionIdAndResolvedFalse(any(), any()) } returns
            mutableListOf()
        every { phaseCheckReviewItemRepository.save(any()) } answers { firstArg() }
        every { phaseCheckReviewItemRepository.saveAll(any<List<PhaseCheckReviewItem>>()) } answers { firstArg() }
        every { phaseCheckQuestionRepository.findAllById(any()) } returns mutableListOf()
        every { phaseCheckAttemptRepository.findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(any(), any()) } returns
            mutableListOf()
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

    @Nested
    inner class CarryOver {
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

        @Test
        fun `carries an own question wrong in an earlier attempt to the next phase even if now correct`() {
            val path = makePath(0, 1)
            val phase = path.phases.first { it.position == 0 }
            val nextPhase = path.phases.first { it.position == 1 }
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
                phaseCheckAttemptRepository.findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(
                    phase.id,
                    userId,
                )
            } returns
                mutableListOf(priorAttempt)
            val saved = mutableListOf<PhaseCheckReviewItem>()
            every { phaseCheckReviewItemRepository.save(capture(saved)) } answers { firstArg() }

            val request = SubmitPhaseCheckAttemptRequest(answers = questions.map { answerCorrect(it) })
            val result = service.submitPhaseCheckAttemptForMe(authId, phase.id, request)

            assertTrue(result.passed)
            assertEquals(1, saved.size)
            assertEquals(questions[2].id, saved.single().questionId)
            assertEquals(nextPhase.id, saved.single().targetPhaseId)
            assertEquals(phase.id, saved.single().sourcePhaseId)
        }

        @Test
        fun `shows a carried-over question and resolves it when answered correctly`() {
            val path = makePath(0, 1)
            val sourcePhase = path.phases.first { it.position == 0 }
            val targetPhase = path.phases.first { it.position == 1 }
            val ownQuestion = addMcQuestion(targetPhase, 0)
            val carriedQuestion = addMcQuestion(sourcePhase, 0)
            val reviewItem = PhaseCheckReviewItem(
                userId = userId,
                questionId = carriedQuestion.id,
                sourcePhaseId = sourcePhase.id,
                targetPhaseId = targetPhase.id,
            )

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(targetPhase.id, userId) } returns
                Optional.of(targetPhase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            every {
                phaseCheckReviewItemRepository
                    .findAllByUserIdAndTargetPhaseIdAndResolvedFalseOrderByCreatedAtAsc(userId, targetPhase.id)
            } returns mutableListOf(reviewItem)
            every { phaseCheckQuestionRepository.findAllById(listOf(carriedQuestion.id)) } returns
                mutableListOf(carriedQuestion)

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(answerCorrect(ownQuestion), answerCorrect(carriedQuestion)),
            )
            val result = service.submitPhaseCheckAttemptForMe(authId, targetPhase.id, request)

            assertTrue(result.passed)
            assertTrue(reviewItem.resolved)
            val reviewResult = result.results.first { it.review }
            assertEquals(carriedQuestion.id, reviewResult.questionId)
            assertEquals("P0", reviewResult.reviewSourcePhaseTitle)
        }

        @Test
        fun `advances a carried-over question to the following phase when answered wrong`() {
            val path = makePath(0, 1, 2)
            val sourcePhase = path.phases.first { it.position == 0 }
            val targetPhase = path.phases.first { it.position == 1 }
            val phaseAfter = path.phases.first { it.position == 2 }
            val ownQuestion = addMcQuestion(targetPhase, 0)
            val carriedQuestion = addMcQuestion(sourcePhase, 0)
            val reviewItem = PhaseCheckReviewItem(
                userId = userId,
                questionId = carriedQuestion.id,
                sourcePhaseId = sourcePhase.id,
                targetPhaseId = targetPhase.id,
            )

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(targetPhase.id, userId) } returns
                Optional.of(targetPhase)
            every { phaseCheckAttemptRepository.save(any()) } answers { firstArg() }
            every {
                phaseCheckReviewItemRepository
                    .findAllByUserIdAndTargetPhaseIdAndResolvedFalseOrderByCreatedAtAsc(userId, targetPhase.id)
            } returns mutableListOf(reviewItem)
            every { phaseCheckQuestionRepository.findAllById(listOf(carriedQuestion.id)) } returns
                mutableListOf(carriedQuestion)

            val request = SubmitPhaseCheckAttemptRequest(
                answers = listOf(answerCorrect(ownQuestion), answerWrong(carriedQuestion)),
            )
            service.submitPhaseCheckAttemptForMe(authId, targetPhase.id, request)

            assertFalse(reviewItem.resolved)
            assertEquals(phaseAfter.id, reviewItem.targetPhaseId)
        }

        @Test
        fun `getPhaseCheckForMe appends carried-over repeat questions`() {
            val path = makePath(0, 1)
            val sourcePhase = path.phases.first { it.position == 0 }
            val targetPhase = path.phases.first { it.position == 1 }
            addMcQuestion(targetPhase, 0)
            val carriedQuestion = addMcQuestion(sourcePhase, 0)
            val reviewItem = PhaseCheckReviewItem(
                userId = userId,
                questionId = carriedQuestion.id,
                sourcePhaseId = sourcePhase.id,
                targetPhaseId = targetPhase.id,
            )

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(targetPhase.id, userId) } returns
                Optional.of(targetPhase)
            every {
                phaseCheckReviewItemRepository
                    .findAllByUserIdAndTargetPhaseIdAndResolvedFalseOrderByCreatedAtAsc(userId, targetPhase.id)
            } returns mutableListOf(reviewItem)
            every { phaseCheckQuestionRepository.findAllById(listOf(carriedQuestion.id)) } returns
                mutableListOf(carriedQuestion)

            val result = service.getPhaseCheckForMe(authId, targetPhase.id)

            assertEquals(2, result.questions.size)
            val review = result.questions.first { it.review }
            assertEquals(carriedQuestion.id, review.id)
            assertEquals("P0", review.reviewSourcePhaseTitle)
        }
    }
}
