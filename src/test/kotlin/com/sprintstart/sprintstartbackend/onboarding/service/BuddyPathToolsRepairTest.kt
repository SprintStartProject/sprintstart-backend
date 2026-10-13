package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
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

    // -- no open visible phase left: the repair still has to reach the mentor --------------------
    //
    // Built from real entities through the real mapper, because that is where the hiding happens: a
    // hand-made response could carry a hidden phase in `phases` that the real read never would.

    @Test
    fun `a path whose every phase came back empty still names them, with what to repair them with`() {
        val path = OnboardingPath(userId = userId)
        val deployment = hiddenPhase(path, 0, "Deployment", GenerationStatus.SKIPPED)
        every { onboardingPathService.findPathForUserId(userId) } returns path.toGetForUserResponse()

        val text = tools.execute(userId)

        // Not "no phases at all, an authoring problem -- point them at their PM".
        assertThat(text).doesNotContain("authoring problem")
        assertThat(text).contains("phase_id: ${deployment.id}")
        assertThat(text).contains("add_path_step")
    }

    @Test
    fun `finishing every visible phase is not finishing a path with empty phases on it`() {
        val path = OnboardingPath(userId = userId)
        val setup = OnboardingPhase(path = path, position = 0, title = "Setup", description = "")
        setup.steps += OnboardingStep(
            phase = setup,
            position = 0,
            title = "Clone the repository",
            description = "",
            type = StepType.TASK,
            estimatedMinutes = 10,
            expectedOutcome = "",
            status = StepStatus.FINISHED,
        )
        path.phases += setup
        val deployment = hiddenPhase(path, 1, "Deployment", GenerationStatus.FAILED)
        every { onboardingPathService.findPathForUserId(userId) } returns path.toGetForUserResponse()

        val text = tools.execute(userId)

        assertThat(text).doesNotContain("nothing left on it")
        assertThat(text).contains("phase_id: ${deployment.id}")
        assertThat(text).contains("add_path_step")
    }

    @Test
    fun `the greeting does not call a path of empty phases one without phases`() {
        val path = OnboardingPath(userId = userId)
        hiddenPhase(path, 0, "Deployment", GenerationStatus.EMPTY)
        every { onboardingPathService.findPathForUserId(userId) } returns path.toGetForUserResponse()

        val snapshot = tools.snapshotFor(userId)

        assertThat(snapshot).doesNotContain("no phases in it")
        assertThat(snapshot).contains("came back with nothing in it")
    }

    @Test
    fun `the greeting does not call a path finished while a phase of it came back empty`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(0, "Setup", steps = listOf(step("Clone the repository", StepStatus.FINISHED))),
            issues = listOf(
                OnboardingGenerationIssueResponse(UUID.randomUUID(), "Deployment", GenerationStatus.SKIPPED),
            ),
        )

        val snapshot = tools.snapshotFor(userId)

        assertThat(snapshot).contains("every phase of their path that has something in it")
        assertThat(snapshot).contains("came back with nothing in them")
    }

    // -- fixtures ---------------------------------------------------------------------------------

    /** A phase generation left empty, on a real path, so the real mapper hides it. */
    private fun hiddenPhase(path: OnboardingPath, position: Int, title: String, status: GenerationStatus) =
        OnboardingPhase(
            path = path,
            position = position,
            title = title,
            description = "What the phase was meant to cover",
            generationStatus = status,
        ).also { path.phases += it }

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
