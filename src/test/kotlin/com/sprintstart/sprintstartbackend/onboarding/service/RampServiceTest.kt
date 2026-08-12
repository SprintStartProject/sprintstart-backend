package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.RampStage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.AutonomyMilestone
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskZeroAssignment
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserGoal
import com.sprintstart.sprintstartbackend.onboarding.repository.AutonomyMilestoneRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskZeroAssignmentRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.onboarding.service.evidence.PullRequestEvidenceProvider
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RampServiceTest {
    private val taskZeroAssignmentRepository: TaskZeroAssignmentRepository = mockk(relaxed = true)
    private val userGoalRepository: UserGoalRepository = mockk()
    private val proposalRepository: StarterWorkTaskProposalRepository = mockk(relaxed = true)
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk(relaxed = true)
    private val competencyRepository: CompetencyRepository = mockk()
    private val autonomyMilestoneRepository: AutonomyMilestoneRepository = mockk(relaxed = true)
    private val knowledgeRequestRepository: KnowledgeRequestRepository = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()

    private val now: Instant = Instant.parse("2026-07-21T12:00:00Z")
    private val hireId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private val service = RampService(
        taskZeroAssignmentRepository,
        userGoalRepository,
        proposalRepository,
        userCompetencyStateRepository,
        competencyRepository,
        autonomyMilestoneRepository,
        knowledgeRequestRepository,
        projectMembershipApi,
        // A real ContributionService over the same mocked ingestion API, not a mock: these
        // tests assert the numbers this service reports, and the point of the refactor is
        // that swapping pull requests for contributions did not move any of them.
        ContributionService(listOf(PullRequestEvidenceProvider(artifactIngestionApi))),
        // The default engineering track, so every copy assertion below is the copy an engineer
        // reads today -- the vocabulary swap must not have moved it.
        mockk<TrackService> { every { forMember(any()) } returns engineeringTrack() },
        // The real reader over the same mocked repositories: "which task is this person on" is one
        // answer shared with the board, and these tests are what pin it.
        CurrentTaskReader(userGoalRepository, taskZeroAssignmentRepository, proposalRepository),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @BeforeEach
    fun `no proposals unless a test says so`() {
        // A relaxed mock would hand back a relaxed Optional, whose orElse(null) is an Object.
        every { proposalRepository.findById(any()) } returns Optional.empty()
    }

    private fun daysAgo(days: Long): Instant = now.minus(Duration.ofDays(days))

    private fun engineeringTrack() = OnboardingTrack(
        key = OnboardingTrack.DEFAULT_KEY,
        label = "Engineering",
        contributionNoun = "change",
        contributionNounPlural = "changes",
        contributionVerbPast = "merged",
        evidenceKinds = mutableSetOf(ContributionEvidenceKind.PULL_REQUEST),
    )

    private fun isMember(login: String? = "hire") {
        every { projectMembershipApi.getProjectMembers(projectId) } returns
            listOf(ProjectMember(hireId, "A Hire", login, daysAgo(20)))
    }

    private fun pullRequest(
        opened: Instant = daysAgo(5),
        merged: Instant? = daysAgo(3),
        changesRequested: Int = 0,
    ) = AuthoredPullRequest(
        artifactId = UUID.randomUUID(),
        openedAt = opened,
        firstResponseAt = opened.plus(Duration.ofHours(2)),
        mergedAt = merged,
        state = if (merged != null) "MERGED" else "OPEN",
        changesRequestedCount = changesRequested,
    )

    private fun pullRequests(vararg prs: AuthoredPullRequest) {
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "hire") } returns prs.toList()
    }

    private fun noGoal() {
        every { userGoalRepository.findByUserIdAndProjectId(hireId, projectId) } returns null
    }

    private fun noEscalations() {
        every { knowledgeRequestRepository.findAllByHireIdAndProjectId(hireId, projectId) } returns
            emptyList()
    }

    private fun noMilestone() {
        every { autonomyMilestoneRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        every { autonomyMilestoneRepository.save(any()) } answers { firstArg() }
    }

    private fun claimedGoal(vararg keys: String, claimedAt: Instant = daysAgo(6)): StarterWorkTaskProposal {
        val proposal = StarterWorkTaskProposal(
            sourceId = "github:acme/api:ISSUE:1",
            title = "A real change",
            competencyKeys = keys.toMutableList(),
            status = ProposalStatus.LIVE,
        )
        every { userGoalRepository.findByUserIdAndProjectId(hireId, projectId) } returns
            UserGoal(
                userId = hireId,
                projectId = projectId,
                sourceProposalId = proposal.id,
                claimedAt = claimedAt,
            )
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)
        every { competencyRepository.findAllByKeyIn(keys.toList()) } returns
            keys.map { Competency(key = it, label = it, kind = CompetencyKind.SKILL, targetLevel = 2) }
        return proposal
    }

    // --- credit -----------------------------------------------------------------------------

    @Test
    fun `a merged pull request credits the claimed task's competencies as VERIFIED`() {
        isMember()
        pullRequests(pullRequest())
        claimedGoal("kotlin")
        noEscalations()
        noMilestone()
        every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(hireId, "kotlin") } returns null
        val saved = slot<UserCompetencyState>()
        every { userCompetencyStateRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.getForHire(hireId, projectId)

        assertEquals(listOf("kotlin"), result.creditedCompetencyKeys)
        assertEquals(CompetencySource.VERIFIED, saved.captured.source)
        // Credit lands at the bar the project set for that competency.
        assertEquals(2, saved.captured.level)
    }

    @Test
    fun `Task 0 credits nothing`() {
        isMember()
        pullRequests(pullRequest())
        // Task 0 is an assignment, not a claimed goal -- so there is no path that could credit it.
        noGoal()
        every { taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns
            TaskZeroAssignment(hireId = hireId, projectId = projectId, proposalId = UUID.randomUUID(), assignedAt = now)
        noEscalations()
        noMilestone()

        val result = service.getForHire(hireId, projectId)

        assertEquals(emptyList(), result.creditedCompetencyKeys)
        verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
    }

    @Test
    fun `credit never lowers a level already earned`() {
        isMember()
        pullRequests(pullRequest())
        claimedGoal("kotlin")
        noEscalations()
        noMilestone()
        val existing = UserCompetencyState(
            userId = hireId,
            competencyKey = "kotlin",
            level = 4,
            source = CompetencySource.VERIFIED,
        )
        every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(hireId, "kotlin") } returns existing

        service.getForHire(hireId, projectId)

        assertEquals(4, existing.level)
    }

    @Test
    fun `a pull request merged before the task was claimed does not credit it`() {
        isMember()
        pullRequests(pullRequest(opened = daysAgo(12), merged = daysAgo(10)))
        claimedGoal("kotlin", claimedAt = daysAgo(6))
        noEscalations()
        noMilestone()

        val result = service.getForHire(hireId, projectId)

        assertEquals(emptyList(), result.creditedCompetencyKeys)
    }

    // --- stage ------------------------------------------------------------------------------

    @Test
    fun `stage is counted in merged changes, and an open pull request has proven nothing`() {
        isMember()
        pullRequests(pullRequest(merged = null))
        noGoal()
        every { taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        noEscalations()
        noMilestone()

        val result = service.getForHire(hireId, projectId)

        assertEquals(RampStage.TASK_ZERO, result.stage)
        assertEquals(0, result.mergedCount)
    }

    @Test
    fun `one merge is task one, two is task two plus`() {
        isMember()
        noGoal()
        every { taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        noEscalations()

        every { autonomyMilestoneRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        every { autonomyMilestoneRepository.save(any()) } answers { firstArg() }
        pullRequests(pullRequest(changesRequested = 1))
        assertEquals(RampStage.TASK_ONE, service.getForHire(hireId, projectId).stage)

        pullRequests(
            pullRequest(opened = daysAgo(9), merged = daysAgo(8), changesRequested = 1),
            pullRequest(changesRequested = 1),
        )
        assertEquals(RampStage.TASK_TWO_PLUS, service.getForHire(hireId, projectId).stage)
    }

    // --- autonomy ---------------------------------------------------------------------------

    @Test
    fun `autonomy is not granted when the last change needed rework`() {
        isMember()
        pullRequests(pullRequest(changesRequested = 2))
        noGoal()
        noEscalations()
        noMilestone()

        val result = service.getForHire(hireId, projectId)

        assertFalse(result.autonomy.reached)
        assertTrue(result.autonomy.blockers.any { it.contains("sent back") })
        verify(exactly = 0) { autonomyMilestoneRepository.save(any()) }
    }

    @Test
    fun `autonomy is not granted when a person was pulled in during that change`() {
        isMember()
        pullRequests(pullRequest(opened = daysAgo(5), merged = daysAgo(3)))
        noGoal()
        every { knowledgeRequestRepository.findAllByHireIdAndProjectId(hireId, projectId) } returns
            listOf(
                KnowledgeRequest(
                    projectId = projectId,
                    hireId = hireId,
                    question = "How do we run this locally?",
                    createdAt = daysAgo(4),
                ),
            )
        noMilestone()

        val result = service.getForHire(hireId, projectId)

        assertFalse(result.autonomy.reached)
        assertTrue(result.autonomy.blockers.any { it.contains("pulled in a person") })
    }

    @Test
    fun `help given before the change started does not block autonomy`() {
        isMember()
        pullRequests(pullRequest(opened = daysAgo(5), merged = daysAgo(3)))
        noGoal()
        // Week one needed help; this change did not. That is exactly what the exit asks about.
        every { knowledgeRequestRepository.findAllByHireIdAndProjectId(hireId, projectId) } returns
            listOf(
                KnowledgeRequest(
                    projectId = projectId,
                    hireId = hireId,
                    question = "Where does the config live?",
                    createdAt = daysAgo(15),
                ),
            )
        noMilestone()

        assertTrue(service.getForHire(hireId, projectId).autonomy.reached)
    }

    @Test
    fun `reaching autonomy records the merge time, not the moment it was noticed`() {
        isMember()
        val mergedAt = daysAgo(3)
        pullRequests(pullRequest(merged = mergedAt))
        noGoal()
        noEscalations()
        val saved = slot<AutonomyMilestone>()
        every { autonomyMilestoneRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        every { autonomyMilestoneRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.getForHire(hireId, projectId)

        assertTrue(result.autonomy.reached)
        assertEquals(mergedAt, saved.captured.reachedAt)
        assertEquals(RampStage.AUTONOMOUS, result.stage)
        assertNotNull(result.autonomy.provenByArtifactId)
    }

    @Test
    fun `autonomy once reached is never re-evaluated`() {
        isMember()
        // Later work needed rework -- which is ordinary, and does not un-happen the milestone.
        pullRequests(pullRequest(changesRequested = 3))
        noGoal()
        every { autonomyMilestoneRepository.findByHireIdAndProjectId(hireId, projectId) } returns
            AutonomyMilestone(hireId = hireId, projectId = projectId, reachedAt = daysAgo(10))

        val result = service.getForHire(hireId, projectId)

        assertTrue(result.autonomy.reached)
        assertEquals(daysAgo(10), result.autonomy.reachedAt)
        assertEquals(emptyList(), result.autonomy.blockers)
    }

    @Test
    fun `no merged change yet is a blocker stated plainly, not a silent false`() {
        isMember()
        pullRequests()
        noGoal()
        every { taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        noEscalations()
        noMilestone()

        val result = service.getForHire(hireId, projectId)

        assertFalse(result.autonomy.reached)
        assertNull(result.autonomy.reachedAt)
        assertEquals(listOf("No merged change here yet"), result.autonomy.blockers)
    }

    @Test
    fun `a hire with no declared GitHub login is not reported as having shipped nothing wrongly`() {
        isMember(login = null)
        noGoal()
        every { taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null
        noEscalations()
        noMilestone()

        val result = service.getForHire(hireId, projectId)

        assertEquals(0, result.mergedCount)
        assertFalse(result.autonomy.reached)
    }

    @Test
    fun `404s when the hire is not a member`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertThrows<ResponseStatusException> { service.getForHire(hireId, projectId) }
    }
}
