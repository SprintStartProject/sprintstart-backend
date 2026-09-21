package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingGenerationIssueResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTasksResponse
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

class ContentTeamToolsTest {
    private val f = ContentFixture()
    private val pathService: OnboardingPathService = mockk(relaxed = true)
    private val stepService: OnboardingStepService = mockk(relaxed = true)
    private val tools = ContentTeamTools(
        f.scope,
        f.pathElements,
        pathService,
        stepService,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
    )

    private val phaseId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()
    private val resourceId = UUID.randomUUID()

    private fun phase(description: String) =
        GetOnboardingPhaseForUserResponse(
            phaseId,
            UUID.randomUUID(),
            0,
            "Setup",
            description,
            locked = false,
            steps = emptyList(),
        )

    private fun path() {
        every { pathService.getOnboardingPathByUserId(f.memberId) } returns
            GetOnboardingPathForUserResponse(
                id = UUID.randomUUID(),
                userId = f.memberId,
                createdAt = Instant.now(),
                phases = listOf(
                    phase("Get the machine ready"),
                ),
            )
        every { stepService.getOnboardingStepsByPhaseId(phaseId) } returns
            listOf(
                GetOnboardingStepResponse(
                    id = stepId,
                    phaseId = phaseId,
                    position = 0,
                    title = "Install",
                    description = "Install the toolchain",
                    type = StepType.TASK,
                    estimatedMinutes = 20,
                    isAiAssisted = false,
                    tasks = listOf(GetOnboardingTasksResponse(taskId, stepId, 0, "Clone", "d", finished = true)),
                    resources = listOf(
                        GetOnboardingResourcesResponse(resourceId, stepId, "Docs", "d", "https://docs.example.com"),
                    ),
                    status = StepStatus.IN_PROGRESS,
                    completedAt = null,
                    skip = null,
                ),
            )
    }

    private fun read(memberId: Any) = tools.execute(f.call("get_member_path", "member_id" to memberId), f.context)

    @Test
    fun `mounts exactly the reads of the area, in the content area`() {
        assertThat(tools.area).isEqualTo(TeamArea.CONTENT)
        assertThat(tools.toolSpecs().map { it.name }).containsExactly(
            "get_member_path",
            "list_pending_skips",
            "list_feedback",
            "get_phase_checks",
            "get_orientation_packet",
        )
        assertThat(tools.toolSpecs().all { tools.handles(it.name) }).isTrue()
        assertThat(tools.handles("add_phase")).isFalse()
    }

    @Test
    fun `prints the whole path with the id every action needs`() {
        path()

        val text = read(f.memberId)

        assertThat(text).contains(
            "Sam Rivera's onboarding path",
            "Phase 1: Setup [phase_id: $phaseId]",
            "Step 1: Install [step_id: $stepId]",
            "task, 20 min, in progress",
            "[x] Clone [task_id: $taskId]",
            "link: Docs <https://docs.example.com> [resource_id: $resourceId]",
        )
    }

    @Test
    fun `phases that produced nothing are listed as not shown to them`() {
        val hiddenId = UUID.randomUUID()
        every { pathService.getOnboardingPathByUserId(f.memberId) } returns
            GetOnboardingPathForUserResponse(
                UUID.randomUUID(),
                f.memberId,
                Instant.now(),
                emptyList(),
                generationIssues = listOf(
                    OnboardingGenerationIssueResponse(hiddenId, "Deep dive", GenerationStatus.FAILED),
                ),
            )

        assertThat(read(f.memberId)).contains("Not shown to them", "Deep dive [phase_id: $hiddenId] (failed)")
    }

    @Test
    fun `says when the path is also somebody else's`() {
        path()
        f.alsoOn("Payments")

        assertThat(read(f.memberId)).contains("also on Payments", "one onboarding path")
    }

    @Test
    fun `long descriptions are cut short so the ids stay findable`() {
        path()
        val long = "word ".repeat(200)
        every { pathService.getOnboardingPathByUserId(f.memberId) } returns
            GetOnboardingPathForUserResponse(
                UUID.randomUUID(),
                f.memberId,
                Instant.now(),
                listOf(phase(long)),
            )

        val text = read(f.memberId)

        assertThat(text).contains("…")
        assertThat(text.length).isLessThan(long.length)
    }

    @Test
    fun `somebody not on the project cannot be read, and there is nothing to probe`() {
        path()

        assertThat(read(f.outsiderId)).contains("not on this project")
        assertThat(read("not-an-id")).contains("not on this project")
    }

    @Test
    fun `a member with no path is said to have none`() {
        every { pathService.getOnboardingPathByUserId(f.memberId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        assertThat(read(f.memberId)).isEqualTo("Sam Rivera has no onboarding path yet.")
    }

    @Test
    fun `an unknown tool is named as such`() {
        assertThat(tools.execute(f.call("nope"), f.context)).isEqualTo("Unknown tool: nope.")
    }
}
