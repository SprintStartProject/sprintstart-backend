package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckOption
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.entity.QuestionAttempt
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OnboardingQuestionMapperTest {
    private val path = OnboardingPath(userId = UUID.randomUUID())
    private val phase = OnboardingPhase(path = path, position = 0, title = "P", description = "d")

    private fun question(position: Int): PhaseCheckQuestion {
        val q = PhaseCheckQuestion(
            phase = phase,
            position = position,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "q$position",
        )
        phase.checkQuestions += q
        return q
    }

    private fun option(question: PhaseCheckQuestion, position: Int, label: String, correct: Boolean): PhaseCheckOption {
        val o = PhaseCheckOption(question = question, position = position, label = label, correct = correct)
        question.options += o
        return o
    }

    @Test
    fun `user response sorts options by position and carries the given status`() {
        val q = question(0)
        option(q, position = 1, label = "b", correct = true)
        option(q, position = 0, label = "a", correct = false)

        val response = q.toForUserResponse(QuestionStatus.RETRY)

        assertEquals(q.id, response.id)
        assertEquals(phase.id, response.phaseId)
        assertEquals(QuestionStatus.RETRY, response.status)
        assertEquals(listOf("a", "b"), response.options.map { it.label })
    }

    @Test
    fun `admin response exposes correct flags explanation and blockers`() {
        val blocker = question(0)
        val q = question(1)
        q.explanation = "because"
        q.blockedBy += blocker
        option(q, position = 0, label = "right", correct = true)
        option(q, position = 1, label = "wrong", correct = false)

        val response = q.toForAdminResponse()

        assertEquals("because", response.explanation)
        assertEquals(listOf(true, false), response.options.map { it.correct })
        assertEquals(setOf(blocker.id), response.blockerIds)
    }

    @Test
    fun `phase questions response sorts questions by position`() {
        val later = question(1)
        val earlier = question(0)

        val response = phase.toPhaseQuestionsResponse()

        assertEquals(phase.id, response.phaseId)
        assertEquals(listOf(earlier.id, later.id), response.questions.map { it.id })
    }

    @Test
    fun `attempt response copies the selected option ids and the text answer`() {
        val optionId = UUID.randomUUID()
        val attempt = QuestionAttempt(
            questionId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            correct = false,
            selectedOptionIds = mutableListOf(optionId),
            textAnswer = "free text",
        )

        val response = attempt.toResponse()

        assertEquals(attempt.id, response.id)
        assertFalse(response.correct)
        assertEquals(listOf(optionId), response.selectedOptionIds)
        assertEquals("free text", response.textAnswer)
        assertEquals(attempt.createdAt, response.createdAt)
    }
}
