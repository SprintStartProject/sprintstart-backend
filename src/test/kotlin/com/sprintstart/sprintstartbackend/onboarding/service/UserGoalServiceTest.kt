package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserGoal
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for claiming a starter task as a goal.
 *
 * A goal points straight at the proposal, so there is no derived key to resolve through and no
 * second table to disagree with.
 */
class UserGoalServiceTest {
    private val userGoalRepository: UserGoalRepository = mockk(relaxed = true)
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = UserGoalService(userGoalRepository, starterWorkTaskProposalRepository, userApi)

    private val userId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()
    private val authId = "auth-1"

    private fun approvedProposal() = StarterWorkTaskProposal(
        sourceId = "github:acme/repo:ISSUE:42",
        title = "Fix the login redirect",
        summary = "A small, well-scoped bug",
        sourceUrl = "https://github.com/acme/repo/issues/42",
    ).apply { status = ProposalStatus.LIVE }

    private fun stageUser() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        // A relaxed mock returns a bare Object from the generic save(S): S, which the checkcast
        // Kotlin inserts at the call site rejects -- echo the argument back instead.
        every { userGoalRepository.save(any()) } answers { firstArg() }
    }

    @Nested
    inner class ClaimForMe {
        @Test
        fun `describes the goal in the task's own words`() {
            stageUser()
            val proposal = approvedProposal()
            every { starterWorkTaskProposalRepository.findById(proposal.id) } returns Optional.of(proposal)
            every { userGoalRepository.findByUserIdAndProjectId(userId, projectId) } returns null

            val result = service.claimForMe(authId, projectId, proposal.id)

            // The wording of the work, not of a synthetic skill node derived from it.
            assertEquals(proposal.id, result.proposalId)
            assertEquals("Fix the login redirect", result.title)
            assertEquals("https://github.com/acme/repo/issues/42", result.sourceUrl)
        }

        @Test
        fun `persists the goal against the user and project`() {
            stageUser()
            val proposal = approvedProposal()
            every { starterWorkTaskProposalRepository.findById(proposal.id) } returns Optional.of(proposal)
            every { userGoalRepository.findByUserIdAndProjectId(userId, projectId) } returns null
            val saved = slot<UserGoal>()
            every { userGoalRepository.save(capture(saved)) } answers { saved.captured }

            service.claimForMe(authId, projectId, proposal.id)

            assertEquals(userId, saved.captured.userId)
            assertEquals(projectId, saved.captured.projectId)
            assertEquals(proposal.id, saved.captured.sourceProposalId)
        }

        @Test
        fun `replaces an existing goal rather than adding a second one`() {
            stageUser()
            val proposal = approvedProposal()
            val existing = UserGoal(
                userId = userId,
                projectId = projectId,
                sourceProposalId = UUID.randomUUID(),
            )
            every { starterWorkTaskProposalRepository.findById(proposal.id) } returns Optional.of(proposal)
            every { userGoalRepository.findByUserIdAndProjectId(userId, projectId) } returns existing
            val saved = slot<UserGoal>()
            every { userGoalRepository.save(capture(saved)) } answers { saved.captured }

            service.claimForMe(authId, projectId, proposal.id)

            assertEquals(existing.id, saved.captured.id)
            assertEquals(proposal.id, saved.captured.sourceProposalId)
        }

        @Test
        fun `409s for a task the PM has not approved`() {
            stageUser()
            val proposal = approvedProposal().apply { status = ProposalStatus.REJECTED }
            every { starterWorkTaskProposalRepository.findById(proposal.id) } returns Optional.of(proposal)

            val exception = assertThrows<ResponseStatusException> {
                service.claimForMe(authId, projectId, proposal.id)
            }

            // A PM curates which tasks exist; a hire may pick from that set, not extend it.
            assertEquals(HttpStatus.CONFLICT, exception.statusCode)
            verify(exactly = 0) { userGoalRepository.save(any()) }
        }
    }

    @Nested
    inner class FindForUser {
        @Test
        fun `returns the goal in the task's words`() {
            val proposal = approvedProposal()
            every { userGoalRepository.findByUserIdAndProjectId(userId, projectId) } returns
                UserGoal(userId = userId, projectId = projectId, sourceProposalId = proposal.id)
            every { starterWorkTaskProposalRepository.findById(proposal.id) } returns Optional.of(proposal)

            assertEquals("Fix the login redirect", service.findForUser(userId, projectId)?.title)
        }

        @Test
        fun `reads as no goal once the task is gone`() {
            val missingId = UUID.randomUUID()
            every { userGoalRepository.findByUserIdAndProjectId(userId, projectId) } returns
                UserGoal(userId = userId, projectId = projectId, sourceProposalId = missingId)
            every { starterWorkTaskProposalRepository.findById(missingId) } returns Optional.empty()

            // A stale goal degrades to "no goal" rather than describing work that no longer exists.
            assertNull(service.findForUser(userId, projectId))
        }
    }
}
