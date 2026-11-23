package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.textArray
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.ProjectRoleApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/*
 * The actions of team mode's team area: who is on the project, and what they do here.
 *
 * The services behind these take an id and act on it, and sit behind admin-only routes. A manager reaches
 * them here only because the turn already proved they manage this project, so every target is resolved
 * against this project's membership first, at draft and again at confirm.
 *
 * Roles are scoped to the membership: the project-less overloads of `assignRoleToUser` and
 * `unassignRoleFromUser` apply across every project somebody belongs to and are never called from here.
 */

/** The people a confirmed add puts on the project. */
@Serializable
data class AddMembersParams(
    @SerialName("user_ids") val userIds: List<
        @Serializable(with = UuidSerializer::class)
        UUID,
    >,
)

/** The member a confirmed removal takes off the project. */
@Serializable
data class RemoveMemberParams(
    @SerialName("member_id") @Serializable(with = UuidSerializer::class) val memberId: UUID,
)

/** The member and the role a confirmed role change applies to; [roleName] is only for the message. */
@Serializable
data class ProjectRoleParams(
    @SerialName("member_id") @Serializable(with = UuidSerializer::class) val memberId: UUID,
    @SerialName("role_id") @Serializable(with = UuidSerializer::class) val roleId: UUID,
    @SerialName("role_name") val roleName: String,
)

/** The member [memberId] names on [projectId], or null. Shared with [TeamMemberTools]. */
internal fun ProjectMembershipApi.memberOn(memberId: UUID?, projectId: UUID): ProjectMember? =
    memberId?.let { id -> getProjectMembers(projectId).firstOrNull { it.userId == id } }

/**
 * The roles [userId] holds on [projectId]; empty when they hold none.
 *
 * Only the 404 for a non-member is read as "no roles"; anything else is rethrown.
 */
private fun ProjectRoleApi.rolesHeldOn(userId: UUID, projectId: UUID): List<ProjectRoleShortDto> =
    runCatching { getRolesOnProject(userId, projectId) }
        .getOrElse { if (it is ResponseStatusException) emptyList() else throw it }

private const val NOT_A_MEMBER =
    "That person is not on this project. Call find_member for the people who are, and pass the member_id " +
        "it gives."

private const val LEFT_SINCE = "That person is no longer on this project, so nothing was changed."

/**
 * Offers to put one or more people on the project.
 *
 * The ids come from `find_user_to_add`, which resolves exactly one person, so a model cannot guess them.
 */
@Component
class AddMembersAction(
    private val projectMembershipApi: ProjectMembershipApi,
    private val userApi: UserApi,
) : TypedTeamAction<AddMembersParams>(AddMembersParams.serializer()) {
    override val area = TeamArea.TEAM
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "add_members",
        description = "Offer to put one or more people on this project. Every user_id must come from " +
            "find_user_to_add — never guess one, and never pass a member_id, which is somebody already " +
            "here. Several people are one offer the manager confirms once. This does NOT add anybody by " +
            "itself.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("user_ids") {
                    put("type", "array")
                    put("description", "The user_ids from find_user_to_add.")
                    putJsonObject("items") { put("type", "string") }
                }
            }
            putJsonArray("required") { add("user_ids") }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val raw = call.arguments.textArray("user_ids")
        if (raw.isEmpty()) {
            return TeamActionDraft.Refused("No user_ids were given, so there is nobody to add.")
        }
        val ids = raw.map { runCatching { UUID.fromString(it) }.getOrNull() }
        if (ids.any { it == null }) {
            return TeamActionDraft.Refused(
                "One of those is not a user_id. Call find_user_to_add for each person and pass the " +
                    "user_id it returns.",
            )
        }

        val alreadyHere = projectMembershipApi.getProjectMembers(context.projectId).map { it.userId }.toSet()
        val wanted = ids.filterNotNull().distinct()
        if (wanted.any { it in alreadyHere }) {
            return TeamActionDraft.Refused(
                "Somebody in that list is already on this project. Call find_member to see who is here.",
            )
        }
        val found = userApi.getUsersByIds(wanted).associateBy { it.id }
        val people = wanted.map { id ->
            val person = found[id]
                ?: return TeamActionDraft.Refused(
                    "One of those user_ids is not a person any more. Call find_user_to_add again.",
                )
            id to "${person.firstname} ${person.lastname}".trim().ifBlank { person.username }
        }

        return proposed(
            params = AddMembersParams(people.map { it.first }),
            label = if (people.size == 1) "Add ${people.single().second.forLabel()}" else "Add ${people.size} people",
            preview = buildString {
                appendLine(if (people.size == 1) "Put this person on the project:" else "Put these people on it:")
                people.forEach { (_, name) -> appendLine("- $name") }
                appendLine()
                append(
                    "They get access to everything on this project — its material, its board and its " +
                        "onboarding — and start with no role here until one is given.",
                )
            }.trim(),
        )
    }

    override fun recheckParams(params: AddMembersParams, context: TeamToolContext): String? {
        val alreadyHere = projectMembershipApi.getProjectMembers(context.projectId).map { it.userId }.toSet()
        val ids = params.userIds
        if (ids.any { it in alreadyHere }) {
            return "Somebody in that list joined this project since, so nothing was changed."
        }
        // An account deleted in the meantime would otherwise reach the manager as the service's 404.
        val found = userApi.getUsersByIds(ids).map { it.id }.toSet()
        return "Somebody in that list is not a person any more, so nothing was changed."
            .takeIf { ids.any { id -> id !in found } }
    }

    override suspend fun performParams(params: AddMembersParams, context: TeamToolContext): String {
        val ids = params.userIds
        projectMembershipApi.addMembers(context.projectId, ids.toSet())
        return if (ids.size == 1) {
            "Added. They are on this project now, with no role here yet."
        } else {
            "Added ${ids.size} people to this project. None of them holds a role here yet."
        }
    }
}

/**
 * Offers to take somebody off the project.
 *
 * Destructive because the membership carries the person's roles here: removing it drops them, and adding
 * the person back starts with none.
 */
@Component
class RemoveMemberAction(
    private val projectMembershipApi: ProjectMembershipApi,
    private val projectRoleApi: ProjectRoleApi,
) : TypedTeamAction<RemoveMemberParams>(RemoveMemberParams.serializer()) {
    override val area = TeamArea.TEAM
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "remove_member",
        description = "Offer to take somebody off this project. They lose access to everything on it, and " +
            "the roles they hold here go with the membership. The project's own manager cannot be removed " +
            "this way. This does NOT remove anybody by itself; the manager confirms.",
        parameters = stringFields(
            "member_id" to "The member_id from find_member.",
            required = listOf("member_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val memberId = call.uuidArgument("member_id")
        val member = projectMembershipApi.memberOn(memberId, context.projectId)
            ?: return TeamActionDraft.Refused(NOT_A_MEMBER)

        managerRefusal(member, context)?.let { return TeamActionDraft.Refused(it) }

        val roles = rolesOf(member.userId, context.projectId)
        return proposed(
            params = RemoveMemberParams(member.userId),
            label = "Remove ${member.displayName.forLabel()} from the project",
            preview = buildString {
                appendLine("Take ${member.displayName} off this project.")
                appendLine()
                appendLine("They lose access to this project's material, board and onboarding.")
                if (roles.isNotEmpty()) {
                    appendLine(
                        "The ${roles.size} role${if (roles.size == 1) "" else "s"} they hold here " +
                            "(${roles.joinToString(", ")}) go with the membership. Adding them back later " +
                            "does not bring the roles back — they would have to be given again.",
                    )
                }
                append("What they already did on this project is not deleted.")
            }.trim(),
        )
    }

    override fun recheckParams(params: RemoveMemberParams, context: TeamToolContext): String? {
        val member = projectMembershipApi.memberOn(params.memberId, context.projectId)
            ?: return LEFT_SINCE
        // They may have been made manager between the preview and the confirm.
        return managerRefusal(member, context)
    }

    override suspend fun performParams(params: RemoveMemberParams, context: TeamToolContext): String {
        projectMembershipApi.removeMember(context.projectId, params.memberId)
        return "Removed. They are off this project, and the roles they held here went with the membership."
    }

    private fun managerRefusal(member: ProjectMember, context: TeamToolContext): String? {
        val managerId = projectMembershipApi.getProjectManagerId(context.projectId).orElse(null)
        return if (managerId == member.userId) {
            "${member.displayName} manages this project, so they cannot be taken off it here. The project's " +
                "manager has to be changed first, which is an administrator's job."
        } else {
            null
        }
    }

    private fun rolesOf(userId: UUID, projectId: UUID): List<String> =
        projectRoleApi.rolesHeldOn(userId, projectId).map { it.name }
}

/** Offers to give somebody a role on this project. */
@Component
class AssignProjectRoleAction(
    private val projectRoleApi: ProjectRoleApi,
    private val projectMembershipApi: ProjectMembershipApi,
) : TypedTeamAction<ProjectRoleParams>(ProjectRoleParams.serializer()) {
    override val area = TeamArea.TEAM
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "assign_project_role",
        description = "Offer to give somebody on this project a role here. Call list_project_roles for the " +
            "role_id and get_member_roles for what they already hold. A role applies to this project only — " +
            "the same person may do something else on another. This does NOT give anybody a role by itself.",
        parameters = stringFields(
            "member_id" to "The member_id from find_member.",
            "role_id" to "The role_id from list_project_roles.",
            required = listOf("member_id", "role_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val member = projectMembershipApi.memberOn(call.uuidArgument("member_id"), context.projectId)
            ?: return TeamActionDraft.Refused(NOT_A_MEMBER)
        val roleId = call.uuidArgument("role_id")
        val role = projectRoleApi.getAllProjectRoles().firstOrNull { it.id == roleId }
            ?: return TeamActionDraft.Refused(
                "There is no such role. Call list_project_roles and pass a role_id from it.",
            )
        if (projectRoleApi.rolesHeldOn(member.userId, context.projectId).any { it.id == role.id }) {
            return TeamActionDraft.Refused(
                "${member.displayName} already holds “${role.name}” on this project, so there is nothing " +
                    "to change.",
            )
        }

        return proposed(
            params = ProjectRoleParams(member.userId, role.id, role.name),
            label = "Make ${member.displayName.forLabel()} ${role.name}",
            preview = "Give ${member.displayName} the role “${role.name}” on this project.\n\n" +
                "It applies here only — any role they hold on another project is untouched.",
        )
    }

    /** They are still here and still without the role: a repeated assignment is no error, so it must be checked. */
    override fun recheckParams(params: ProjectRoleParams, context: TeamToolContext): String? {
        val member = projectMembershipApi.memberOn(params.memberId, context.projectId)
            ?: return LEFT_SINCE
        return "They were given “${params.roleName}” on this project since, so nothing was changed."
            .takeIf { _ -> projectRoleApi.rolesHeldOn(member.userId, context.projectId).any { it.id == params.roleId } }
    }

    override suspend fun performParams(params: ProjectRoleParams, context: TeamToolContext): String {
        projectRoleApi.assignRoleOnProject(params.memberId, context.projectId, params.roleId)
        return "Done. They hold “${params.roleName}” on this project now."
    }
}

/** Offers to take a role off somebody on this project. */
@Component
class UnassignProjectRoleAction(
    private val projectRoleApi: ProjectRoleApi,
    private val projectMembershipApi: ProjectMembershipApi,
) : TypedTeamAction<ProjectRoleParams>(ProjectRoleParams.serializer()) {
    override val area = TeamArea.TEAM
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "unassign_project_role",
        description = "Offer to take a role off somebody on this project. Call get_member_roles first for " +
            "what they actually hold and its role_id. This affects this project only. It does NOT take " +
            "anything off by itself.",
        parameters = stringFields(
            "member_id" to "The member_id from find_member.",
            "role_id" to "The role_id from get_member_roles.",
            required = listOf("member_id", "role_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val member = projectMembershipApi.memberOn(call.uuidArgument("member_id"), context.projectId)
            ?: return TeamActionDraft.Refused(NOT_A_MEMBER)
        val roleId = call.uuidArgument("role_id")
        // The service removes by id silently, so a role they do not hold would confirm as a change.
        val held = projectRoleApi.rolesHeldOn(member.userId, context.projectId)
        val role = held.firstOrNull { it.id == roleId }
            ?: return TeamActionDraft.Refused(
                "${member.displayName} does not hold that role on this project. Call get_member_roles for " +
                    "the ones they do.",
            )

        return proposed(
            params = ProjectRoleParams(member.userId, role.id, role.name),
            label = "Take “${role.name}” off ${member.displayName.forLabel()}",
            preview = "Take the role “${role.name}” off ${member.displayName} on this project.\n\n" +
                "They stay on the project${remaining(held.size)}. " +
                "Any role they hold on another project is untouched.",
        )
    }

    private fun remaining(heldNow: Int): String =
        when (heldNow) {
            1 -> " with no role here"
            2 -> " and keep their other role here"
            else -> " and keep their other ${heldNow - 1} roles here"
        }

    /** They are still here and still hold the role: the service removes by id silently. */
    override fun recheckParams(params: ProjectRoleParams, context: TeamToolContext): String? {
        val member = projectMembershipApi.memberOn(params.memberId, context.projectId)
            ?: return LEFT_SINCE
        return "“${params.roleName}” is already off them on this project, so nothing was changed."
            .takeIf { _ ->
                projectRoleApi
                    .rolesHeldOn(
                        member.userId,
                        context.projectId,
                    ).none { it.id == params.roleId }
            }
    }

    override suspend fun performParams(params: ProjectRoleParams, context: TeamToolContext): String {
        projectRoleApi.unassignRoleOnProject(params.memberId, context.projectId, params.roleId)
        return "Done. “${params.roleName}” is off them on this project."
    }
}
