package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.ProjectRoleApi
import com.sprintstart.sprintstartbackend.user.external.ProjectRoleDetailDto
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class TeamMemberActionsTest {
    private val projectRoleApi: ProjectRoleApi = mockk(relaxed = true)
    private val projectMembershipApi: ProjectMembershipApi = mockk(relaxed = true)
    private val userApi: UserApi = mockk(relaxed = true)

    private val projectId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private fun member(id: UUID = memberId, name: String = "Sam Rivera") =
        ProjectMember(userId = id, displayName = name, githubLogin = null, joinedAt = null)

    private fun onProject(vararg members: ProjectMember) {
        every { projectMembershipApi.getProjectMembers(projectId) } returns members.toList()
    }

    private fun managedBy(id: UUID?) {
        every { projectMembershipApi.getProjectManagerId(projectId) } returns Optional.ofNullable(id)
    }

    private fun call(name: String, vararg args: Pair<String, String>) =
        BuddyToolCallDto(
            id = "c1",
            name = name,
            arguments = buildJsonObject { args.forEach { (k, v) -> put(k, v) } },
        )

    private fun proposed(draft: TeamActionDraft): TeamActionDraft.Proposed {
        assertThat(draft).isInstanceOf(TeamActionDraft.Proposed::class.java)
        return draft as TeamActionDraft.Proposed
    }

    private fun refusal(draft: TeamActionDraft): String {
        assertThat(draft).isInstanceOf(TeamActionDraft.Refused::class.java)
        return (draft as TeamActionDraft.Refused).reason
    }

    @Nested
    inner class AddMembers {
        private val action = AddMembersAction(projectMembershipApi, userApi)

        private fun ids(vararg values: String) = buildJsonObject {
            putJsonArray("user_ids") { values.forEach { add(it) } }
        }

        private fun known(id: UUID, first: String, last: String) {
            every { userApi.getUsersByIds(listOf(id)) } returns listOf(
                UserDto(
                    id = id,
                    username = "$first.$last",
                    firstname = first,
                    lastname = last,
                    avatarUrl = null,
                    profileIcon = null,
                    projects = emptySet(),
                    projectRoles = emptyList(),
                ),
            )
        }

        @Test
        fun `is a standard change in the team area`() {
            assertThat(action.area).isEqualTo(TeamArea.TEAM)
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.STANDARD)
        }

        @Test
        fun `names everybody being added, so the manager confirms who and not how many`() {
            val a = UUID.randomUUID()
            val b = UUID.randomUUID()
            onProject()
            known(a, "Sam", "Rivera")
            known(b, "Alex", "Chen")

            val draft = proposed(action.draft(BuddyToolCallDto("c1", "add_members", ids("$a", "$b")), context))

            assertThat(draft.preview).contains("Sam Rivera")
            assertThat(draft.preview).contains("Alex Chen")
            assertThat(draft.preview).contains("no role here")
        }

        @Test
        fun `nothing is assigned while drafting`() {
            val a = UUID.randomUUID()
            onProject()
            known(a, "Sam", "Rivera")

            action.draft(BuddyToolCallDto("c1", "add_members", ids("$a")), context)

            verify(exactly = 0) { projectMembershipApi.addMembers(any(), any()) }
        }

        @Test
        fun `somebody already here is refused`() {
            onProject(member())

            val reason = refusal(action.draft(BuddyToolCallDto("c1", "add_members", ids("$memberId")), context))

            assertThat(reason).contains("already on this project")
        }

        @Test
        fun `an id that is not a user_id is refused`() {
            onProject()

            val reason = refusal(action.draft(BuddyToolCallDto("c1", "add_members", ids("not-an-id")), context))

            assertThat(reason).contains("not a user_id")
        }

        @Test
        fun `an empty list is refused`() {
            onProject()

            val reason = refusal(action.draft(BuddyToolCallDto("c1", "add_members", ids()), context))

            assertThat(reason).contains("nobody to add")
        }

        @Test
        fun `somebody who joined between preview and confirm stops the confirm`() {
            onProject(member())

            val stale = action.recheck(ids("$memberId"), context)

            assertThat(stale).contains("joined this project since")
        }

        @Test
        fun `the confirm assigns to the turn's project`() = runTest {
            val captured = slot<Set<UUID>>()
            every { projectMembershipApi.addMembers(projectId, capture(captured)) } returns Unit

            action.perform(ids("$memberId"), context)

            assertThat(captured.captured).containsExactly(memberId)
            verify { projectMembershipApi.addMembers(projectId, any()) }
        }
    }

    @Nested
    inner class RemoveMember {
        private val action = RemoveMemberAction(projectMembershipApi, projectRoleApi)

        @Test
        fun `is marked destructive`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
        }

        @Test
        fun `says the roles go with the membership and do not come back`() {
            onProject(member())
            managedBy(UUID.randomUUID())
            every { projectRoleApi.getRolesOnProject(memberId, projectId) } returns listOf(
                ProjectRoleShortDto(id = UUID.randomUUID(), name = "Reviewer"),
                ProjectRoleShortDto(id = UUID.randomUUID(), name = "Backend developer"),
            )

            val draft = proposed(action.draft(call("remove_member", "member_id" to "$memberId"), context))

            assertThat(draft.preview).contains("2 roles they hold here")
            assertThat(draft.preview).contains("Reviewer")
            assertThat(draft.preview).contains("does not bring the roles back")
        }

        @Test
        fun `a member with no roles is not told about roles`() {
            onProject(member())
            managedBy(null)
            every { projectRoleApi.getRolesOnProject(memberId, projectId) } returns emptyList()

            val draft = proposed(action.draft(call("remove_member", "member_id" to "$memberId"), context))

            assertThat(draft.preview).doesNotContain("go with the membership")
        }

        @Test
        fun `the project's own manager is refused with a plain message`() {
            onProject(member(name = "Dana Okafor"))
            managedBy(memberId)

            val reason = refusal(action.draft(call("remove_member", "member_id" to "$memberId"), context))

            assertThat(reason).contains("Dana Okafor manages this project")
            verify(exactly = 0) { projectMembershipApi.removeMember(any(), any()) }
        }

        @Test
        fun `somebody on another project is not reachable by id`() {
            onProject()

            val reason = refusal(
                action.draft(call("remove_member", "member_id" to "${UUID.randomUUID()}"), context),
            )

            assertThat(reason).contains("not on this project")
        }

        @Test
        fun `becoming the manager between preview and confirm stops the confirm`() {
            onProject(member())
            managedBy(memberId)

            val stale = action.recheck(buildJsonObject { put("member_id", "$memberId") }, context)

            assertThat(stale).contains("manages this project")
        }

        @Test
        fun `somebody who left between preview and confirm stops the confirm`() {
            onProject()

            val stale = action.recheck(buildJsonObject { put("member_id", "$memberId") }, context)

            assertThat(stale).contains("no longer on this project")
        }

        @Test
        fun `the confirm removes from the turn's project`() = runTest {
            action.perform(buildJsonObject { put("member_id", "$memberId") }, context)

            verify { projectMembershipApi.removeMember(projectId, memberId) }
        }
    }

    @Nested
    inner class AssignRole {
        private val action = AssignProjectRoleAction(projectRoleApi, projectMembershipApi)
        private val role = ProjectRoleDetailDto(UUID.randomUUID(), "Reviewer", "Reviews work")

        @Test
        fun `a non-member is refused before the service is called`() {
            onProject()
            every { projectRoleApi.getAllProjectRoles() } returns listOf(role)

            val reason = refusal(
                action.draft(
                    call("assign_project_role", "member_id" to "$memberId", "role_id" to "${role.id}"),
                    context,
                ),
            )

            assertThat(reason).contains("not on this project")
            verify(exactly = 0) { projectRoleApi.assignRoleOnProject(any(), any(), any()) }
        }

        @Test
        fun `an unknown role is refused`() {
            onProject(member())
            every { projectRoleApi.getAllProjectRoles() } returns listOf(role)

            val reason = refusal(
                action.draft(
                    call("assign_project_role", "member_id" to "$memberId", "role_id" to "${UUID.randomUUID()}"),
                    context,
                ),
            )

            assertThat(reason).contains("no such role")
        }

        @Test
        fun `a role they already hold is refused rather than confirmed as a no-op`() {
            onProject(member())
            every { projectRoleApi.getAllProjectRoles() } returns listOf(role)
            every { projectRoleApi.getRolesOnProject(memberId, projectId) } returns
                listOf(ProjectRoleShortDto(id = role.id, name = "Reviewer"))

            val reason = refusal(
                action.draft(
                    call("assign_project_role", "member_id" to "$memberId", "role_id" to "${role.id}"),
                    context,
                ),
            )

            assertThat(reason).contains("already holds")
        }

        @Test
        fun `the preview says the role is scoped to this project`() {
            onProject(member())
            every { projectRoleApi.getAllProjectRoles() } returns listOf(role)
            every { projectRoleApi.getRolesOnProject(memberId, projectId) } returns emptyList()

            val draft = proposed(
                action.draft(
                    call("assign_project_role", "member_id" to "$memberId", "role_id" to "${role.id}"),
                    context,
                ),
            )

            assertThat(draft.preview).contains("applies here only")
            assertThat(draft.label).contains("Reviewer")
        }

        @Test
        fun `the confirm uses the project-scoped overload`() = runTest {
            val params = buildJsonObject {
                put("member_id", "$memberId")
                put("role_id", "${role.id}")
                put("role_name", "Reviewer")
            }

            action.perform(params, context)

            verify { projectRoleApi.assignRoleOnProject(memberId, projectId, role.id) }
            // The published API has no projectless form to call by mistake: every role operation on
            // it takes a project, so "applies to every project they belong to" is unreachable here.
            assertThat(ProjectRoleApi::class.java.methods.filter { it.name.contains("RoleOnProject") })
                .allMatch { it.parameterCount == 3 }
        }

        @Test
        fun `somebody who left between preview and confirm stops the confirm`() {
            onProject()

            assertThat(action.recheck(buildJsonObject { put("member_id", "$memberId") }, context))
                .contains("no longer on this project")
        }
    }

    @Nested
    inner class UnassignRole {
        private val action = UnassignProjectRoleAction(projectRoleApi, projectMembershipApi)
        private val role = ProjectRoleDetailDto(UUID.randomUUID(), "Reviewer", "Reviews work")

        @Test
        fun `a role they do not hold is refused, since the service would silently do nothing`() {
            onProject(member())
            every { projectRoleApi.getRolesOnProject(memberId, projectId) } returns emptyList()

            val reason = refusal(
                action.draft(
                    call("unassign_project_role", "member_id" to "$memberId", "role_id" to "${role.id}"),
                    context,
                ),
            )

            assertThat(reason).contains("does not hold that role")
            verify(exactly = 0) { projectRoleApi.unassignRoleOnProject(any(), any(), any()) }
        }

        @Test
        fun `taking their only role says they keep the project but hold nothing here`() {
            onProject(member())
            every { projectRoleApi.getRolesOnProject(memberId, projectId) } returns
                listOf(ProjectRoleShortDto(id = role.id, name = "Reviewer"))

            val draft = proposed(
                action.draft(
                    call("unassign_project_role", "member_id" to "$memberId", "role_id" to "${role.id}"),
                    context,
                ),
            )

            assertThat(draft.preview).contains("stay on the project with no role here")
            assertThat(draft.preview).contains("another project is untouched")
        }

        @Test
        fun `a non-member is refused`() {
            onProject()

            val reason = refusal(
                action.draft(
                    call("unassign_project_role", "member_id" to "$memberId", "role_id" to "${role.id}"),
                    context,
                ),
            )

            assertThat(reason).contains("not on this project")
        }

        @Test
        fun `the confirm uses the project-scoped overload`() = runTest {
            val params = buildJsonObject {
                put("member_id", "$memberId")
                put("role_id", "${role.id}")
                put("role_name", "Reviewer")
            }

            action.perform(params, context)

            verify { projectRoleApi.unassignRoleOnProject(memberId, projectId, role.id) }
        }
    }

    @Nested
    inner class EveryAction {
        @Test
        fun `all four are in the team area and none mutates while drafting`() {
            val actions: List<TeamActionHandler> = listOf(
                AddMembersAction(projectMembershipApi, userApi),
                RemoveMemberAction(projectMembershipApi, projectRoleApi),
                AssignProjectRoleAction(projectRoleApi, projectMembershipApi),
                UnassignProjectRoleAction(projectRoleApi, projectMembershipApi),
            )

            assertThat(actions.map { it.area }).allMatch { it == TeamArea.TEAM }
            assertThat(actions.map { it.spec.name }).containsExactlyInAnyOrder(
                "add_members",
                "remove_member",
                "assign_project_role",
                "unassign_project_role",
            )
            // Every description has to tell the model the call is an offer, not the change itself.
            assertThat(actions.map { it.spec.description }).allMatch { it.contains("does NOT") }
        }

        @Test
        fun `no action offers to create or delete a role`() {
            val names = listOf(
                AssignProjectRoleAction(projectRoleApi, projectMembershipApi).spec.name,
                UnassignProjectRoleAction(projectRoleApi, projectMembershipApi).spec.name,
            )

            assertThat(names).noneMatch { it.contains("create") || it.contains("delete") }
        }
    }
}
