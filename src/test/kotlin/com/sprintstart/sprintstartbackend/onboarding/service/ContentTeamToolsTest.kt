package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
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
    private val tools = ContentTeamTools(f.scope, pathService, stepService)

    private val phaseId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()
    private val resourceId = UUID.randomUUID()

    private fun path() {
        every { pathService.getOnboardingPathByUserId(f.memberId) } returns
            GetOnboardingPathResponse(
                id = UUID.randomUUID(),
                userId = f.memberId,
                createdAt = Instant.now(),
                phases = listOf(
                    GetOnboardingPhasesResponse(phaseId, UUID.randomUUID(), 0, "Setup", "Get the machine ready"),
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
        assertThat(tools.toolSpecs().map { it.name }).containsExactly("get_member_path")
        assertThat(tools.handles("get_member_path")).isTrue()
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
            GetOnboardingPathResponse(
                UUID.randomUUID(),
                f.memberId,
                Instant.now(),
                listOf(GetOnboardingPhasesResponse(phaseId, UUID.randomUUID(), 0, "Setup", long)),
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
