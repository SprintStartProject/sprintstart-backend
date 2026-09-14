package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyActionProposal
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyActionProposalRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class BuddyProposalServiceTest {
    private val repository: BuddyActionProposalRepository = mockk()
    private val userApi: UserApi = mockk()
    private val handlersProvider: ObjectProvider<TeamActionHandler> = mockk()

    private val now: Instant = Instant.parse("2026-09-20T10:00:00Z")
    private var clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val authId = "auth|pm"
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val context = TeamToolContext(userId = userId, authId = authId, projectId = projectId)

    /** A scriptable action: what it drafts, whether its target is still valid, and what performing does. */
    private inner class FakeAction : TeamActionHandler {
        override val area = TeamArea.KNOWLEDGE
        override val risk = BuddyProposalRisk.DESTRUCTIVE
        override val spec = BuddyToolSpecDto(
            name = "dismiss_escalation",
            description = "",
            parameters = JsonObject(emptyMap()),
        )

        var draftResult: TeamActionDraft = TeamActionDraft.Proposed(
            params = JsonObject(mapOf("request_id" to JsonPrimitive("r-1"))),
            label = "Dismiss: how do we deploy?",
            preview = "The question disappears from the inbox.",
        )
        var recheckResult: String? = null
        var performAnswer: (JsonObject) -> String = { "Dismissed the question." }
        val performed = mutableListOf<JsonObject>()

        override fun draft(call: BuddyToolCallDto, context: TeamToolContext) = draftResult

        override fun recheck(params: JsonObject, context: TeamToolContext) = recheckResult

        override fun perform(params: JsonObject, context: TeamToolContext): String {
            performed.add(params)
            return performAnswer(params)
        }
    }

    private val action = FakeAction()

    /** Every outcome recorded through `finish`, as (status it ended in, message shown). */
    private val recorded = mutableListOf<Pair<BuddyProposalStatus, String>>()

    private fun service() = BuddyProposalService(repository, userApi, handlersProvider, clock)

    @BeforeEach
    fun setUp() {
        every { handlersProvider.orderedStream() } answers { listOf<TeamActionHandler>(action).stream() }
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.canManageProject(authId, projectId) } returns true
        every { repository.save(any()) } answers { firstArg() }
        every { repository.finish(any(), BuddyProposalStatus.CONFIRMING, any(), any(), any()) } answers {
            recorded.add(thirdArg<BuddyProposalStatus>() to arg<String>(4))
            1
        }
    }

    private fun stored(
        status: BuddyProposalStatus = BuddyProposalStatus.PROPOSED,
        owner: UUID = userId,
        expiresAt: Instant = now.plusSeconds(3600),
    ): BuddyActionProposal {
        val proposal = BuddyActionProposal(
            userId = owner,
            projectId = projectId,
            action = "dismiss_escalation",
            params = """{"request_id":"r-1"}""",
            label = "Dismiss: how do we deploy?",
            preview = "The question disappears from the inbox.",
            risk = BuddyProposalRisk.DESTRUCTIVE,
            status = status,
            createdAt = now.minusSeconds(60),
            expiresAt = expiresAt,
        )
        every { repository.findById(proposal.id) } returns Optional.of(proposal)
        return proposal
    }

    private fun call() = BuddyToolCallDto(id = "c1", name = "dismiss_escalation")

    @Test
    fun `mounts an area's actions only once it is opened`() {
        val service = service()

        assertThat(service.actionAreas()).containsExactly(TeamArea.KNOWLEDGE)
        assertThat(service.actionSpecs(emptySet())).isEmpty()
        assertThat(service.actionSpecs(setOf(TeamArea.KNOWLEDGE)).map { it.name }).containsExactly("dismiss_escalation")
        assertThat(service.isAction("dismiss_escalation")).isTrue()
        assertThat(service.isAction("get_team_attention")).isFalse()
    }

    /** Proposing writes the proposal and nothing else: the action itself does not run. */
    @Test
    fun `proposing stores the draft with the action's own risk and a day to confirm it`() {
        val saved = slot<BuddyActionProposal>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        val outcome = service().propose(call(), context)

        assertThat(outcome.proposal).isNotNull
        with(saved.captured) {
            assertThat(userId).isEqualTo(this@BuddyProposalServiceTest.userId)
            assertThat(projectId).isEqualTo(this@BuddyProposalServiceTest.projectId)
            assertThat(action).isEqualTo("dismiss_escalation")
            assertThat(risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
            assertThat(status).isEqualTo(BuddyProposalStatus.PROPOSED)
            assertThat(expiresAt).isEqualTo(now.plus(BuddyProposalService.TIME_TO_LIVE))
            assertThat(params).contains("r-1")
        }
        assertThat(outcome.toolResult).contains("never say it is done")
        assertThat(action.performed).isEmpty()
    }

    @Test
    fun `a refused draft stores nothing and tells the model why`() {
        action.draftResult = TeamActionDraft.Refused("That escalation is on another project.")

        val outcome = service().propose(call(), context)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).isEqualTo("That escalation is on another project.")
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `confirming runs exactly the stored params once and records it as confirmed`() {
        val proposal = stored()
        every {
            repository.transition(proposal.id, BuddyProposalStatus.PROPOSED, BuddyProposalStatus.CONFIRMING, now)
        } returns 1

        val response = service().confirm(authId, proposal.id)

        assertThat(response.ok).isTrue()
        assertThat(response.message).isEqualTo("Dismissed the question.")
        val performedWith = action.performed.single()["request_id"] as JsonPrimitive
        assertThat(performedWith.contentOrNull).isEqualTo("r-1")
        assertThat(recorded).containsExactly(BuddyProposalStatus.CONFIRMED to "Dismissed the question.")
    }

    /** A proposal id says nothing about whether somebody else's proposal exists. */
    @Test
    fun `confirming somebody else's proposal is a 404 and runs nothing`() {
        val proposal = stored(owner = UUID.randomUUID())

        assertThrows<ResponseStatusException> { service().confirm(authId, proposal.id) }
            .also { assertThat(it.statusCode).isEqualTo(HttpStatus.NOT_FOUND) }
        assertThat(action.performed).isEmpty()
        verify(exactly = 0) { repository.transition(any(), any(), any(), any()) }
    }

    @Test
    fun `confirming an already decided proposal says so and runs nothing`() {
        val proposal = stored(status = BuddyProposalStatus.DISMISSED)

        val response = service().confirm(authId, proposal.id)

        assertThat(response.ok).isFalse()
        assertThat(response.message).contains("dismissed")
        assertThat(action.performed).isEmpty()
    }

    /** A preview written about yesterday's state is not confirmed as if it were current. */
    @Test
    fun `confirming an expired proposal marks it expired and runs nothing`() {
        val proposal = stored(expiresAt = now.minusSeconds(1))
        every {
            repository.transition(proposal.id, BuddyProposalStatus.PROPOSED, BuddyProposalStatus.EXPIRED, now)
        } returns 1

        val response = service().confirm(authId, proposal.id)

        assertThat(response.ok).isFalse()
        assertThat(response.message).contains("expired")
        assertThat(action.performed).isEmpty()
        verify { repository.transition(proposal.id, BuddyProposalStatus.PROPOSED, BuddyProposalStatus.EXPIRED, now) }
    }

    /** Two confirms that both saw it open: only the one that wins the claim performs anything. */
    @Test
    fun `a confirm that loses the claim runs nothing`() {
        val proposal = stored()
        every { repository.transition(proposal.id, any(), BuddyProposalStatus.CONFIRMING, any()) } returns 0

        val response = service().confirm(authId, proposal.id)

        assertThat(response.ok).isFalse()
        assertThat(action.performed).isEmpty()
        assertThat(recorded).isEmpty()
    }

    @Test
    fun `a manager who lost the project since the proposal cannot confirm it`() {
        val proposal = stored()
        every { repository.transition(proposal.id, any(), BuddyProposalStatus.CONFIRMING, any()) } returns 1
        every { userApi.canManageProject(authId, projectId) } returns false

        val response = service().confirm(authId, proposal.id)

        assertThat(response.ok).isFalse()
        assertThat(response.message).contains("no longer manage")
        assertThat(action.performed).isEmpty()
        assertThat(recorded.single().first).isEqualTo(BuddyProposalStatus.FAILED)
    }

    @Test
    fun `a target that changed since the preview is refused with the action's reason`() {
        val proposal = stored()
        every { repository.transition(proposal.id, any(), BuddyProposalStatus.CONFIRMING, any()) } returns 1
        action.recheckResult = "Somebody already answered that question."

        val response = service().confirm(authId, proposal.id)

        assertThat(response.ok).isFalse()
        assertThat(response.message).isEqualTo("Somebody already answered that question.")
        assertThat(action.performed).isEmpty()
        assertThat(recorded.single().first).isEqualTo(BuddyProposalStatus.FAILED)
    }

    @Test
    fun `a handled failure in the action is shown and recorded, not thrown`() {
        val proposal = stored()
        every { repository.transition(proposal.id, any(), BuddyProposalStatus.CONFIRMING, any()) } returns 1
        action.performAnswer = { throw ResponseStatusException(HttpStatus.CONFLICT, "That question is gone.") }

        val response = service().confirm(authId, proposal.id)

        assertThat(response.ok).isFalse()
        assertThat(response.message).isEqualTo("That question is gone.")
        assertThat(recorded.single().first).isEqualTo(BuddyProposalStatus.FAILED)
    }

    /** A claimed proposal never stays in CONFIRMING, whatever the action throws. */
    @Test
    fun `an unexpected failure still records the proposal as failed and propagates`() {
        val proposal = stored()
        every { repository.transition(proposal.id, any(), BuddyProposalStatus.CONFIRMING, any()) } returns 1
        action.performAnswer = { error("database went away") }

        assertThrows<IllegalStateException> { service().confirm(authId, proposal.id) }

        assertThat(recorded.single().first).isEqualTo(BuddyProposalStatus.FAILED)
    }

    @Test
    fun `dismissing an open proposal declines it without running anything`() {
        val proposal = stored()
        every {
            repository.transition(proposal.id, BuddyProposalStatus.PROPOSED, BuddyProposalStatus.DISMISSED, now)
        } returns 1

        val response = service().dismiss(authId, proposal.id)

        assertThat(response.ok).isTrue()
        assertThat(action.performed).isEmpty()
    }

    @Test
    fun `dismissing a proposal that already moved says so`() {
        val proposal = stored(status = BuddyProposalStatus.CONFIRMED)
        every { repository.transition(proposal.id, any(), BuddyProposalStatus.DISMISSED, any()) } returns 0

        val response = service().dismiss(authId, proposal.id)

        assertThat(response.ok).isFalse()
        assertThat(response.message).contains("already done")
    }

    @Test
    fun `dismissing somebody else's proposal is a 404`() {
        val proposal = stored(owner = UUID.randomUUID())

        assertThrows<ResponseStatusException> { service().dismiss(authId, proposal.id) }
            .also { assertThat(it.statusCode).isEqualTo(HttpStatus.NOT_FOUND) }
    }

    /**
     * By the time the outcome is recorded, the change may already be committed. A database refusing to
     * record it must not turn that into an error: the manager would be told a change failed that happened.
     */
    @Test
    fun `a failure to record the outcome still returns what actually happened`() {
        val proposal = stored()
        every { repository.transition(proposal.id, any(), BuddyProposalStatus.CONFIRMING, any()) } returns 1
        every { repository.finish(any(), any(), any(), any(), any()) } throws
            DataAccessResourceFailureException("connection lost")

        val response = service().confirm(authId, proposal.id)

        assertThat(response.ok).isTrue()
        assertThat(response.message).isEqualTo("Dismissed the question.")
        assertThat(action.performed).hasSize(1)
    }

    /** Recording is one conditional update, so it cannot lose an optimistic-lock race to a stale entity. */
    @Test
    fun `the outcome is recorded with a conditional update, not by saving the loaded proposal`() {
        val proposal = stored()
        every { repository.transition(proposal.id, any(), BuddyProposalStatus.CONFIRMING, any()) } returns 1

        service().confirm(authId, proposal.id)

        verify {
            repository.finish(
                proposal.id,
                BuddyProposalStatus.CONFIRMING,
                BuddyProposalStatus.CONFIRMED,
                now,
                "Dismissed the question.",
            )
        }
        verify(exactly = 0) { repository.save(any()) }
    }
}
