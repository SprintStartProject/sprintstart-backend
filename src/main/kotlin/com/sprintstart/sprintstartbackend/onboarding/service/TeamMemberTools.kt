package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.service.ProjectRoleService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The read tools of team mode's team area: the roles a project can hand out, the ones a member
 * holds, and a way to name somebody who is not on the project yet.
 *
 * The first two are scoped to the turn's project. The third is the one read here that is not about
 * this project at all — adding somebody means naming a person the manager cannot yet see — and it
 * is deliberately the narrowest thing that can do that job. See [UserApi.findByExactEmailOrGithubLogin].
 */
@Component
class TeamMemberTools(
    private val projectRoleService: ProjectRoleService,
    private val projectMembershipApi: ProjectMembershipApi,
    private val userApi: UserApi,
) : TeamAreaTools {
    override val area = TeamArea.TEAM

    override fun toolSpecs(): List<BuddyToolSpecDto> =
        listOf(LIST_PROJECT_ROLES_SPEC, GET_MEMBER_ROLES_SPEC, FIND_USER_TO_ADD_SPEC)

    override fun handles(toolName: String): Boolean =
        toolName == LIST_PROJECT_ROLES || toolName == GET_MEMBER_ROLES || toolName == FIND_USER_TO_ADD

    override fun execute(call: BuddyToolCallDto, context: TeamToolContext): String =
        when (call.name) {
            LIST_PROJECT_ROLES -> projectRoles()
            GET_MEMBER_ROLES -> memberRoles(call.uuidArgument("member_id"), context.projectId)
            FIND_USER_TO_ADD -> findUserToAdd(call.textArgument("email_or_github_login"), context.projectId)
            else -> "Unknown tool: ${call.name}."
        }

    /**
     * The role catalogue, which is organisation-wide rather than this project's.
     *
     * Readable because assigning a role needs its id; not editable here — creating and deleting
     * roles changes every project at once, so it stays an admin surface.
     */
    private fun projectRoles(): String {
        val roles = projectRoleService.getAllRoles()
        if (roles.isEmpty()) {
            return "No project roles exist yet. They are created by an administrator, not from here."
        }
        return buildString {
            appendLine("Roles that can be given to somebody on this project:")
            roles.sortedBy { it.name }.forEach { role ->
                append("- ${role.name} [role_id: ${role.id}]")
                role.description.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                appendLine()
            }
            append("Creating or deleting roles is an administrator's job, not something to offer here.")
        }.trim()
    }

    private fun memberRoles(memberId: UUID?, projectId: UUID): String {
        val member = memberOn(memberId, projectId) ?: return NOT_A_MEMBER
        // The service 404s rather than returning empty for a non-member, which the membership check
        // above has already ruled out; anything else from it is worth saying plainly.
        val roles = runCatching { projectRoleService.getRolesForUserOnProject(member.userId, projectId) }
            .getOrElse { return if (it is ResponseStatusException) NOT_A_MEMBER else throw it }

        if (roles.isEmpty()) {
            return "${member.displayName} is on this project but holds no role here."
        }
        return buildString {
            appendLine("${member.displayName} holds these roles on this project:")
            roles.forEach { appendLine("- ${it.name} [role_id: ${it.id}]") }
        }.trim()
    }

    /**
     * Somebody not on the project yet, by an identifier the manager already has.
     *
     * Exact and single by contract, so a manager can add the person they mean without the buddy
     * becoming a way to read the whole staff list. Somebody who is already on the project is
     * answered as such rather than offered again.
     */
    private fun findUserToAdd(query: String, projectId: UUID): String {
        if (query.isBlank()) {
            return "No email or GitHub login was given. This finds one person by their exact address " +
                "or account name — it cannot list or search for people."
        }
        val match = userApi.findByExactEmailOrGithubLogin(query).orElse(null)
            ?: return "Nobody has exactly that email address or GitHub login. Check the spelling with the " +
                "manager — a partial or approximate one finds nobody on purpose."

        val already = projectMembershipApi.getProjectMembers(projectId).any { it.userId == match.userId }
        return if (already) {
            "${match.displayName} is already on this project [member_id: ${match.userId}]. " +
                "There is nothing to add; their roles can be changed instead."
        } else {
            "${match.displayName} [user_id: ${match.userId}] — not on this project yet. " +
                "Offer add_members with this user_id to put them on it."
        }
    }

    private fun memberOn(memberId: UUID?, projectId: UUID) =
        memberId?.let { id -> projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == id } }

    companion object {
        const val LIST_PROJECT_ROLES = "list_project_roles"
        const val GET_MEMBER_ROLES = "get_member_roles"
        const val FIND_USER_TO_ADD = "find_user_to_add"

        private const val NOT_A_MEMBER =
            "That person is not on this project, so their roles here cannot be read. Call find_member for " +
                "the people who are."

        private fun noArgs() = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
        }

        val LIST_PROJECT_ROLES_SPEC = BuddyToolSpecDto(
            name = LIST_PROJECT_ROLES,
            description = "The roles that can be given to somebody on this project, each with its role_id. " +
                "Read it before offering to assign or unassign a role, and pass the role_id it gives. Roles " +
                "are shared across the whole organisation: creating or deleting one is an administrator's " +
                "job and must never be offered here. Takes no arguments.",
            parameters = noArgs(),
        )

        val GET_MEMBER_ROLES_SPEC = BuddyToolSpecDto(
            name = GET_MEMBER_ROLES,
            description = "What one person on this project does here, as roles with their role_ids. Use the " +
                "member_id from find_member or get_team_attention. Roles are per project, so this is about " +
                "this project only — the same person may do something else elsewhere.",
            parameters = stringFields(
                "member_id" to "The member_id from find_member.",
                required = listOf("member_id"),
            ),
        )

        val FIND_USER_TO_ADD_SPEC = BuddyToolSpecDto(
            name = FIND_USER_TO_ADD,
            description = "Find one person who is not on this project yet, by their exact email address or " +
                "exact GitHub login. Ask the manager for the full address or account name — this matches " +
                "exactly and finds nobody on a partial or approximate one, and it cannot list or browse " +
                "people. Use find_member instead for somebody already on the project.",
            parameters = stringFields(
                "email_or_github_login" to "The person's full email address, or their exact GitHub login.",
                required = listOf("email_or_github_login"),
            ),
        )
    }
}
