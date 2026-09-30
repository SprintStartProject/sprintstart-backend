package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingGenerationIssueResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.QuestionAttemptRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The phases generation left empty, as the mentor sees them.
 *
 * Hidden from the hire's own path and reported only as generation issues -- which is right for the
 * page, and was wrong for the mentor: the read told it to repair such a phase with add_path_step and
 * gave it neither the id to do that with nor a lookup that could find the phase. Kept apart from
 * [BuddyPathToolsTest] because this is the one part of the read that is about phases that are *not*
 * on the path.
 */
class BuddyPathToolsRepairTest {
    private val onboardingPathService: OnboardingPathService = mockk()
    private val onboardingTaskService: OnboardingTaskService = mockk {
        every { getOnboardingTasksByStepId(any()) } returns emptyList()
    }
    private val questionAttemptRepository: QuestionAttemptRepository = mockk {
        every { findAllByQuestionIdAndUserIdOrderByCreatedAtDesc(any(), any()) } returns mutableListOf()
    }
    private val tools = BuddyPathTools(onboardingPathService, onboardingTaskService, questionAttemptRepository)

    private val userId = UUID.randomUUID()

    @Test
    fun `a phase that generated nothing is named, with what to do about it`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(0, "Setup", steps = listOf(step("Clone the repository", StepStatus.WAITING))),
            issues = listOf(
                OnboardingGenerationIssueResponse(UUID.randomUUID(), "Deployment", GenerationStatus.SKIPPED),
            ),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("Deployment")
        assertThat(text).contains("SKIPPED")
        // Not the hire's fault, and the one part of a path a conversation can genuinely repair.
        assertThat(text).contains("not the hire's fault")
        assertThat(text).contains("add_path_step")
    }

    @Test
    fun `an empty phase comes with the id and the description add_path_step needs`() {
        val deploymentId = UUID.randomUUID()
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(0, "Setup", steps = listOf(step("Clone the repository", StepStatus.WAITING))),
            issues = listOf(
                OnboardingGenerationIssueResponse(
                    deploymentId,
                    "Deployment",
                    GenerationStatus.SKIPPED,
                    description = "How a change reaches production",
                ),
            ),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("phase_id: $deploymentId")
        assertThat(text).contains("How a change reaches production")
    }

    @Test
    fun `a phase hidden for being empty is still found for the step that repairs it`() {
        val hidden = phase(1, "Deployment")
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(step("Clone the repository", StepStatus.WAITING))))
        every { onboardingPathService.findHiddenPhaseForUserId(userId, hidden.id) } returns hidden

        assertThat(tools.findPhase(userId, hidden.id)).isEqualTo(hidden)
    }

    @Test
    fun `an empty current phase says so rather than listing nothing`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(phase(0, "Deployment"))

        val text = tools.execute(userId)

        assertThat(text).contains("nothing in it")
    }

    // -- fixtures ---------------------------------------------------------------------------------

    private fun path(
        vararg phases: GetOnboardingPhaseForUserResponse,
        issues: List<OnboardingGenerationIssueResponse> = emptyList(),
    ) = GetOnboardingPathForUserResponse(
        id = UUID.randomUUID(),
        userId = userId,
        createdAt = Instant.EPOCH,
        phases = phases.toList(),
        generationIssues = issues,
    )

    private fun phase(position: Int, title: String, steps: List<GetOnboardingStepsResponse> = emptyList()) =
        GetOnboardingPhaseForUserResponse(
            id = UUID.randomUUID(),
            pathId = UUID.randomUUID(),
            position = position,
            title = title,
            description = "",
            locked = false,
            steps = steps,
            questions = emptyList(),
        )

    private fun step(title: String, status: StepStatus) = GetOnboardingStepsResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = 0,
        title = title,
        description = "",
        type = StepType.TASK,
        estimatedMinutes = 20,
        isAiAssisted = false,
        status = status,
        completedAt = null,
        skip = null,
        locked = false,
    )
}
