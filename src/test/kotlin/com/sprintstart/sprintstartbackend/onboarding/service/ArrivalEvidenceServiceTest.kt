package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStepState
import com.sprintstart.sprintstartbackend.onboarding.repository.ArrivalStepStateRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginVerification
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.Instant
import java.util.UUID

/**
 * What the system is willing to conclude from what it can see.
 *
 * The load-bearing half is what it *refuses* to conclude: no check here writes a negative, and a
 * refusal to answer is never treated as an answer.
 */
class ArrivalEvidenceServiceTest {
    private val arrivalStepService: ArrivalStepService = mockk()
    private val arrivalStepStateRepository: ArrivalStepStateRepository = mockk()
    private val contributionService: ContributionService = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val githubClient: GithubClient = mockk()
    private val userApi: UserApi = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

    private val hireId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private lateinit var service: ArrivalEvidenceService

    @BeforeEach
    fun setUp() {
        // TransactionTemplate runs the callback directly against a mocked manager.
        every { transactionManager.getTransaction(any()) } returns
            mockk<DefaultTransactionStatus>(relaxed = true) as TransactionStatus

        service = ArrivalEvidenceService(
            arrivalStepService,
            arrivalStepStateRepository,
            contributionService,
            projectMembershipApi,
            githubClient,
            userApi,
            transactionManager,
        )

        every { arrivalStepStateRepository.findAllByUserId(hireId) } returns emptyList()
        every { arrivalStepStateRepository.save(any()) } answers { firstArg() }
        every { arrivalStepService.projectsFor(hireId) } returns listOf(projectId)
        every { userApi.recordGithubLoginVerification(any(), any()) } returns Unit
    }

    @Test
    fun `a confirmed GitHub account settles the step as observed`() = runTest {
        steps(derived("github-account"))
        every { userApi.getGithubLoginByUserId(hireId) } returns "ada"
        coEvery { githubClient.userExists("ada") } returns true
        val saved = slot<ArrivalStepState>()
        every { arrivalStepStateRepository.save(capture(saved)) } answers { saved.captured }

        service.refresh(hireId)

        assertEquals(Rigor.OBSERVED, saved.captured.rigor)
        assertEquals("github-account", saved.captured.stepKey)
        verify { userApi.recordGithubLoginVerification(hireId, GithubLoginVerification.VERIFIED) }
    }

    @Test
    fun `a login GitHub does not recognise is recorded, and settles nothing`() = runTest {
        steps(derived("github-account"))
        every { userApi.getGithubLoginByUserId(hireId) } returns "nosuchuser"
        coEvery { githubClient.userExists("nosuchuser") } returns false

        service.refresh(hireId)

        // The verdict is worth storing -- it is what lets the app say "we could not find that
        // account" instead of silently failing to credit their work months later.
        verify { userApi.recordGithubLoginVerification(hireId, GithubLoginVerification.NOT_FOUND) }
        verify(exactly = 0) { arrivalStepStateRepository.save(any()) }
    }

    /**
     * The rule the whole service exists to hold: an outage is not evidence about the world.
     */
    @Test
    fun `GitHub refusing to answer records nothing at all`() = runTest {
        steps(derived("github-account"))
        every { userApi.getGithubLoginByUserId(hireId) } returns "ada"
        coEvery { githubClient.userExists("ada") } returns null

        service.refresh(hireId)

        // Not NOT_FOUND. Telling somebody their perfectly good username does not exist is worse
        // than telling them nothing.
        verify(exactly = 0) { userApi.recordGithubLoginVerification(any(), any()) }
        verify(exactly = 0) { arrivalStepStateRepository.save(any()) }
    }

    @Test
    fun `no declared login means nothing to check`() = runTest {
        steps(derived("github-account"))
        every { userApi.getGithubLoginByUserId(hireId) } returns null

        service.refresh(hireId)

        verify(exactly = 0) { userApi.recordGithubLoginVerification(any(), any()) }
        verify(exactly = 0) { arrivalStepStateRepository.save(any()) }
    }

    @Test
    fun `work anywhere settles the environment step`() = runTest {
        steps(derived("environment-ready"))
        val member = member()
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member)
        every { contributionService.forHire(member, projectId) } returns listOf(contribution())
        val saved = slot<ArrivalStepState>()
        every { arrivalStepStateRepository.save(capture(saved)) } answers { saved.captured }

        service.refresh(hireId)

        assertEquals(Rigor.OBSERVED, saved.captured.rigor)
    }

    @Test
    fun `no work yet says nothing about whether the environment runs`() = runTest {
        steps(derived("environment-ready"))
        val member = member()
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member)
        every { contributionService.forHire(member, projectId) } returns emptyList()

        service.refresh(hireId)

        // Absence of a contribution is not evidence of a broken machine -- which is exactly why
        // this step stays self-confirmable.
        verify(exactly = 0) { arrivalStepStateRepository.save(any()) }
    }

    @Test
    fun `a step the hire already confirmed is not settled again`() = runTest {
        steps(
            ResolvedArrivalStep(
                step = derivedStep("github-account"),
                settledAt = Instant.parse("2026-08-01T09:00:00Z"),
                rigor = Rigor.DECLARED,
            ),
        )

        service.refresh(hireId)

        // Already settled, so not even looked at: their word arriving first is not something a
        // later observation of the same fact should overwrite.
        verify(exactly = 0) { arrivalStepStateRepository.save(any()) }
    }

    @Test
    fun `a step with no derivation behind it is left entirely alone`() = runTest {
        steps(
            ResolvedArrivalStep(
                step = ArrivalStep(key = "badge", title = "Collect your badge"),
                settledAt = null,
                rigor = null,
            ),
        )

        service.refresh(hireId)

        assertTrue(true)
        verify(exactly = 0) { arrivalStepStateRepository.save(any()) }
    }

    private fun steps(vararg resolved: ResolvedArrivalStep) {
        every { arrivalStepService.forHire(hireId) } returns resolved.toList()
    }

    private fun derived(key: String) =
        ResolvedArrivalStep(step = derivedStep(key), settledAt = null, rigor = null)

    private fun derivedStep(key: String) =
        ArrivalStep(key = key, title = key, settledBy = Rigor.OBSERVED, selfConfirmable = false)

    private fun member() =
        ProjectMember(
            userId = hireId,
            displayName = "Sam Hire",
            githubLogin = "ada",
            joinedAt = Instant.parse("2026-07-01T09:00:00Z"),
        )

    private fun contribution() =
        Contribution(
            kind = ContributionEvidenceKind.PULL_REQUEST,
            state = ContributionState.ACCEPTED,
            rigor = Rigor.OBSERVED,
            openedAt = Instant.parse("2026-07-10T09:00:00Z"),
            firstResponseAt = null,
            acceptedAt = Instant.parse("2026-07-11T09:00:00Z"),
            returnedCount = 0,
            evidenceRef = UUID.randomUUID(),
        )
}
