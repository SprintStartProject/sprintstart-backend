package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.ActionProposal
import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEventType
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.buddy.service.result
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class TeamToolRunnerTest {
    private val buddyTeamTools: BuddyTeamTools = mockk()
    private val teamProposalService: TeamProposalService = mockk()
    private val runner = TeamToolRunner(buddyTeamTools, teamProposalService)

    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = UUID.randomUUID())
    private val events = mutableListOf<BuddyStreamEvent>()
    private val emit: suspend (BuddyStreamEvent) -> Unit = { events += it }

    private fun call(name: String) = BuddyToolCallDto(id = "c1", name = name)

    private fun proposal() =
        ActionProposal(
            id = UUID.randomUUID(),
            userId = context.userId,
            projectId = context.projectId,
            action = "answer_escalation",
            params = JsonObject(emptyMap()),
            label = "Answer: how do we deploy?",
            preview = "The answer is published.",
            risk = BuddyProposalRisk.STANDARD,
        )

    @Test
    fun `a mounted action becomes a proposal event and is never run`() = runTest {
        val areas = OpenAreas(setOf(TeamArea.KNOWLEDGE))
        val stored = proposal()
        every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE
        every { teamProposalService.isAction("answer_escalation") } returns true
        every { teamProposalService.propose(any(), any()) } returns
            TeamProposalService.ProposeOutcome(toolResult = "Offered.", proposal = stored)

        val result = runner.run(call("answer_escalation"), context, setOf("answer_escalation"), areas, emit)

        assertThat(result).isEqualTo("Offered.")
        assertThat(events.single().type).isEqualTo(BuddyStreamEventType.ACTION_PROPOSAL)
        assertThat(events.single().proposalId).isEqualTo(stored.id.toString())
        verify(exactly = 0) { buddyTeamTools.execute(any(), any(), any()) }
    }

    @Test
    fun `a refused action tells the model why and shows the manager nothing`() = runTest {
        every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE
        every { teamProposalService.isAction("answer_escalation") } returns true
        every { teamProposalService.propose(any(), any()) } returns
            TeamProposalService.ProposeOutcome(toolResult = "No such question.", proposal = null)

        val result = runner.run(
            call("answer_escalation"),
            context,
            setOf("answer_escalation"),
            OpenAreas(setOf(TeamArea.KNOWLEDGE)),
            emit,
        )

        assertThat(result).isEqualTo("No such question.")
        assertThat(events).isEmpty()
    }

    @Test
    fun `using a tool keeps its area open for the next replies`() = runTest {
        val areas = OpenAreas(setOf(TeamArea.KNOWLEDGE))
        every { buddyTeamTools.areaOf("list_open_escalations") } returns TeamArea.KNOWLEDGE
        every { teamProposalService.isAction("list_open_escalations") } returns false
        every { buddyTeamTools.execute(any(), any(), any()) } returns "Two open questions."

        val result = runner.run(call("list_open_escalations"), context, setOf("list_open_escalations"), areas, emit)

        assertThat(result).isEqualTo("Two open questions.")
        assertThat(areas.activeThisTurn).containsExactly(TeamArea.KNOWLEDGE)
        assertThat(events.single().type).isEqualTo(BuddyStreamEventType.TOOL_USE)
    }

    @Test
    fun `open_area opens the area it names`() = runTest {
        val areas = OpenAreas()
        every { buddyTeamTools.areaOf(BuddyTeamTools.OPEN_AREA) } returns null
        every { teamProposalService.isAction(BuddyTeamTools.OPEN_AREA) } returns false
        every { buddyTeamTools.openArea(any()) } returns
            BuddyTeamTools.OpenAreaOutcome(TeamArea.TEAM, "Opened team.")

        val result = runner.run(call(BuddyTeamTools.OPEN_AREA), context, setOf(BuddyTeamTools.OPEN_AREA), areas, emit)

        assertThat(result).isEqualTo("Opened team.")
        assertThat(areas.mounted).containsExactly(TeamArea.TEAM)
    }

    @Test
    fun `a tool called before its area is open opens the area and is not run`() = runTest {
        val areas = OpenAreas()
        every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE

        val result = runner.run(call("answer_escalation"), context, setOf(BuddyTeamTools.OPEN_AREA), areas, emit)

        assertThat(result).contains("belongs to the knowledge area").contains("call answer_escalation again")
        assertThat(areas.mounted).containsExactly(TeamArea.KNOWLEDGE)
        assertThat(events.single().name).isEqualTo(BuddyTeamTools.OPEN_AREA)
        verify(exactly = 0) { teamProposalService.propose(any(), any()) }
        verify(exactly = 0) { buddyTeamTools.execute(any(), any(), any()) }
    }

    @Test
    fun `a tool that belongs to no area is left to BuddyTeamTools to refuse`() = runTest {
        every { buddyTeamTools.areaOf("nonsense") } returns null
        every { buddyTeamTools.execute(any(), any(), any()) } returns "The tool nonsense is not available right now."

        val result = runner.run(call("nonsense"), context, setOf(BuddyTeamTools.OPEN_AREA), OpenAreas(), emit)

        assertThat(result).contains("not available")
    }

    @Test
    fun `without open_area mounted an unopened tool is not opened for the model`() = runTest {
        val areas = OpenAreas()
        every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE
        every { buddyTeamTools.execute(any(), any(), any()) } returns
            "The tool answer_escalation is not available right now."
        every { teamProposalService.isAction(any()) } returns false

        runner.run(call("answer_escalation"), context, emptySet(), areas, emit)

        assertThat(areas.mounted).isEmpty()
    }

    @Test
    fun `a tool call written out as text opens the areas of the tools it names`() = runTest {
        val areas = OpenAreas()
        every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE

        val told = runner.openAreasWrittenOut(
            """{"name":"answer_escalation","parameters":{}}""",
            setOf(BuddyTeamTools.OPEN_AREA),
            areas,
            emit,
        )

        assertThat(told).contains("answer_escalation").contains("is open now")
        assertThat(areas.mounted).containsExactly(TeamArea.KNOWLEDGE)
    }

    @Test
    fun `a written-out call to an unknown tool just gets the standard reminder`() = runTest {
        every { buddyTeamTools.areaOf("nonsense") } returns null

        val told = runner.openAreasWrittenOut(
            """{"name":"nonsense","parameters":{}}""",
            setOf(BuddyTeamTools.OPEN_AREA),
            OpenAreas(),
            emit,
        )

        assertThat(told).isEqualTo(TOOL_CALL_WRITTEN_OUT)
        assertThat(events).isEmpty()
    }

    @Test
    fun `without open_area mounted a written-out call gets the standard reminder`() = runTest {
        val told = runner.openAreasWrittenOut(
            """{"name":"answer_escalation","parameters":{}}""",
            emptySet(),
            OpenAreas(),
            emit,
        )

        assertThat(told).isEqualTo(TOOL_CALL_WRITTEN_OUT)
    }
}
