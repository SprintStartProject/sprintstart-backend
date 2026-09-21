package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.user.external.DirectoryMatch
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.response.user.ProjectRoleSummary
import com.sprintstart.sprintstartbackend.user.service.ProjectRoleService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class TeamMemberToolsTest {
    private val projectRoleService: ProjectRoleService = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val userApi: UserApi = mockk()
    private val tools = TeamMemberTools(projectRoleService, projectMembershipApi, userApi)

    private val projectId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private fun call(name: String, vararg args: Pair<String, String>) =
        BuddyToolCallDto(
            id = "c1",
            name = name,
            arguments = buildJsonObject { args.forEach { (k, v) -> put(k, v) } },
        )

    private fun member(id: UUID = memberId, name: String = "Sam Rivera") =
        ProjectMember(userId = id, displayName = name, githubLogin = null, joinedAt = null)

    @Test
    fun `is the team area and handles exactly its three reads`() {
        assertThat(tools.area).isEqualTo(TeamArea.TEAM)
        assertThat(tools.toolSpecs().map { it.name }).containsExactly(
            TeamMemberTools.LIST_PROJECT_ROLES,
            TeamMemberTools.GET_MEMBER_ROLES,
            TeamMemberTools.FIND_USER_TO_ADD,
        )
        assertThat(tools.handles("add_members")).isFalse()
    }

    @Test
    fun `lists the role catalogue with the ids to act on, and says it is not editable here`() {
        every { projectRoleService.getAllRoles() } returns listOf(
            ProjectRole(name = "Reviewer", description = "Reviews work"),
            ProjectRole(name = "Backend developer", description = "Writes the backend"),
        )

        val result = tools.execute(call(TeamMemberTools.LIST_PROJECT_ROLES), context)

        assertThat(result).contains("Backend developer [role_id:")
        assertThat(result).contains("Reviewer [role_id:")
        assertThat(result).contains("administrator's job")
        // Sorted by name, so the same conversation twice does not present a different order.
        assertThat(result.indexOf("Backend developer")).isLessThan(result.indexOf("Reviewer"))
    }

    @Test
    fun `reads one member's roles on this project`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member())
        every { projectRoleService.getRolesForUserOnProject(memberId, projectId) } returns
            listOf(ProjectRoleSummary(id = UUID.randomUUID(), name = "Reviewer"))

        val result = tools.execute(call(TeamMemberTools.GET_MEMBER_ROLES, "member_id" to "$memberId"), context)

        assertThat(result).contains("Sam Rivera holds these roles")
        assertThat(result).contains("Reviewer [role_id:")
    }

    @Test
    fun `a member with no role here is not confused with somebody who is not here`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member())
        every { projectRoleService.getRolesForUserOnProject(memberId, projectId) } returns emptyList()

        val result = tools.execute(call(TeamMemberTools.GET_MEMBER_ROLES, "member_id" to "$memberId"), context)

        assertThat(result).contains("on this project but holds no role here")
    }

    @Test
    fun `somebody on another project cannot have their roles read here`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        val result = tools.execute(
            call(TeamMemberTools.GET_MEMBER_ROLES, "member_id" to "${UUID.randomUUID()}"),
            context,
        )

        assertThat(result).contains("not on this project")
        verify(exactly = 0) { projectRoleService.getRolesForUserOnProject(any(), any()) }
    }

    @Test
    fun `a 404 from the role service is answered, not thrown`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member())
        every { projectRoleService.getRolesForUserOnProject(memberId, projectId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "gone")

        val result = tools.execute(call(TeamMemberTools.GET_MEMBER_ROLES, "member_id" to "$memberId"), context)

        assertThat(result).contains("not on this project")
    }

    @Test
    fun `finds one person to add by an exact identifier`() {
        val found = UUID.randomUUID()
        every { userApi.findByExactEmailOrGithubLogin("sam@example.test") } returns
            Optional.of(DirectoryMatch(userId = found, displayName = "Sam Rivera"))
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        val result = tools.execute(
            call(TeamMemberTools.FIND_USER_TO_ADD, "email_or_github_login" to "sam@example.test"),
            context,
        )

        assertThat(result).contains("Sam Rivera [user_id: $found]")
        assertThat(result).contains("not on this project yet")
    }

    @Test
    fun `somebody already on the project is not offered as an addition`() {
        every { userApi.findByExactEmailOrGithubLogin(any()) } returns
            Optional.of(DirectoryMatch(userId = memberId, displayName = "Sam Rivera"))
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member())

        val result = tools.execute(
            call(TeamMemberTools.FIND_USER_TO_ADD, "email_or_github_login" to "sam@example.test"),
            context,
        )

        assertThat(result).contains("already on this project")
        assertThat(result).doesNotContain("Offer add_members")
    }

    @Test
    fun `no match is said plainly, and the tool never falls back to searching`() {
        every { userApi.findByExactEmailOrGithubLogin("sam") } returns Optional.empty()

        val result = tools.execute(call(TeamMemberTools.FIND_USER_TO_ADD, "email_or_github_login" to "sam"), context)

        assertThat(result).contains("Nobody has exactly that email address or GitHub login")
        verify(exactly = 0) { userApi.searchUsers(any(), any(), any(), any()) }
    }

    @Test
    fun `a blank identifier is refused rather than treated as everybody`() {
        val result = tools.execute(call(TeamMemberTools.FIND_USER_TO_ADD, "email_or_github_login" to "  "), context)

        assertThat(result).contains("cannot list or search")
        verify(exactly = 0) { userApi.findByExactEmailOrGithubLogin(any()) }
    }

    @Test
    fun `the catalogue description forbids offering role creation or deletion`() {
        assertThat(TeamMemberTools.LIST_PROJECT_ROLES_SPEC.description).contains("never be offered here")
    }

    @Test
    fun `an unknown tool name is answered, not thrown`() {
        assertThat(tools.execute(call("nope"), context)).isEqualTo("Unknown tool: nope.")
    }
}
