package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class BuddyAppGuideToolsTest {
    private val userId = UUID.randomUUID()
    private val managedProject = ProjectDto(projectId = UUID.randomUUID(), name = "Apollo", description = null)
    private val otherProject = ProjectDto(projectId = UUID.randomUUID(), name = "Gemini", description = null)

    private val userApi: UserApi = mockk {
        every { getAuthIdByUserId(userId) } returns Optional.of("auth|me")
        every { getUsersByIds(listOf(userId)) } returns listOf(
            UserDto(
                id = userId,
                username = "me",
                firstname = "Sam",
                lastname = "Lee",
                avatarUrl = null,
                profileIcon = null,
                projects = setOf(managedProject, otherProject),
                projectRoles = emptyList(),
            ),
        )
        every { canManageProject("auth|me", any()) } returns false
    }

    private val tools = BuddyAppGuideTools(userApi)

    private fun guideAs(role: Role?, currentPage: String? = null): String {
        every { userApi.getPermissionGroup(userId) } returns role
        return tools.guideFor(userId, currentPage)
    }

    /** The part of the guide that lists pages the caller may be sent to. */
    private fun openable(guide: String): String =
        guide.substringAfter("Pages they can open").substringBefore("Only somebody else")

    /** The part that lists what somebody else has to do. */
    private fun elsewhere(guide: String): String =
        guide.substringAfter("Only somebody else", missingDelimiterValue = "")

    @Test
    fun `offers a hire their own pages and never a manager page`() {
        val guide = guideAs(Role.USER)

        assertThat(openable(guide)).contains("Board (/board)", "OnBoarding (/onboarding)")
        assertThat(openable(guide)).doesNotContain("/pm-dashboard", "/team-management", "/admin")
    }

    /** The question this tool exists for: a PM asking how to create a project. */
    @Test
    fun `tells a PM that only an admin creates a project`() {
        every { userApi.canManageProject("auth|me", managedProject.projectId) } returns true

        val guide = guideAs(Role.PM)

        assertThat(openable(guide)).doesNotContain("/admin", "New Project")
        assertThat(elsewhere(guide)).contains("create a project: an admin, on Access Management")
    }

    @Test
    fun `shows a managing PM where roles are set up and chosen`() {
        every { userApi.canManageProject("auth|me", managedProject.projectId) } returns true

        val guide = guideAs(Role.PM)

        assertThat(guide).contains("They manage: Apollo.")
        assertThat(guide).doesNotContain("Gemini")
        assertThat(openable(guide))
            .contains("Team Management (/team-management) — PM Dashboard")
            .contains("to create a project role: \"Role Management\" tab")
            .contains("to choose or change a member's role")
    }

    /** The frontend hides every manager page from a PM who manages nothing; so must the guide. */
    @Test
    fun `keeps manager pages from a PM who manages no project, and says why`() {
        val guide = guideAs(Role.PM)

        assertThat(openable(guide)).doesNotContain("/pm-dashboard", "/team-management")
        assertThat(guide).contains("They manage no project yet.")
        assertThat(guide).contains("manage no project, so the manager pages")
    }

    /** HR opens Access Management but may not press "New Project" — the backend is admin-only. */
    @Test
    fun `sends HR to an admin for what only an admin may do on a page HR can open`() {
        val guide = guideAs(Role.HR)

        assertThat(openable(guide)).contains("Access Management (/admin)").doesNotContain("New Project")
        assertThat(elsewhere(guide)).contains("create a project: an admin")
    }

    @Test
    fun `gives an admin everything and nothing to ask anybody else`() {
        val guide = guideAs(Role.ADMIN)

        assertThat(openable(guide)).contains("to create a project: \"Projects\" tab → \"New Project\"")
        assertThat(elsewhere(guide)).isEmpty()
    }

    @Test
    fun `reads an unknown user as a plain member`() {
        val guide = guideAs(null)

        assertThat(guide).contains("Who is asking: a team member.")
        assertThat(openable(guide)).doesNotContain("/pm-dashboard")
    }

    @Test
    fun `names the page the caller is on, ignoring the query`() {
        val guide = guideAs(Role.ADMIN, currentPage = "/team/42?tab=path#top")

        assertThat(guide).contains("They are looking at: Team member (/team/{member_id}).")
    }

    @Test
    fun `admits a page it does not describe`() {
        assertThat(guideAs(Role.USER, currentPage = "/skill-wizard"))
            .contains("They are on a page this guide does not describe.")
    }

    /** The value is the client's word; anything that is not an app path is dropped, never echoed. */
    @Test
    fun `drops a current page that is not an app path`() {
        val guide = guideAs(Role.USER, currentPage = "javascript:alert(1)")

        assertThat(guide).doesNotContain("javascript", "They are looking at", "They are on")
    }

    @Test
    fun `says nothing about the current page when none was sent`() {
        assertThat(guideAs(Role.USER)).doesNotContain("They are looking at", "They are on")
    }
}
