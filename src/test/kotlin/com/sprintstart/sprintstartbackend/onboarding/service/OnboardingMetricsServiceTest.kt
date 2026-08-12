package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserGoal
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.onboarding.service.evidence.PullRequestEvidenceProvider
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The through-line of these tests: **"has not happened yet" must never be reported as a number.**
 *
 * A dashboard that renders an unreached milestone as zero shows a team succeeding at onboarding
 * they have not done, which is worse than showing nothing at all.
 */
class OnboardingMetricsServiceTest {
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val userGoalRepository: UserGoalRepository = mockk()

    // Task-0 assignment is exercised in TaskZeroServiceTest; here it defaults to "none assigned".
    private val taskZeroService: TaskZeroService = mockk(relaxed = true)

    // Autonomy is exercised in RampServiceTest; here it defaults to "not reached". This read must
    // never write -- a PM opening the dashboard cannot be what grants somebody autonomy.
    private val rampService: RampService = mockk(relaxed = true)

    // Engineering unless a test says otherwise: these assert numbers, and the track only decides
    // the words around them -- except in the stall tests, where it decides whether a hire whose
    // work nothing observes can be seen at all.
    private val trackService: TrackService = mockk()

    private val now: Instant = Instant.parse("2026-07-20T12:00:00Z")
    private val projectId: UUID = UUID.randomUUID()

    private val engineering = OnboardingTrack(
        key = OnboardingTrack.DEFAULT_KEY,
        label = "Engineering",
        contributionNoun = "change",
        contributionNounPlural = "changes",
        contributionVerbPast = "merged",
        evidenceKinds = mutableSetOf(ContributionEvidenceKind.PULL_REQUEST),
    )

    private val scrumMaster = OnboardingTrack(
        key = "scrum-master",
        label = "Scrum Master",
        contributionNoun = "ceremony",
        contributionNounPlural = "ceremonies",
        contributionVerbPast = "facilitated",
        evidenceKinds = mutableSetOf(ContributionEvidenceKind.ATTESTATION),
    )

    private val service = OnboardingMetricsService(
        projectMembershipApi,
        // A real ContributionService over the same mocked ingestion API, not a mock: these
        // tests assert the numbers this service reports, and the point of the refactor is
        // that swapping pull requests for contributions did not move any of them.
        ContributionService(listOf(PullRequestEvidenceProvider(artifactIngestionApi))),
        userGoalRepository,
        taskZeroService,
        rampService,
        trackService,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @BeforeEach
    fun setUp() {
        every { trackService.forMember(any()) } returns engineering
    }

    private fun daysAgo(days: Long): Instant = now.minus(Duration.ofDays(days))

    private fun member(
        login: String? = "hire",
        joinedDaysAgo: Long? = 10,
        userId: UUID = UUID.randomUUID(),
    ) = ProjectMember(
        userId = userId,
        displayName = "A Hire",
        githubLogin = login,
        joinedAt = joinedDaysAgo?.let { daysAgo(it) },
    )

    private fun pullRequest(
        openedDaysAgo: Long? = 5,
        respondedDaysAgo: Long? = null,
        mergedDaysAgo: Long? = null,
        state: String = if (mergedDaysAgo != null) "MERGED" else "OPEN",
    ) = AuthoredPullRequest(
        artifactId = UUID.randomUUID(),
        openedAt = openedDaysAgo?.let { daysAgo(it) },
        firstResponseAt = respondedDaysAgo?.let { daysAgo(it) },
        mergedAt = mergedDaysAgo?.let { daysAgo(it) },
        state = state,
    )

    private fun stage(members: List<ProjectMember>, pullRequests: List<AuthoredPullRequest>) {
        every { projectMembershipApi.getProjectMembers(projectId) } returns members
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, any()) } returns pullRequests
        every { userGoalRepository.findByUserIdAndProjectId(any(), projectId) } returns null
    }

    @Nested
    inner class Timeline {
        @Test
        fun `measures joined to first merged pull request`() {
            stage(listOf(member(joinedDaysAgo = 10)), listOf(pullRequest(openedDaysAgo = 5, mergedDaysAgo = 3)))

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertEquals(7 * 24, hire.hoursToFirstAcceptedContribution?.toInt())
            assertEquals(1, hire.acceptedContributionCount)
        }

        @Test
        fun `reports an unreached milestone as null, never as zero`() {
            stage(listOf(member()), listOf(pullRequest(openedDaysAgo = 2)))

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertNull(hire.hoursToFirstAcceptedContribution)
            assertNull(hire.firstContributionAcceptedAt)
            assertNull(hire.hoursToFirstResponse)
        }

        @Test
        fun `measures the wait for a first response on the first pull request`() {
            stage(
                listOf(member()),
                listOf(pullRequest(openedDaysAgo = 6, respondedDaysAgo = 4, mergedDaysAgo = 3)),
            )

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertEquals(2 * 24, hire.hoursToFirstResponse?.toInt())
        }

        @Test
        fun `an unanswered pull request keeps growing rather than stopping at some cap`() {
            stage(listOf(member()), listOf(pullRequest(openedDaysAgo = 21)))

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertEquals(21 * 24, hire.longestOpenWaitHours?.toInt())
        }

        @Test
        fun `a member with no join date has no elapsed time, not an instant one`() {
            stage(listOf(member(joinedDaysAgo = null)), listOf(pullRequest(mergedDaysAgo = 1)))

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertNull(hire.joinedAt)
            assertNull(hire.hoursToFirstAcceptedContribution)
            // The merge itself is still real and still reported.
            assertEquals(1, hire.acceptedContributionCount)
        }
    }

    @Nested
    inner class Attribution {
        @Test
        fun `a member whose work nothing could observe is unattributable, not stalled`() {
            // A missing GitHub username is an optional setup item, not a stall — onboarding is not
            // blocked on it. It is still surfaced separately via unattributableMemberCount so a PM
            // can see whose work cannot be measured, without calling the hire stuck.
            //
            // This holds only while the hire's track has no other way to be seen; the test below
            // covers a track that does.
            every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member(login = null))
            every { userGoalRepository.findByUserIdAndProjectId(any(), projectId) } returns null

            val metrics = service.getProjectMetrics(projectId)

            assertEquals(1, metrics.unattributableMemberCount)
            assertFalse(metrics.hires.single().stalled)
            assertNull(metrics.hires.single().stalledReason)
        }

        @Test
        fun `a hire on an attested track is judged with no GitHub login at all`() {
            // The invisible-hire bug in one test. Attested evidence is attributed by identity, not
            // by a git handle, so an empty contribution list is a real answer for this hire rather
            // than a blind spot -- and silence has to reach the PM. Before, they read as calm.
            every { trackService.forMember(any()) } returns scrumMaster
            stage(listOf(member(login = null, joinedDaysAgo = 20)), emptyList())

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertTrue(hire.stalled)
            assertTrue(hire.stalledReason!!.contains("No ceremony started"))
        }
    }

    @Nested
    inner class Stalls {
        @Test
        fun `a pull request past the response window is the stall, and names the wait`() {
            stage(listOf(member()), listOf(pullRequest(openedDaysAgo = 4)))

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertTrue(hire.stalled)
            assertTrue(hire.stalledReason!!.contains("4 days"))
        }

        @Test
        fun `the stall is worded in the hire's own track, never in pull requests`() {
            // A Scrum Master told their pull request is late learns nothing except that the tool
            // was not built for them.
            every { trackService.forMember(any()) } returns scrumMaster
            stage(listOf(member()), listOf(pullRequest(openedDaysAgo = 4)))

            val reason = service
                .getProjectMetrics(projectId)
                .hires
                .single()
                .stalledReason

            assertTrue(reason!!.contains("A ceremony has been waiting"))
        }

        @Test
        fun `the timeline carries the words to say its numbers in`() {
            every { trackService.forMember(any()) } returns scrumMaster
            stage(listOf(member()), emptyList())

            val vocabulary = service
                .getProjectMetrics(projectId)
                .hires
                .single()
                .vocabulary

            assertEquals("ceremonies", vocabulary.contributionNounPlural)
            assertEquals("facilitated", vocabulary.contributionVerbPast)
        }

        @Test
        fun `a pull request closed without merging is neither open nor a stall`() {
            // Closed-unmerged PRs have a null mergedAt, so a merge-state-only check would miscount
            // them as open and even report a long "waiting for review" stall on work that is done.
            stage(listOf(member()), listOf(pullRequest(openedDaysAgo = 63, state = "CLOSED")))

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertEquals(0, hire.openContributionCount)
            assertFalse(hire.stalled)
            assertNull(hire.longestOpenWaitHours)
        }

        @Test
        fun `a fresh pull request is not a stall`() {
            stage(listOf(member()), listOf(pullRequest(openedDaysAgo = 1)))

            assertFalse(
                service
                    .getProjectMetrics(projectId)
                    .hires
                    .single()
                    .stalled,
            )
        }

        @Test
        fun `silence since joining becomes a stall once it has gone on long enough`() {
            stage(listOf(member(joinedDaysAgo = 20)), emptyList())

            val hire = service.getProjectMetrics(projectId).hires.single()

            assertTrue(hire.stalled)
            assertTrue(hire.stalledReason!!.contains("No change started"))
        }

        @Test
        fun `a new joiner with nothing open yet is not a stall`() {
            stage(listOf(member(joinedDaysAgo = 2)), emptyList())

            assertFalse(
                service
                    .getProjectMetrics(projectId)
                    .hires
                    .single()
                    .stalled,
            )
        }

        @Test
        fun `somebody who has merged something is moving, whatever else is open`() {
            stage(
                listOf(member(joinedDaysAgo = 40)),
                listOf(pullRequest(openedDaysAgo = 30, respondedDaysAgo = 29, mergedDaysAgo = 28)),
            )

            assertFalse(
                service
                    .getProjectMetrics(projectId)
                    .hires
                    .single()
                    .stalled,
            )
        }
    }

    @Nested
    inner class Aggregates {
        @Test
        fun `uses a median so one slow hire cannot define the cohort`() {
            val fast = member(login = "fast")
            val middle = member(login = "middle")
            val slow = member(login = "slow")
            every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(fast, middle, slow)
            every { userGoalRepository.findByUserIdAndProjectId(any(), projectId) } returns null
            every { artifactIngestionApi.getAuthoredPullRequests(projectId, "fast") } returns
                listOf(pullRequest(openedDaysAgo = 9, respondedDaysAgo = 9, mergedDaysAgo = 9))
            every { artifactIngestionApi.getAuthoredPullRequests(projectId, "middle") } returns
                listOf(pullRequest(openedDaysAgo = 8, respondedDaysAgo = 8, mergedDaysAgo = 8))
            every { artifactIngestionApi.getAuthoredPullRequests(projectId, "slow") } returns
                listOf(pullRequest(openedDaysAgo = 1, respondedDaysAgo = 1, mergedDaysAgo = 1))

            val metrics = service.getProjectMetrics(projectId)

            // Joined 10 days ago; merges at 1, 2 and 9 days in. The mean would be 4 days.
            assertEquals(2 * 24, metrics.medianHoursToFirstAcceptedContribution?.toInt())
            assertEquals(3, metrics.hiresWithAcceptedContribution)
        }

        @Test
        fun `an empty project reports nothing rather than zero`() {
            every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

            val metrics = service.getProjectMetrics(projectId)

            assertEquals(0, metrics.memberCount)
            assertNull(metrics.medianHoursToFirstAcceptedContribution)
            assertNull(metrics.p90HoursToFirstResponse)
        }
    }

    @Nested
    inner class HireLookup {
        @Test
        fun `returns null for somebody who is not a member of the project`() {
            every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

            assertNull(service.getHireTimeline(UUID.randomUUID(), projectId))
        }

        @Test
        fun `carries a claimed task through as the assignment moment`() {
            val hire = member()
            val claimedAt = daysAgo(6)
            every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(hire)
            every { artifactIngestionApi.getAuthoredPullRequests(projectId, "hire") } returns emptyList()
            every { userGoalRepository.findByUserIdAndProjectId(hire.userId, projectId) } returns
                UserGoal(
                    userId = hire.userId,
                    projectId = projectId,
                    sourceProposalId = UUID.randomUUID(),
                    claimedAt = claimedAt,
                )

            assertEquals(claimedAt, service.getHireTimeline(hire.userId, projectId)?.firstTaskClaimedAt)
        }
    }
}
