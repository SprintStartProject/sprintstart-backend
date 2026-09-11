package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.PhaseCheckAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerResult
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckOption
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdateOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdatePhaseQuestionsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdateQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.QuestionAttemptRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestionAttemptServiceTest {
    private val onboardingPhaseRepository: OnboardingPhaseRepository = mockk()
    private val phaseCheckQuestionRepository: PhaseCheckQuestionRepository = mockk()
    private val questionAttemptRepository: QuestionAttemptRepository = mockk()
    private val onboardingCompletionService: OnboardingCompletionService = mockk(relaxed = true)
    private val userApi: com.sprintstart.sprintstartbackend.user.external.UserApi = mockk()
    private val phaseCheckAiClient: PhaseCheckAiClient = mockk()
    private val service = QuestionAttemptService(
        onboardingPhaseRepository,
        phaseCheckQuestionRepository,
        questionAttemptRepository,
        onboardingCompletionService,
        userApi,
        phaseCheckAiClient,
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth|q-user"
    private val phaseId = UUID.randomUUID()

    private fun makeQuestion(): PhaseCheckQuestion {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(id = phaseId, path = path, position = 0, title = "Setup", description = "d")
        path.phases += phase
        return PhaseCheckQuestion(phase = phase, position = 0, type = CheckQuestionType.MULTIPLE_CHOICE, question = "q")
    }

    private fun addMcOptions(question: PhaseCheckQuestion): PhaseCheckQuestion {
        question.options += PhaseCheckOption(question = question, position = 0, label = "right", correct = true)
        question.options += PhaseCheckOption(question = question, position = 1, label = "wrong", correct = false)
        return question
    }

    @Test
    fun `grades a correct multiple choice answer as passed`() = runTest {
        val question = addMcOptions(makeQuestion())
        val correctOption = question.options.first { it.correct }
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { phaseCheckQuestionRepository.findById(question.id) } returns Optional.of(question)
        every { questionAttemptRepository.save(any()) } answers { firstArg() }
        every { onboardingCompletionService.completeIfFinished(userId) } returns false

        val result = service.submitQuestionAttemptForMe(
            authId,
            question.id,
            SubmitQuestionAttemptRequest(selectedOptionIds = listOf(correctOption.id)),
        )

        assertTrue(result.correct)
        assertEquals(QuestionStatus.PASSED, result.status)
        verify { questionAttemptRepository.save(match { it.correct }) }
        verify { onboardingCompletionService.completeIfFinished(userId) }
    }

    @Test
    fun `grades a wrong multiple choice answer as retry`() = runTest {
        val question = addMcOptions(makeQuestion())
        val wrongOption = question.options.first { !it.correct }
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { phaseCheckQuestionRepository.findById(question.id) } returns Optional.of(question)
        every { questionAttemptRepository.save(any()) } answers { firstArg() }

        val result = service.submitQuestionAttemptForMe(
            authId,
            question.id,
            SubmitQuestionAttemptRequest(selectedOptionIds = listOf(wrongOption.id)),
        )

        assertFalse(result.correct)
        assertEquals(QuestionStatus.RETRY, result.status)
        verify(exactly = 0) { onboardingCompletionService.completeIfFinished(userId) }
    }

    @Test
    fun `short text uses the AI grading result`() = runTest {
        val question = makeQuestion().apply {
            type = CheckQuestionType.SHORT_TEXT
            correctAnswer = "gradlew bootRun"
        }
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { phaseCheckQuestionRepository.findById(question.id) } returns Optional.of(question)
        every { questionAttemptRepository.save(any()) } answers { firstArg() }
        coEvery { phaseCheckAiClient.gradeAnswers(any()) } returns
            listOf(GradeAnswerResult(id = question.id.toString(), correct = true, feedback = "nice"))
        every { onboardingCompletionService.completeIfFinished(userId) } returns true

        val result = service.submitQuestionAttemptForMe(
            authId,
            question.id,
            SubmitQuestionAttemptRequest(textAnswer = "gradlew bootRun"),
        )

        assertTrue(result.correct)
        assertEquals("nice", result.feedback)
        assertTrue(result.onboardingCompleted)
    }

    @Test
    fun `falls back to exact match when the AI service is unavailable`() = runTest {
        val question = makeQuestion().apply {
            type = CheckQuestionType.SHORT_TEXT
            correctAnswer = "spring boot"
        }
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { phaseCheckQuestionRepository.findById(question.id) } returns Optional.of(question)
        every { questionAttemptRepository.save(any()) } answers { firstArg() }
        coEvery { phaseCheckAiClient.gradeAnswers(any()) } throws RuntimeException("down")

        val correct = service.submitQuestionAttemptForMe(
            authId,
            question.id,
            SubmitQuestionAttemptRequest(textAnswer = "Spring Boot"),
        )
        val wrong = service.submitQuestionAttemptForMe(
            authId,
            question.id,
            SubmitQuestionAttemptRequest(textAnswer = "something else"),
        )

        assertTrue(correct.correct)
        assertFalse(wrong.correct)
    }

    @Test
    fun `rejects a question that is not part of the user's path`() = runTest {
        val question = makeQuestion()
        val otherUserPath = OnboardingPath(userId = UUID.randomUUID())
        val otherPhase = OnboardingPhase(path = otherUserPath, position = 0, title = "Other", description = "d")
        otherUserPath.phases += otherPhase
        val otherQuestion = PhaseCheckQuestion(
            phase = otherPhase,
            position = 0,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "q",
        )
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { phaseCheckQuestionRepository.findById(otherQuestion.id) } returns Optional.of(otherQuestion)

        assertThrows<ResponseStatusException> {
            service.submitQuestionAttemptForMe(
                authId,
                otherQuestion.id,
                SubmitQuestionAttemptRequest(selectedOptionIds = emptyList()),
            )
        }
    }

    @Test
    fun `replaces phase questions keeping surviving identities`() {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(id = phaseId, path = path, position = 0, title = "Setup", description = "d")
        path.phases += phase
        val existing = addMcOptions(
            PhaseCheckQuestion(
                phase = phase,
                position = 0,
                type = CheckQuestionType.MULTIPLE_CHOICE,
                question = "old",
            ),
        )
        phase.checkQuestions += existing

        every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)
        every { onboardingPhaseRepository.save(any()) } answers { firstArg() }

        val response = service.replacePhaseQuestions(
            phaseId,
            UpdatePhaseQuestionsRequest(
                questions = listOf(
                    UpdateQuestionRequest(
                        id = existing.id,
                        position = 0,
                        type = CheckQuestionType.MULTIPLE_CHOICE,
                        question = "updated",
                        options = listOf(
                            UpdateOptionRequest(
                                id = existing.options[0].id,
                                position = 0,
                                label = "right",
                                correct = true,
                            ),
                            UpdateOptionRequest(
                                id = existing.options[1].id,
                                position = 1,
                                label = "wrong",
                                correct = false,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, response.questions.size)
        assertEquals(existing.id, response.questions.single().id)
        assertEquals("updated", response.questions.single().question)
    }

    @Test
    fun `rejects a multiple choice question without a correct option`() {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(id = phaseId, path = path, position = 0, title = "Setup", description = "d")
        path.phases += phase
        every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)

        assertThrows<ResponseStatusException> {
            service.replacePhaseQuestions(
                phaseId,
                UpdatePhaseQuestionsRequest(
                    questions = listOf(
                        UpdateQuestionRequest(
                            position = 0,
                            type = CheckQuestionType.MULTIPLE_CHOICE,
                            question = "q",
                            options = listOf(
                                UpdateOptionRequest(
                                    position = 0,
                                    label = "a",
                                    correct = false,
                                ),
                                UpdateOptionRequest(
                                    position = 1,
                                    label = "b",
                                    correct = false,
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}
