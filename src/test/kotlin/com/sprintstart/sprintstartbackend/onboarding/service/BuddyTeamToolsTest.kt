package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.AttentionItemResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.AttentionSeverity
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.ProjectAttentionResponse
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import java.util.UUID

class BuddyTeamToolsTest {
    private val projectAttentionService: ProjectAttentionService = mockk()
    private val onboardingMetricsService: OnboardingMetricsService = mockk()
    private val myCompetencyService: MyCompetencyService = mockk()
    private val arrivalStepService: ArrivalStepService = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val areaToolsProvider: ObjectProvider<TeamAreaTools> = mockk()
    private val buddyProposalService: BuddyProposalService = mockk()

    private val projectId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private fun tools(
        vararg areas: TeamAreaTools,
        actions: List<TeamActionHandler> = emptyList(),
    ): BuddyTeamTools {
        every { areaToolsProvider.orderedStream() } answers { areas.toList().stream() }
        every { buddyProposalService.actionAreas() } answers { actions.map { it.area }.toSet() }
        every { buddyProposalService.actionSpecs(any()) } answers {
            val opened = firstArg<Set<TeamArea>>()
            actions.filter { it.area in opened }.map { it.spec }
        }
        return BuddyTeamTools(
            projectAttentionService,
            onboardingMetricsService,
            myCompetencyService,
            arrivalStepService,
            projectMembershipApi,
            areaToolsProvider,
            buddyProposalService,
        )
    }

    private fun action(name: String, area: TeamArea) =
        object : TeamActionHandler {
            override val area = area
            override val risk = BuddyProposalRisk.STANDARD
            override val spec = spec(name)

            override fun draft(call: BuddyToolCallDto, context: TeamToolContext) = TeamActionDraft.Refused("unused")

            override fun recheck(params: JsonObject, context: TeamToolContext): String? = null

            override fun perform(params: JsonObject, context: TeamToolContext) = "unused"
        }

    private fun spec(name: String) =
        BuddyToolSpecDto(name = name, description = "", parameters = JsonObject(emptyMap()))

    private fun call(name: String, vararg args: Pair<String, String>) =
        BuddyToolCallDto(
            id = "call-1",
            name = name,
            arguments = JsonObject(args.associate { it.first to JsonPrimitive(it.second) }),
        )

    private fun member(id: UUID, name: String, login: String? = null) =
        ProjectMember(userId = id, displayName = name, githubLogin = login, joinedAt = null)

    private val knowledgeArea = object : TeamAreaTools {
        override val area = TeamArea.KNOWLEDGE

        override fun toolSpecs() = listOf(spec("list_open_escalations"))

        override fun handles(toolName: String) = toolName == "list_open_escalations"

        override fun execute(call: BuddyToolCallDto, context: TeamToolContext) = "escalations on ${context.projectId}"
    }

    private fun readNames(tools: BuddyTeamTools, opened: Set<TeamArea> = emptySet()) =
        tools.toolSpecs(opened).map { it.name }.toSet()

    /**
     * "Absent, never empty": an `open_area` with nothing behind it invites the model to open an area
     * and find nothing, so it is not mounted until some area has tools.
     */
    @Test
    fun `mounts the team reads and no open_area while no area has tools`() {
        val names = tools().toolSpecs(emptySet()).map { it.name }

        assertThat(names).containsExactly(
            BuddyTeamTools.GET_TEAM_ATTENTION,
            BuddyTeamTools.FIND_MEMBER,
            BuddyTeamTools.GET_MEMBER_PROGRESS,
        )
    }

    @Test
    fun `mounts open_area once an area has tools, and the area's tools only after it is opened`() {
        val tools = tools(knowledgeArea)

        assertThat(readNames(tools)).contains(BuddyTeamTools.OPEN_AREA).doesNotContain("list_open_escalations")
        assertThat(readNames(tools, setOf(TeamArea.KNOWLEDGE))).contains("list_open_escalations")
    }

    @Test
    fun `opening an area returns it and names its tools`() {
        val outcome = tools(knowledgeArea).openArea(call(BuddyTeamTools.OPEN_AREA, "area" to "knowledge"))

        assertThat(outcome.area).isEqualTo(TeamArea.KNOWLEDGE)
        assertThat(outcome.toolResult).contains("list_open_escalations")
    }

    @Test
    fun `opening an area with no tools opens nothing and names the areas there are`() {
        val outcome = tools(knowledgeArea).openArea(call(BuddyTeamTools.OPEN_AREA, "area" to "sources"))

        assertThat(outcome.area).isNull()
        assertThat(outcome.toolResult).contains("knowledge")
    }

    /**
     * Mounting is the access control. A model that names an area tool it was never handed on this hop
     * — from memory of an earlier turn, or by guessing — gets a refusal, never the tool.
     */
    @Test
    fun `refuses a tool that was not mounted on this hop`() {
        val result = tools(knowledgeArea).execute(
            call("list_open_escalations"),
            context,
            mountedToolNames = setOf(BuddyTeamTools.GET_TEAM_ATTENTION),
        )

        assertThat(result).contains("not available")
    }

    @Test
    fun `runs an opened area's tool for the turn's project`() {
        val result = tools(knowledgeArea).execute(
            call("list_open_escalations"),
            context,
            mountedToolNames = setOf("list_open_escalations"),
        )

        assertThat(result).isEqualTo("escalations on $projectId")
    }

    @Test
    fun `team attention says whose move a waiting pull request is`() {
        every { projectAttentionService.getAttention(projectId) } returns ProjectAttentionResponse(
            projectId = projectId,
            memberCount = 4,
            items = listOf(
                AttentionItemResponse(
                    hireId = memberId,
                    hireName = "Sam",
                    reason = "A pull request has been waiting 3 days for a response",
                    severity = AttentionSeverity.BLOCKED,
                    days = 3,
                ),
            ),
        )

        val result = tools().execute(
            call(BuddyTeamTools.GET_TEAM_ATTENTION),
            context,
            setOf(BuddyTeamTools.GET_TEAM_ATTENTION),
        )

        assertThat(result)
            .contains("Sam [member_id: $memberId]")
            .contains("waiting 3 days")
            .contains("somebody else's move, not theirs")
    }

    @Test
    fun `find_member matches by name and lists everyone without a query`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(
            member(memberId, "Sam Rivera", login = "samr"),
            member(UUID.randomUUID(), "Alex Chen"),
        )
        val tools = tools()
        val mounted = setOf(BuddyTeamTools.FIND_MEMBER)

        val matched = tools.execute(call(BuddyTeamTools.FIND_MEMBER, "query" to "sam"), context, mounted)
        val everyone = tools.execute(call(BuddyTeamTools.FIND_MEMBER), context, mounted)

        assertThat(matched).contains("Sam Rivera [member_id: $memberId]").doesNotContain("Alex Chen")
        assertThat(everyone).contains("Sam Rivera").contains("Alex Chen")
    }

    /**
     * The check that makes accepting a model-supplied id safe at all. The manager was authorised for
     * this project; somebody on another project is not theirs to read, and nothing about them is loaded.
     */
    @Test
    fun `member progress refuses somebody who is not on the project and reads nothing about them`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member(UUID.randomUUID(), "Alex"))

        val result = tools().execute(
            call(BuddyTeamTools.GET_MEMBER_PROGRESS, "member_id" to memberId.toString()),
            context,
            setOf(BuddyTeamTools.GET_MEMBER_PROGRESS),
        )

        assertThat(result).contains("not on this project")
        verify(exactly = 0) { onboardingMetricsService.getHireTimeline(any(), any()) }
        verify(exactly = 0) { myCompetencyService.getCompetenciesForUser(any()) }
        verify(exactly = 0) { arrivalStepService.forHireOn(any(), any()) }
    }

    @Test
    fun `member progress refuses an id that is not an id`() {
        val result = tools().execute(
            call(BuddyTeamTools.GET_MEMBER_PROGRESS, "member_id" to "Sam"),
            context,
            setOf(BuddyTeamTools.GET_MEMBER_PROGRESS),
        )

        assertThat(result).contains("find_member")
    }

    /**
     * A member on two projects has arrival steps on both. The tool asks the service for this project's
     * list rather than filtering the member's full one: the full list lets the other project override a
     * company step, and filtering it afterwards would hide a step that still applies here.
     */
    @Test
    fun `member progress reads this project's arrival steps, never the member's full list`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member(memberId, "Sam"))
        every { arrivalStepService.forHireOn(memberId, projectId) } returns listOf(
            outstandingStep(title = "Get staging access", stepProjectId = projectId),
            outstandingStep(title = "Sign the handbook", stepProjectId = null),
        )
        every { onboardingMetricsService.getHireTimeline(memberId, projectId) } returns null
        every { myCompetencyService.getCompetenciesForUser(memberId) } returns emptyList()

        val result = tools().execute(
            call(BuddyTeamTools.GET_MEMBER_PROGRESS, "member_id" to memberId.toString()),
            context,
            setOf(BuddyTeamTools.GET_MEMBER_PROGRESS),
        )

        assertThat(result).contains("Get staging access").contains("Sign the handbook")
        verify(exactly = 0) { arrivalStepService.forHire(any()) }
    }

    @Test
    fun `member progress reports contributions and demonstrated competencies on this project`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member(memberId, "Sam"))
        every { arrivalStepService.forHireOn(memberId, projectId) } returns emptyList()
        every { onboardingMetricsService.getHireTimeline(memberId, projectId) } returns mockk<HireTimelineResponse> {
            every { openContributionCount } returns 1
            every { acceptedContributionCount } returns 2
            every { longestOpenWaitHours } returns 50
            every { hoursToFirstResponse } returns null
            every { stalled } returns false
            every { stalledReason } returns null
            every { returnedContributionCount } returns 0
            every { autonomyReachedAt } returns null
        }
        every { myCompetencyService.getCompetenciesForUser(memberId) } returns listOf(
            competency(label = "Kotlin", level = 3, target = 3),
            competency(label = "Unplaced", level = 0, target = 2),
        )

        val result = tools().execute(
            call(BuddyTeamTools.GET_MEMBER_PROGRESS, "member_id" to memberId.toString()),
            context,
            setOf(BuddyTeamTools.GET_MEMBER_PROGRESS),
        )

        assertThat(result)
            .contains("Merged pull requests: 2")
            .contains("waiting on a reviewer: 50 hours")
            .contains("Kotlin (level 3/3)")
            .doesNotContain("Unplaced")
    }

    private fun outstandingStep(title: String, stepProjectId: UUID?): ResolvedArrivalStep {
        val step = mockk<ArrivalStep> {
            every { this@mockk.title } returns title
            every { projectId } returns stepProjectId
            every { selfConfirmable } returns true
        }
        return ResolvedArrivalStep(step = step, settledAt = null, rigor = null)
    }

    private fun settledStep(title: String, stepProjectId: UUID?, rigor: Rigor): ResolvedArrivalStep {
        val step = mockk<ArrivalStep> {
            every { this@mockk.title } returns title
            every { projectId } returns stepProjectId
            every { selfConfirmable } returns true
        }
        return ResolvedArrivalStep(step = step, settledAt = Instant.now(), rigor = rigor)
    }

    private fun competency(label: String, level: Int, target: Int): MyCompetencyResponse =
        mockk {
            every { this@mockk.label } returns label
            every { this@mockk.level } returns level
            every { targetLevel } returns target
            every { updatedAt } returns Instant.now()
        }

    /**
     * What the system confirmed and what the member only told us are different facts, and a manager
     * deciding whether to chase an access grant needs to know which one they are looking at.
     */
    @Test
    fun `member progress says how each settled step was settled, and never totals them`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member(memberId, "Sam"))
        every { arrivalStepService.forHireOn(memberId, projectId) } returns listOf(
            settledStep(title = "GitHub account", stepProjectId = null, rigor = Rigor.OBSERVED),
            settledStep(title = "Read the handbook", stepProjectId = projectId, rigor = Rigor.DECLARED),
        )
        every { onboardingMetricsService.getHireTimeline(memberId, projectId) } returns null
        every { myCompetencyService.getCompetenciesForUser(memberId) } returns emptyList()

        val result = tools().execute(
            call(BuddyTeamTools.GET_MEMBER_PROGRESS, "member_id" to memberId.toString()),
            context,
            setOf(BuddyTeamTools.GET_MEMBER_PROGRESS),
        )

        assertThat(result)
            .contains("GitHub account (we confirmed this)")
            .contains("Read the handbook (they told us)")
            .doesNotContain(" of ")
    }

    /** An area whose only tools are actions still has something behind it, so it can be opened. */
    @Test
    fun `an area with only actions can be opened, and its actions are mounted only after opening`() {
        val tools = tools(actions = listOf(action("answer_escalation", TeamArea.KNOWLEDGE)))

        assertThat(readNames(tools)).contains(BuddyTeamTools.OPEN_AREA).doesNotContain("answer_escalation")
        assertThat(tools.openArea(call(BuddyTeamTools.OPEN_AREA, "area" to "knowledge")).area)
            .isEqualTo(TeamArea.KNOWLEDGE)
        assertThat(readNames(tools, setOf(TeamArea.KNOWLEDGE))).contains("answer_escalation")
    }
}
