package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEventType
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import com.sprintstart.sprintstartbackend.assistant.external.HireActionProposal
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class BuddyToolCallRunnerTest {
    private val buddyToolExecutor = mockk<BuddyToolExecutor>()
    private val buddyActionService = mockk<BuddyActionService>()
    private val runner = BuddyToolCallRunner(buddyToolExecutor, buddyActionService)
    private val user = UserId(UUID.randomUUID())
    private val emitted = mutableListOf<BuddyStreamEvent>()
    private val collector = FlowCollector<BuddyStreamEvent> { emitted += it }

    @Test
    fun `a read-only tool announces itself and returns the executor's result`() {
        val call = BuddyToolCallDto(id = "c1", name = "get_my_tasks")
        every { buddyActionService.isAction("get_my_tasks") } returns false
        every { buddyToolExecutor.execute(call, user.value, "/board") } returns "two tasks"

        val result = runBlocking { runner.run(collector, call, user, "/board") }

        assertThat(result).isEqualTo("two tasks")
        assertThat(emitted).hasSize(1)
        assertThat(emitted.single().type).isEqualTo(BuddyStreamEventType.TOOL_USE)
        assertThat(emitted.single().name).isEqualTo("get_my_tasks")
        verify(exactly = 0) { buddyActionService.propose(any(), any()) }
    }

    @Test
    fun `an action tool emits its proposal and returns the outcome's result`() {
        val call = BuddyToolCallDto(id = "c2", name = "claim_task")
        val proposal = HireActionProposal(action = "claim_task", label = "Claim it", question = "Claim?")
        every { buddyActionService.isAction("claim_task") } returns true
        every { buddyActionService.propose(call, user.value) } returns HireActionOutcome("proposed", proposal)

        val result = runBlocking { runner.run(collector, call, user, null) }

        assertThat(result).isEqualTo("proposed")
        assertThat(emitted).containsExactly(proposal.toStreamEvent())
        coVerify(exactly = 0) { buddyToolExecutor.execute(any(), any(), any()) }
    }

    @Test
    fun `a refused action emits nothing and returns the reason`() {
        val call = BuddyToolCallDto(id = "c3", name = "claim_task")
        every { buddyActionService.isAction("claim_task") } returns true
        every { buddyActionService.propose(call, user.value) } returns HireActionOutcome("not allowed", null)

        val result = runBlocking { runner.run(collector, call, user, null) }

        assertThat(result).isEqualTo("not allowed")
        assertThat(emitted).isEmpty()
    }

    @Test
    fun `every field of a proposal reaches the stream event`() {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val other = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val proposal = HireActionProposal(
            action = "act",
            label = "Label",
            question = "Q?",
            taskId = id,
            title = "T",
            attesterId = "att",
            githubLogin = "gh",
            competencyKey = "ck",
            level = "lvl",
            stepId = id,
            questionId = id,
            phaseId = id,
            onboardingTaskId = id,
            answer = "ans",
            optionIds = listOf(id, other),
            description = "d",
            reason = "r",
            waitsOnIds = listOf(id),
            unlocksIds = listOf(other),
            checklistTitle = "ct",
            checklistItems = listOf("a", "b"),
            cardId = id,
            noteText = "note",
            lineBefore = "before",
            lineAfter = "after",
            linkUrl = "https://x",
            linkLabel = "x",
            cardIds = listOf(id, other),
            cardNames = listOf("n1", "n2"),
            basedOn = "fp",
            preview = "pv",
        )

        val event = proposal.toStreamEvent()

        assertThat(event).isEqualTo(
            BuddyStreamEvent(
                type = BuddyStreamEventType.ACTION_PROPOSAL,
                action = "act",
                label = "Label",
                question = "Q?",
                taskId = id.toString(),
                title = "T",
                attesterId = "att",
                githubLogin = "gh",
                competencyKey = "ck",
                level = "lvl",
                stepId = id.toString(),
                questionId = id.toString(),
                phaseId = id.toString(),
                onboardingTaskId = id.toString(),
                answer = "ans",
                optionIds = listOf(id.toString(), other.toString()),
                description = "d",
                reason = "r",
                waitsOnIds = listOf(id.toString()),
                unlocksIds = listOf(other.toString()),
                checklistTitle = "ct",
                checklistItems = listOf("a", "b"),
                cardId = id.toString(),
                noteText = "note",
                lineBefore = "before",
                lineAfter = "after",
                linkUrl = "https://x",
                linkLabel = "x",
                cardIds = listOf(id.toString(), other.toString()),
                cardNames = listOf("n1", "n2"),
                basedOn = "fp",
                preview = "pv",
            ),
        )
    }

    @Test
    fun `empty id lists are left off the stream event`() {
        val event = HireActionProposal(action = "act", label = "L", question = null).toStreamEvent()

        assertThat(event.optionIds).isNull()
        assertThat(event.waitsOnIds).isNull()
        assertThat(event.unlocksIds).isNull()
    }
}
