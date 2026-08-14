package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskZeroAssignment
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskZeroAssignmentRepository
import com.sprintstart.sprintstartbackend.onboarding.service.evidence.PullRequestEvidenceProvider
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskZeroServiceTest {
    private val proposalRepository: StarterWorkTaskProposalRepository = mockk(relaxed = true)
    private val assignmentRepository: TaskZeroAssignmentRepository = mockk(relaxed = true)
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()

    private val now: Instant = Instant.parse("2026-07-20T12:00:00Z")
    private val hireId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private val service = TaskZeroService(
        proposalRepository,
        assignmentRepository,
        projectMembershipApi,
        // A real ContributionService over the same mocked ingestion API, not a mock: these
        // tests assert the numbers this service reports, and the point of the refactor is
        // that swapping pull requests for contributions did not move any of them.
        ContributionService(listOf(PullRequestEvidenceProvider(artifactIngestionApi))),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun member(login: String? = "hire") =
        ProjectMember(hireId, "A Hire", login, now.minus(Duration.ofDays(5)))

    private fun isMember(login: String? = "hire") {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member(login))
    }

    private fun noAuthoredPrs() {
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "hire") } returns emptyList()
    }

    private fun task(eligible: Boolean = true, createdDaysAgo: Long = 1) =
        StarterWorkTaskProposal(
            sourceId = "github:org/repo:ISSUE:${UUID.randomUUID()}",
            title = "Fix a typo",
            status = ProposalStatus.LIVE,
            taskZeroEligible = eligible,
            createdAt = now.minus(Duration.ofDays(createdDaysAgo)),
        )

    @Test
    fun `setEligibility flags an approved task`() {
        val proposal = task(eligible = false)
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)
        every { proposalRepository.save(any()) } answers { firstArg() }

        val result = service.setEligibility(proposal.id, true)

        assertTrue(result.taskZeroEligible)
    }

    @Test
    fun `setEligibility 409s on a task that is not approved`() {
        val proposal = StarterWorkTaskProposal(sourceId = "s", title = "t", status = ProposalStatus.REJECTED)
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)

        assertThrows<ResponseStatusException> { service.setEligibility(proposal.id, true) }
    }

    @Test
    fun `setEligibility 404s on an unknown task`() {
        val id = UUID.randomUUID()
        every { proposalRepository.findById(id) } returns Optional.empty()

        assertThrows<ResponseStatusException> { service.setEligibility(id, true) }
    }

    @Test
    fun `getForHire auto-assigns the earliest eligible task on first read`() {
        isMember()
        noAuthoredPrs()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        every { assignmentRepository.findAllAssignedProposalIds() } returns emptyList()
        val older = task(createdDaysAgo = 5)
        val newer = task(createdDaysAgo = 1)
        every { proposalRepository.findAllByStatusAndTaskZeroEligibleTrue(ProposalStatus.LIVE) } returns
            listOf(newer, older)
        val saved = slot<TaskZeroAssignment>()
        every { assignmentRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.getForHire(hireId, projectId)

        assertFalse(result.noneAvailable)
        // The oldest eligible task is handed out first.
        assertEquals(older.id, saved.captured.proposalId)
    }

    @Test
    fun `getForHire is a handled state, not an error, when nothing is eligible`() {
        isMember()
        noAuthoredPrs()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        every { assignmentRepository.findAllAssignedProposalIds() } returns emptyList()
        every { proposalRepository.findAllByStatusAndTaskZeroEligibleTrue(ProposalStatus.LIVE) } returns emptyList()

        val result = service.getForHire(hireId, projectId)

        assertTrue(result.noneAvailable)
        assertNull(result.task)
        verify(exactly = 0) { assignmentRepository.save(any()) }
    }

    @Test
    fun `getForHire never assigns the same task to two hires`() {
        isMember()
        noAuthoredPrs()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        val taken = task(createdDaysAgo = 5)
        val free = task(createdDaysAgo = 1)
        every { assignmentRepository.findAllAssignedProposalIds() } returns listOf(taken.id)
        every { proposalRepository.findAllByStatusAndTaskZeroEligibleTrue(ProposalStatus.LIVE) } returns
            listOf(taken, free)
        val saved = slot<TaskZeroAssignment>()
        every { assignmentRepository.save(capture(saved)) } answers { firstArg() }

        service.getForHire(hireId, projectId)

        assertEquals(free.id, saved.captured.proposalId)
    }

    @Test
    fun `getForHire returns the existing assignment without assigning again`() {
        isMember()
        noAuthoredPrs()
        val proposal = task()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns
            TaskZeroAssignment(hireId = hireId, projectId = projectId, proposalId = proposal.id, assignedAt = now)
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)

        val result = service.getForHire(hireId, projectId)

        assertEquals(now, result.assignedAt)
        verify(exactly = 0) { assignmentRepository.save(any()) }
    }

    @Test
    fun `getForHire reports the loop proven once the hire has merged a pull request`() {
        isMember()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        every { assignmentRepository.findAllAssignedProposalIds() } returns emptyList()
        every { proposalRepository.findAllByStatusAndTaskZeroEligibleTrue(ProposalStatus.LIVE) } returns emptyList()
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "hire") } returns
            listOf(
                AuthoredPullRequest(
                    UUID.randomUUID(),
                    now.minus(Duration.ofDays(2)),
                    null,
                    now.minus(Duration.ofDays(1)),
                    "MERGED",
                ),
            )

        val result = service.getForHire(hireId, projectId)

        assertTrue(result.loopProven)
    }

    @Test
    fun `getForHire 404s when the hire is not a member`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertThrows<ResponseStatusException> { service.getForHire(hireId, projectId) }
    }

    @Test
    fun `unassign frees the task`() {
        service.unassign(hireId, projectId)

        verify(exactly = 1) { assignmentRepository.deleteByHireIdAndProjectId(hireId, projectId) }
    }
}
