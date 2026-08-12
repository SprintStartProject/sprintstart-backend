package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingTrackRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The through-line of these tests: **resolution is total, and it fails toward "as it was before".**
 *
 * Tracks were added to a system where everybody was implicitly an engineer. Every way of not having
 * an answer — no track on the role, a key pointing at nothing, two roles disagreeing, no seeded row
 * at all — has to land on the default, because any of them throwing would mean this feature broke
 * onboarding for somebody who never asked for it.
 */
class TrackServiceTest {
    private val onboardingTrackRepository: OnboardingTrackRepository = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val userApi: UserApi = mockk()

    private val service = TrackService(onboardingTrackRepository, projectMembershipApi, userApi)

    private val userId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private val delivery = OnboardingTrack(
        key = "delivery",
        label = "Agile delivery",
        contributionNoun = "ceremony",
        contributionNounPlural = "ceremonies",
        contributionVerbPast = "facilitated",
    )

    private fun member(trackKey: String?) =
        ProjectMember(userId, "A Hire", "hire", null, onboardingTrackKey = trackKey)

    private fun noSeededTracks() {
        every { onboardingTrackRepository.findByKey(any()) } returns null
    }

    private fun onProjects(vararg projects: ProjectDto) {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(
            UserDto(
                id = userId,
                username = "hire",
                firstname = "A",
                lastname = "Hire",
                avatarUrl = null,
                profileIcon = null,
                projects = projects.toSet(),
                projectRoles = emptyList(),
            ),
        )
    }

    @Test
    fun `a role declaring no track resolves to the default`() {
        noSeededTracks()

        assertEquals(OnboardingTrack.DEFAULT_KEY, service.forMember(member(null)).key)
    }

    @Test
    fun `a role declaring a track resolves to it`() {
        every { onboardingTrackRepository.findByKey("delivery") } returns delivery

        assertEquals("delivery", service.forMember(member("delivery")).key)
    }

    @Test
    fun `a track key pointing at nothing resolves to the default rather than failing`() {
        noSeededTracks()

        // A track somebody deleted must not take the hire's onboarding down with it.
        assertEquals(OnboardingTrack.DEFAULT_KEY, service.forMember(member("deleted-track")).key)
    }

    @Test
    fun `the default track exists even when nothing is seeded`() {
        noSeededTracks()

        // Schema-from-entities contexts never run the seeding migration, so the in-memory twin is
        // load-bearing rather than defensive.
        val default = service.default()

        assertEquals(OnboardingTrack.DEFAULT_KEY, default.key)
        assertTrue(default.admits(ContributionEvidenceKind.PULL_REQUEST))
        assertEquals("merged", default.contributionVerbPast)
    }

    @Test
    fun `a hire on no project yet is judged by the default track`() {
        noSeededTracks()
        onProjects()

        assertTrue(service.admitsAnywhere(userId, ContributionEvidenceKind.PULL_REQUEST))
    }

    @Test
    fun `a hire whose only track admits nothing does not admit that evidence anywhere`() {
        noSeededTracks()
        every { onboardingTrackRepository.findByKey("delivery") } returns delivery
        onProjects(ProjectDto(projectId, "Delivery", null))
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member("delivery"))

        assertFalse(service.admitsAnywhere(userId, ContributionEvidenceKind.PULL_REQUEST))
    }

    @Test
    fun `a hire's vocabulary is their track when every project agrees`() {
        noSeededTracks()
        every { onboardingTrackRepository.findByKey("delivery") } returns delivery
        onProjects(ProjectDto(projectId, "Delivery", null))
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member("delivery"))

        assertEquals("ceremony", service.forUser(userId).contributionNoun)
    }

    @Test
    fun `a hire on two different tracks falls back to the default vocabulary`() {
        noSeededTracks()
        every { onboardingTrackRepository.findByKey("delivery") } returns delivery
        val codeProjectId = UUID.randomUUID()
        onProjects(ProjectDto(projectId, "Delivery", null), ProjectDto(codeProjectId, "Checkout", null))
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member("delivery"))
        every { projectMembershipApi.getProjectMembers(codeProjectId) } returns listOf(member(null))

        // Strict where `admitsAnywhere` is permissive, and deliberately so: a capability can be
        // offered on the chance it is useful, but a sentence can only be written in one set of
        // words, and the wrong ones are worse than the neutral ones.
        assertEquals(OnboardingTrack.DEFAULT_KEY, service.forUser(userId).key)
    }

    @Test
    fun `a hire on no project yet gets the default vocabulary`() {
        noSeededTracks()
        onProjects()

        assertEquals(OnboardingTrack.DEFAULT_KEY, service.forUser(userId).key)
    }

    @Test
    fun `a hire on two tracks admits evidence either of them admits`() {
        noSeededTracks()
        every { onboardingTrackRepository.findByKey("delivery") } returns delivery
        val codeProjectId = UUID.randomUUID()
        onProjects(ProjectDto(projectId, "Delivery", null), ProjectDto(codeProjectId, "Checkout", null))
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member("delivery"))
        every { projectMembershipApi.getProjectMembers(codeProjectId) } returns listOf(member(null))

        // A delivery lead on one project who also ships code on another keeps their pull-request
        // tooling: a track is a bundle of defaults, not a cage.
        assertTrue(service.admitsAnywhere(userId, ContributionEvidenceKind.PULL_REQUEST))
    }
}
