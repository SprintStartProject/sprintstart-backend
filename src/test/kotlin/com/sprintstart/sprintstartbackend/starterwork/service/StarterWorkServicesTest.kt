package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.CandidatePoolState
import com.sprintstart.sprintstartbackend.starterwork.external.enums.GoalClaimant
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProficiencyLevel
import com.sprintstart.sprintstartbackend.starterwork.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.starterwork.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkCandidateResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The starter-work facades only unwrap ids and hand each call to the service that owns it. */
class StarterWorkServicesTest {
    private val currentTaskReader: CurrentTaskReader = mockk()
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService = mockk()
    private val myCompetencyService: MyCompetencyService = mockk()
    private val userGoalService: UserGoalService = mockk()
    private val taskOrientationService: TaskOrientationService = mockk()
    private val competencyPlacementService: CompetencyPlacementService = mockk()
    private val starterWorkScope: StarterWorkScope = mockk()
    private val starterWorkPoolReconciler: StarterWorkPoolReconciler = mockk()

    private val reads = HireStarterWorkService(
        currentTaskReader,
        starterWorkTaskProposalService,
        myCompetencyService,
        userGoalService,
        taskOrientationService,
        competencyPlacementService,
    )
    private val actions = HireStarterWorkActionsService(userGoalService, competencyPlacementService)
    private val review =
        StarterWorkReviewService(starterWorkTaskProposalService, starterWorkScope, starterWorkPoolReconciler)
    private val authoring = OrientationAuthoringService(taskOrientationService)

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()

    @Test
    fun `hire reads answer with what the owning service answers`() {
        val claimedAt = Instant.parse("2026-10-01T09:00:00Z")
        every { currentTaskReader.currentTaskFor(userId, projectId) } returns null
        every { starterWorkTaskProposalService.matchForUserId(userId, projectId) } returns listOf(mockk())
        every { myCompetencyService.getCompetenciesForUser(userId) } returns listOf(mockk())
        every { userGoalService.claimedAt(UserId(userId), ProjectId(projectId)) } returns claimedAt
        coEvery { taskOrientationService.getForHire(userId, projectId) } returns mockk()
        every { competencyPlacementService.topicsFor(userId) } returns listOf(mockk())
        every { competencyPlacementService.labelFor("kotlin") } returns "Kotlin"

        assertEquals(null, reads.currentTaskOf(UserId(userId), ProjectId(projectId)))
        assertSame(
            starterWorkTaskProposalService.matchForUserId(userId, projectId),
            reads.rankedTasksFor(UserId(userId), ProjectId(projectId)),
        )
        assertSame(myCompetencyService.getCompetenciesForUser(userId), reads.competenciesOf(UserId(userId)))
        assertEquals(claimedAt, reads.goalClaimedAt(UserId(userId), ProjectId(projectId)))
        runBlocking {
            assertSame(
                taskOrientationService.getForHire(userId, projectId),
                reads.orientationFor(UserId(userId), ProjectId(projectId)),
            )
        }
        assertSame(competencyPlacementService.topicsFor(userId), reads.placementTopicsFor(UserId(userId)))
        assertEquals("Kotlin", reads.competencyLabel("kotlin"))
    }

    @Test
    fun `hire actions run through the owning service`() {
        every { userGoalService.claimForMe("auth|hire", projectId, taskId, GoalClaimant.BUDDY) } returns mockk()
        every { competencyPlacementService.record(userId, "kotlin", ProficiencyLevel.entries.first()) } returns mockk()

        assertSame(
            userGoalService.claimForMe("auth|hire", projectId, taskId, GoalClaimant.BUDDY),
            actions.claimGoal(AuthId("auth|hire"), ProjectId(projectId), StarterWorkTaskId(taskId), GoalClaimant.BUDDY),
        )
        assertSame(
            competencyPlacementService.record(userId, "kotlin", ProficiencyLevel.entries.first()),
            actions.recordPlacement(UserId(userId), "kotlin", ProficiencyLevel.entries.first()),
        )
    }

    @Test
    fun `promotable issues are the available candidates on the project's repositories`() {
        val available = candidate("github:acme/shop:ISSUE:1", CandidatePoolState.AVAILABLE)
        val pooled = candidate("github:acme/shop:ISSUE:2", CandidatePoolState.IN_POOL)
        every { starterWorkTaskProposalService.listCandidates(projectId) } returns listOf(available, pooled)
        every {
            starterWorkScope.onProject(listOf(available), projectId, any<(StarterWorkCandidateResponse) -> String>())
        } returns listOf(available)

        assertEquals(listOf(available), review.promotableIssuesOn(ProjectId(projectId)))
    }

    @Test
    fun `review decisions run through the owning service`() {
        val request = PromoteStarterWorkCandidateRequest(sourceId = "github:acme/shop:ISSUE:1")
        every { starterWorkTaskProposalService.markReviewed(taskId) } returns mockk()
        every { starterWorkTaskProposalService.promoteCandidate(request) } returns mockk()
        every { starterWorkTaskProposalService.reject(taskId, "duplicate") } returns mockk()
        every { starterWorkTaskProposalService.setTaskZeroEligibility(taskId, true) } returns mockk()
        every { starterWorkPoolReconciler.reconcile() } returns mockk()

        assertSame(starterWorkTaskProposalService.markReviewed(taskId), review.markReviewed(StarterWorkTaskId(taskId)))
        assertSame(starterWorkTaskProposalService.promoteCandidate(request), review.promoteCandidate(request))
        assertSame(
            starterWorkTaskProposalService.reject(taskId, "duplicate"),
            review.reject(StarterWorkTaskId(taskId), "duplicate"),
        )
        assertSame(
            starterWorkTaskProposalService.setTaskZeroEligibility(taskId, true),
            review.setTaskZeroEligibility(StarterWorkTaskId(taskId), true),
        )
        assertSame(starterWorkPoolReconciler.reconcile(), review.reconcile())
    }

    @Test
    fun `orientation authoring runs through the orientation service`() {
        val request: AuthorOrientationRequest = mockk()
        every { taskOrientationService.getForAuthoring(taskId, projectId) } returns mockk()
        every { taskOrientationService.authorPacket(taskId, projectId, request) } returns mockk()
        every { taskOrientationService.revertToAi(taskId, projectId) } just runs

        assertSame(
            taskOrientationService.getForAuthoring(taskId, projectId),
            authoring.packetForAuthoring(StarterWorkTaskId(taskId), ProjectId(projectId)),
        )
        assertSame(
            taskOrientationService.authorPacket(taskId, projectId, request),
            authoring.authorPacket(StarterWorkTaskId(taskId), ProjectId(projectId), request),
        )
        authoring.revertToAi(StarterWorkTaskId(taskId), ProjectId(projectId))
        verify { taskOrientationService.revertToAi(taskId, projectId) }
    }

    private fun candidate(sourceId: String, state: CandidatePoolState): StarterWorkCandidateResponse = mockk {
        every { this@mockk.sourceId } returns sourceId
        every { poolState } returns state
    }
}
