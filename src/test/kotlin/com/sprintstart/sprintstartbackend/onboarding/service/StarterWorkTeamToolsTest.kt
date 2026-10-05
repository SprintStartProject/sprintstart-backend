package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CandidatePoolState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkCandidateResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class StarterWorkTeamToolsTest {
    private val repository: StarterWorkTaskProposalRepository = mockk()
    private val service: StarterWorkTaskProposalService = mockk()
    private val scope: StarterWorkScope = mockk()
    private val tools = StarterWorkTeamTools(repository, service, scope)

    private val projectId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private val mine = "github:acme/shop"
    private val theirs = "github:acme/billing"

    init {
        every { scope.onProject(any<List<Any>>(), projectId, any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val sourceIdOf = thirdArg<(Any) -> String>()
            firstArg<List<Any>>().filter { sourceIdOf(it).startsWith("$mine:") }
        }
    }

    private fun call(name: String, vararg args: Pair<String, String>) =
        BuddyToolCallDto(
            id = "c1",
            name = name,
            arguments = JsonObject(args.associate { it.first to JsonPrimitive(it.second) }),
        )

    private fun task(title: String, repo: String = mine, reviewed: Boolean = false, taskZero: Boolean = false) =
        StarterWorkTaskProposal(
            sourceId = "$repo:ISSUE:${title.hashCode()}",
            title = title,
            summary = "A small fix.",
            sourceUrl = "https://github.com/acme/issues/1",
            reviewed = reviewed,
            taskZeroEligible = taskZero,
        )

    private fun candidate(
        title: String,
        repo: String = mine,
        state: CandidatePoolState = CandidatePoolState.AVAILABLE,
    ) =
        StarterWorkCandidateResponse(
            sourceId = "$repo:ISSUE:${title.hashCode()}",
            tracker = "GITHUB",
            title = title,
            excerpt = "The login test fails\non every second run.",
            excerptTruncated = false,
            labels = listOf("good first issue"),
            sourceUrl = null,
            hasAssignee = null,
            poolState = state,
            updatedAtSource = Instant.now(),
        )

    @Test
    fun `is the starter-work area and handles exactly its three reads`() {
        assertThat(tools.area).isEqualTo(TeamArea.STARTER_WORK)
        assertThat(tools.toolSpecs().map { it.name }).containsExactly(
            StarterWorkTeamTools.LIST_STARTER_WORK_POOL,
            StarterWorkTeamTools.LIST_UNREVIEWED_STARTER_WORK,
            StarterWorkTeamTools.LIST_STARTER_WORK_CANDIDATES,
        )
        assertThat(tools.handles("reject_starter_work")).isFalse()
    }

    @Test
    fun `lists only the pool tasks from the project's repositories, with their id and state`() {
        val reviewed = task("Fix flaky login test", reviewed = true, taskZero = true)
        val elsewhere = task("Billing rounding", repo = theirs)
        every { repository.findAllByStatus(ProposalStatus.LIVE) } returns listOf(reviewed, elsewhere)

        val result = tools.execute(call(StarterWorkTeamTools.LIST_STARTER_WORK_POOL), context)

        assertThat(result)
            .contains("“Fix flaky login test” [task_id: ${reviewed.id}] — acme/shop, reviewed, Task 0 candidate")
            .contains("Note: A small fix.")
            .doesNotContain("Billing rounding")
    }

    @Test
    fun `says so when the project's repositories have nothing in the pool`() {
        every { repository.findAllByStatus(ProposalStatus.LIVE) } returns listOf(task("Billing", repo = theirs))

        assertThat(tools.execute(call(StarterWorkTeamTools.LIST_STARTER_WORK_POOL), context))
            .contains("no tasks from this project's repositories")
    }

    @Test
    fun `lists the unreviewed tasks of the project only`() {
        val open = task("Fix flaky login test")
        every { repository.findAllByStatusAndReviewedFalse(ProposalStatus.LIVE) } returns
            listOf(open, task("Billing rounding", repo = theirs))

        val result = tools.execute(call(StarterWorkTeamTools.LIST_UNREVIEWED_STARTER_WORK), context)

        assertThat(result)
            .contains("[task_id: ${open.id}]")
            .contains("not reviewed")
            .doesNotContain("Billing rounding")
    }

    @Test
    fun `lists promotable issues only, with the source id to promote them by`() {
        val available = candidate("Fix flaky login test")
        every { service.listCandidates(projectId) } returns listOf(
            available,
            candidate("Already pooled", state = CandidatePoolState.IN_POOL),
            candidate("Removed", state = CandidatePoolState.REMOVED),
            candidate("Jira thing", repo = "jira:SHOP"),
        )

        val result = tools.execute(call(StarterWorkTeamTools.LIST_STARTER_WORK_CANDIDATES), context)

        assertThat(result)
            .contains("“Fix flaky login test” [source_id: ${available.sourceId}] — acme/shop, labels: good first issue")
            .contains("The login test fails on every second run.")
            .doesNotContain("Already pooled")
            .doesNotContain("Removed")
            .doesNotContain("Jira thing")
    }

    @Test
    fun `narrows candidates by search and says when nothing matches`() {
        every { service.listCandidates(projectId) } returns
            listOf(candidate("Fix flaky login test"), candidate("Upgrade Gradle"))

        assertThat(
            tools.execute(call(StarterWorkTeamTools.LIST_STARTER_WORK_CANDIDATES, "search" to "gradle"), context),
        ).contains("Upgrade Gradle")
            .doesNotContain("flaky")
        assertThat(
            tools.execute(call(StarterWorkTeamTools.LIST_STARTER_WORK_CANDIDATES, "search" to "docker"), context),
        ).contains("No open issue outside the starter-work pool matches “docker”")
    }
}
