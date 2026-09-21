package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.service.AdminProjectService
import com.sprintstart.sprintstartbackend.user.service.ProjectRoleService
import kotlinx.serialization.json.JsonObject
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
 * The services behind these take an id and act on it. `assignUsers` and `removeUser` sit behind
 * every `/api/v1/admin/projects` route, which is `hasRole('ADMIN')` — a manager cannot reach them
 * through REST, and reaches them here only because the turn already proved they manage this
 * project. So every target is resolved against this project's membership first, at draft and again
 * at confirm, and the project is always the turn's.
 *
 * Roles are scoped to the membership rather than to the person. The project-less overloads of
 * `assignRoleToUser` and `unassignRoleFromUser` apply a role across every project somebody belongs
 * to; they are never called from here, and the three-argument forms always are.
 */

/** The member [memberId] names on [projectId], or null. */
private fun ProjectMembershipApi.memberOn(memberId: UUID?, projectId: UUID): ProjectMember? =
    memberId?.let { id -> getProjectMembers(projectId).firstOrNull { it.userId == id } }

private const val NOT_A_MEMBER =
    "That person is not on this project. Call find_member for the people who are, and pass the member_id " +
        "it gives."

private const val LEFT_SINCE = "That person is no longer on this project, so nothing was changed."

/**
 * Offers to put one or more people on the project.
 *
 * The ids come from `find_user_to_add`, which resolves exactly one person from an identifier the
 * manager already has — so a model cannot assemble this call by guessing.
 */
@Component
class AddMembersAction(
    private val adminProjectService: AdminProjectService,
    private val projectMembershipApi: ProjectMembershipApi,
    private val userApi: UserApi,
) : TeamActionHandler {
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
        val people = mutableListOf<Pair<UUID, String>>()
        for (id in ids.filterNotNull().distinct()) {
            if (id in alreadyHere) {
                return TeamActionDraft.Refused(
                    "Somebody in that list is already on this project. Call find_member to see who is here.",
                )
            }
            // Resolved for the preview: a manager confirming "add two people" has to be told which two.
            val person = userApi.getUsersByIds(listOf(id)).firstOrNull()
                ?: return TeamActionDraft.Refused(
                    "One of those user_ids is not a person any more. Call find_user_to_add again.",
                )
            val name = "${person.firstname} ${person.lastname}".trim().ifBlank { person.username }
            people += id to name
        }

        return TeamActionDraft.Proposed(
            params = buildJsonObject { putJsonArray("user_ids") { people.forEach { add(it.first.toString()) } } },
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

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val alreadyHere = projectMembershipApi.getProjectMembers(context.projectId).map { it.userId }.toSet()
        val ids = params.textArray("user_ids").mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        return "Somebody in that list joined this project since, so nothing was changed."
            .takeIf { ids.any { id -> id in alreadyHere } }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val ids = params.textArray("user_ids").mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        adminProjectService.assignUsers(context.projectId, AssignProjectUsersRequest(userIds = ids.toSet()))
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
 * Destructive for a reason the preview has to carry: the membership is what the person's roles here
 * hang off, so removing it drops them, and putting the person back later gives them a fresh
 * membership with none.
 */
@Component
class RemoveMemberAction(
    private val adminProjectService: AdminProjectService,
    private val projectMembershipApi: ProjectMembershipApi,
    private val projectRoleService: ProjectRoleService,
) : TeamActionHandler {
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
        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("member_id", member.userId.toString()) },
            label = "Remove ${member.displayName.forLabel()} from the project",
            preview = buildString {
                appendLine("Take ${member.displayName} off this project.")
                appendLine()
                appendLine("They lose access to this project's material, board and onboarding.")
                if (roles.isNotEmpty()) {
                    // The roles live on the membership row, so deleting it deletes them. Putting the
                    // person back later creates a new membership, which starts with none.
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

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val member = projectMembershipApi.memberOn(params.uuid("member_id"), context.projectId)
            ?: return LEFT_SINCE
        // They may have been made manager between the preview and the confirm.
        return managerRefusal(member, context)
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val memberId = requireNotNull(params.uuid("member_id"))
        adminProjectService.removeUser(context.projectId, memberId)
        return "Removed. They are off this project, and the roles they held here went with the membership."
    }

    /** Why this member cannot be removed, or null. */
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
        runCatching { projectRoleService.getRolesForUserOnProject(userId, projectId).map { it.name } }
            .getOrElse { if (it is ResponseStatusException) emptyList() else throw it }
}

/** Offers to give somebody a role on this project. */
@Component
class AssignProjectRoleAction(
    private val projectRoleService: ProjectRoleService,
    private val projectMembershipApi: ProjectMembershipApi,
) : TeamActionHandler {
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
        val role = projectRoleService.getAllRoles().firstOrNull { it.id == roleId }
            ?: return TeamActionDraft.Refused(
                "There is no such role. Call list_project_roles and pass a role_id from it.",
            )
        if (heldBy(member.userId, context.projectId).any { it.equals(role.name, ignoreCase = true) }) {
            return TeamActionDraft.Refused(
                "${member.displayName} already holds “${role.name}” on this project, so there is nothing " +
                    "to change.",
            )
        }

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("member_id", member.userId.toString())
                put("role_id", role.id.toString())
                put("role_name", role.name)
            },
            label = "Make ${member.displayName.forLabel()} ${role.name}",
            preview = "Give ${member.displayName} the role “${role.name}” on this project.\n\n" +
                "It applies here only — any role they hold on another project is untouched.",
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        LEFT_SINCE.takeIf { projectMembershipApi.memberOn(params.uuid("member_id"), context.projectId) == null }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        projectRoleService.assignRoleToUser(
            requireNotNull(params.uuid("member_id")),
            context.projectId,
            requireNotNull(params.uuid("role_id")),
        )
        return "Done. They hold “${params.text("role_name")}” on this project now."
    }

    private fun heldBy(userId: UUID, projectId: UUID): List<String> =
        runCatching { projectRoleService.getRolesForUserOnProject(userId, projectId).map { it.name } }
            .getOrElse { if (it is ResponseStatusException) emptyList() else throw it }
}

/** Offers to take a role off somebody on this project. */
@Component
class UnassignProjectRoleAction(
    private val projectRoleService: ProjectRoleService,
    private val projectMembershipApi: ProjectMembershipApi,
) : TeamActionHandler {
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
        // The service removes by id without complaining when nothing matches, so a role they do not
        // hold would confirm as a change and change nothing. Checked here instead.
        val held = runCatching { projectRoleService.getRolesForUserOnProject(member.userId, context.projectId) }
            .getOrElse { if (it is ResponseStatusException) emptyList() else throw it }
        val role = held.firstOrNull { it.id == roleId }
            ?: return TeamActionDraft.Refused(
                "${member.displayName} does not hold that role on this project. Call get_member_roles for " +
                    "the ones they do.",
            )

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("member_id", member.userId.toString())
                put("role_id", role.id.toString())
                put("role_name", role.name)
            },
            label = "Take “${role.name}” off ${member.displayName.forLabel()}",
            preview = "Take the role “${role.name}” off ${member.displayName} on this project.\n\n" +
                "They stay on the project" +
                (if (held.size == 1) " with no role here" else " and keep their other ${held.size - 1} role(s) here") +
                ". Any role they hold on another project is untouched.",
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        LEFT_SINCE.takeIf { projectMembershipApi.memberOn(params.uuid("member_id"), context.projectId) == null }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        projectRoleService.unassignRoleFromUser(
            requireNotNull(params.uuid("member_id")),
            context.projectId,
            requireNotNull(params.uuid("role_id")),
        )
        return "Done. “${params.text("role_name")}” is off them on this project."
    }
}
