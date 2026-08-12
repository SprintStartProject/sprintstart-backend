package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.CompetencyGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.setup.RungState
import com.sprintstart.sprintstartbackend.onboarding.model.response.setup.SetupReadinessResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class SetupReadinessServiceTest {
    private val graphAuthoring: CompetencyGraphAuthoringService = mockk()
    private val starterWork: StarterWorkTaskProposalRepository = mockk()

    // An empty project by default: the tracks rung reads the member list, and cases that are not
    // about tracks should not have to describe a team.
    private val membership: ProjectMembershipApi = mockk {
        every { getProjectMembers(any()) } returns emptyList()
    }

    // No track beyond the default is in use by default, so an uncovered-track warning cannot fire
    // in the existing cases; coverage has its own tests below.
    private val tracks: TrackService = mockk {
        every { tracksInUse() } returns emptyList()
        every { default() } returns ENGINEERING
    }

    private val projectId: UUID = UUID.randomUUID()

    private val service = SetupReadinessService(
        graphAuthoring,
        starterWork,
        membership,
        tracks,
    )

    private fun rungOf(response: SetupReadinessResponse, key: String) =
        response.rungs.single { it.key == key }

    /** Only sizes/fields the service reads matter, so entity instances are relaxed mocks. */
    private fun approvedCompetencies(n: Int) {
        every { graphAuthoring.getGraph() } returns
            CompetencyGraphResponse(
                competencies = List(n) { mockk() },
            )
    }

    // Real rows rather than bare mocks: the rung now reads each task's track, and a mock would
    // answer that with whatever mockk invents rather than with the coverage the test describes.
    private fun task(trackKey: String? = null, reviewed: Boolean = true) = StarterWorkTaskProposal(
        sourceId = "src-${UUID.randomUUID()}",
        title = "A starter task",
        onboardingTrackKey = trackKey,
        reviewed = reviewed,
    )

    /**
     * @param live Tasks in the claimable pool.
     * @param unreviewed How many of them nobody has looked at. They are *part of* [live] now, not a
     * separate queue waiting to be admitted.
     */
    private fun starterTasks(live: Int, unreviewed: Int = 0, trackKey: String? = null) {
        every { starterWork.findAllByStatus(ProposalStatus.LIVE) } returns
            List(live - unreviewed) { task(trackKey) } + List(unreviewed) { task(trackKey, reviewed = false) }
    }

    /**
     * The rung reports what exists rather than what somebody owes.
     *
     * It used to warn that N competencies were "waiting for your review". There is no review queue
     * now: generation writes live rows and a human corrects them, so an empty vocabulary means
     * nothing has been generated yet -- which points at the corpus, not at a person's inbox.
     */
    @Test
    fun `an empty vocabulary points at the corpus, not at a review queue`() {
        approvedCompetencies(0)
        starterTasks(live = 0)
        every { membership.getProjectMembers(projectId) } returns emptyList()

        val response = service.getReadiness(projectId)

        val skillMap = rungOf(response, "skill-map")
        assertThat(skillMap.state).isEqualTo(RungState.WARN)
        assertThat(skillMap.detail).contains("No competencies yet")
        assertThat(skillMap.detail).doesNotContain("waiting for your review")
        assertThat(response.ready).isFalse()
    }

    @Test
    fun `a fully set up project reads ready`() {
        approvedCompetencies(6)
        starterTasks(live = 2)

        val response = service.getReadiness(projectId)

        assertThat(response.rungs.map { it.state }).containsOnly(RungState.OK)
        assertThat(response.ready).isTrue()
        // Two rungs have gone, each when the work behind it stopped existing: the human-loop rung
        // with the buddy loop, and the baseline rung when the path became goal-directed and nothing
        // read the selection any more. `tracks` arrived with role tracks.
        assertThat(response.rungs.map { it.key })
            .containsExactly("skill-map", "starter-tasks", "tracks")
    }

    @Test
    fun `starter work covering no track a role is on is a warning, not readiness`() {
        approvedCompetencies(4)
        // Every approved task is engineering work; somebody is onboarding on delivery.
        starterTasks(live = 3, trackKey = "engineering")
        every { tracks.tracksInUse() } returns listOf(
            OnboardingTrack(
                key = "delivery",
                label = "Agile delivery",
                contributionNoun = "ceremony",
                contributionNounPlural = "ceremonies",
                contributionVerbPast = "facilitated",
            ),
        )

        val rung = rungOf(service.getReadiness(projectId), "starter-tasks")

        // The failure this catches: plenty of tasks, none a delivery lead could pick up, on a
        // project the ladder would otherwise call ready.
        assertThat(rung.state).isEqualTo(RungState.WARN)
        assertThat(rung.detail).contains("Agile delivery")
    }

    @Test
    fun `an unscoped task counts as coverage for every track`() {
        approvedCompetencies(4)
        // Null means "suits any role", so it is coverage for everybody rather than for nobody.
        starterTasks(live = 2, trackKey = null)
        every { tracks.tracksInUse() } returns listOf(
            OnboardingTrack(
                key = "delivery",
                label = "Agile delivery",
                contributionNoun = "ceremony",
                contributionNounPlural = "ceremonies",
                contributionVerbPast = "facilitated",
            ),
        )

        assertThat(rungOf(service.getReadiness(projectId), "starter-tasks").state).isEqualTo(RungState.OK)
    }

    private fun member(trackKey: String?) = ProjectMember(
        userId = UUID.randomUUID(),
        displayName = "A colleague",
        githubLogin = null,
        joinedAt = null,
        onboardingTrackKey = trackKey,
    )

    private fun team(vararg trackKeys: String?) {
        every { membership.getProjectMembers(projectId) } returns trackKeys.map { member(it) }
    }

    /**
     * The gap this rung exists for: track resolution falls back rather than blocking, which is
     * right, but until now nothing said it had happened. A role left undeclared next to roles that
     * are declared is somebody being onboarded in the wrong words with no signal anywhere.
     */
    @Test
    fun `roles left without a track while others have one is a warning`() {
        approvedCompetencies(4)
        starterTasks(live = 2)
        team("delivery", null, null)

        val response = service.getReadiness(projectId)
        val rung = rungOf(response, "tracks")

        assertThat(rung.state).isEqualTo(RungState.WARN)
        assertThat(rung.detail).contains("2 of 3 people", "Engineering")
        // The count is the positive quantity, per the rung contract -- never the pending one.
        assertThat(rung.count).isEqualTo(1)
        assertThat(response.ready).isFalse()
    }

    @Test
    fun `every role declaring a track is the cleared state`() {
        approvedCompetencies(4)
        starterTasks(live = 2)
        team("delivery", "engineering")

        val rung = rungOf(service.getReadiness(projectId), "tracks")

        assertThat(rung.state).isEqualTo(RungState.OK)
        assertThat(rung.count).isEqualTo(2)
    }

    /**
     * A project that never adopted tracks is indistinguishable from a team that really is all
     * engineers, so there is nothing to warn about -- but the wording everyone gets is said out
     * loud, which is what a PM with a designer needs in order to notice.
     */
    @Test
    fun `no role declaring a track states the default wording instead of warning`() {
        approvedCompetencies(4)
        starterTasks(live = 2)
        team(null, null)

        val response = service.getReadiness(projectId)
        val rung = rungOf(response, "tracks")

        assertThat(rung.state).isEqualTo(RungState.OK)
        assertThat(rung.detail).contains("Engineering")
        // Warning here would fire on every project predating tracks and could not be cleared by
        // changing anything real, which is how a ladder stops being read.
        assertThat(response.ready).isTrue()
    }

    @Test
    fun `a project with nobody on it has no track gap to report`() {
        approvedCompetencies(4)
        starterTasks(live = 2)

        assertThat(rungOf(service.getReadiness(projectId), "tracks").state).isEqualTo(RungState.OK)
    }

    private companion object {
        val ENGINEERING = OnboardingTrack(
            key = OnboardingTrack.DEFAULT_KEY,
            label = "Engineering",
            contributionNoun = "change",
            contributionNounPlural = "changes",
            contributionVerbPast = "merged",
        )
    }
}
