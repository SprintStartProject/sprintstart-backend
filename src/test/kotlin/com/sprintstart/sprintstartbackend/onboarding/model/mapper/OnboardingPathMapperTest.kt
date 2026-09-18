package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingPathMapperTest {
    private fun path(vararg phases: OnboardingPhase): OnboardingPath {
        val p = OnboardingPath(userId = UUID.randomUUID())
        phases.forEach { p.phases += it }
        return p
    }

    private fun phase(position: Int, path: OnboardingPath): OnboardingPhase {
        val ph = OnboardingPhase(path = path, position = position, title = "P$position", description = "d")
        path.phases += ph
        return ph
    }

    private fun step(phase: OnboardingPhase, position: Int, status: StepStatus = StepStatus.WAITING): OnboardingStep {
        val s = OnboardingStep(
            phase = phase,
            position = position,
            title = "s$position",
            description = "d",
            type = StepType.TASK,
            estimatedMinutes = 1,
            expectedOutcome = "o",
            status = status,
        )
        phase.steps += s
        return s
    }

    private fun question(phase: OnboardingPhase, position: Int): PhaseCheckQuestion {
        val q = PhaseCheckQuestion(
            phase = phase,
            position = position,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "q$position",
        )
        phase.checkQuestions += q
        return q
    }

    @Test
    fun `for-user response locks a phase behind an incomplete blocker and locks its nodes`() {
        val p = path()
        val ph1 = phase(0, p)
        val ph2 = phase(1, p)
        ph2.blockedBy += ph1
        step(ph1, 0, StepStatus.FINISHED)
        question(ph1, 0)
        step(ph2, 0)
        val q2 = question(ph2, 0)

        // ph1's question is not passed, so ph1 is incomplete and ph2 (with its nodes) is locked.
        val response = p.toGetForUserResponse(attemptedQuestionIds = setOf(q2.id))

        assertFalse(response.phases[0].locked)
        assertEquals(
            QuestionStatus.OPEN,
            response.phases[0]
                .questions
                .single()
                .status,
        )
        assertTrue(response.phases[1].locked)
        assertTrue(
            response.phases[1]
                .steps
                .single()
                .locked,
        )
        assertEquals(
            QuestionStatus.LOCKED,
            response.phases[1]
                .questions
                .single()
                .status,
        )
    }

    @Test
    fun `for-user response unlocks the blocked phase and retries its attempted question once the blocker passes`() {
        val p = path()
        val ph1 = phase(0, p)
        val ph2 = phase(1, p)
        ph2.blockedBy += ph1
        step(ph1, 0, StepStatus.FINISHED)
        val q1 = question(ph1, 0)
        step(ph2, 0)
        val q2 = question(ph2, 0)

        // q1 passed completes ph1 and unlocks ph2; q2 was attempted but never passed, so RETRY.
        val response = p.toGetForUserResponse(
            passedQuestionIds = setOf(q1.id),
            attemptedQuestionIds = setOf(q1.id, q2.id),
        )

        assertFalse(response.phases[1].locked)
        assertFalse(
            response.phases[1]
                .steps
                .single()
                .locked,
        )
        assertEquals(
            QuestionStatus.PASSED,
            response.phases[0]
                .questions
                .single()
                .status,
        )
        assertEquals(
            QuestionStatus.RETRY,
            response.phases[1]
                .questions
                .single()
                .status,
        )
    }

    @Test
    fun `for-user response sorts phases steps and questions by position`() {
        val p = path()
        val later = phase(1, p)
        val earlier = phase(0, p)
        step(earlier, 1, StepStatus.FINISHED)
        step(earlier, 0, StepStatus.FINISHED)
        question(earlier, 1)
        question(earlier, 0)

        val response = p.toGetForUserResponse()

        assertEquals(listOf(earlier.id, later.id), response.phases.map { it.id })
        assertEquals(listOf(0, 1), response.phases[0].steps.map { it.position })
        assertEquals(listOf(0, 1), response.phases[0].questions.map { it.position })
    }

    @Test
    fun `for-user response hides a failed phase and reports it as a generation issue`() {
        val p = path()
        phase(0, p)
        val failed = phase(1, p)
        failed.generationStatus = GenerationStatus.FAILED

        val response = p.toGetForUserResponse()

        assertEquals(1, response.phases.size)
        val issue = response.generationIssues.single()
        assertEquals(failed.id, issue.phaseId)
        assertEquals(failed.title, issue.title)
        assertEquals(GenerationStatus.FAILED, issue.status)
    }
}
