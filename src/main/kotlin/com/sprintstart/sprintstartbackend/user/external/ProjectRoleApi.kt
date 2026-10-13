package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import java.util.UUID

interface ProjectRoleApi {
    /**
     * Returns the project roles matching the given ids.
     *
     * Unknown ids are silently omitted, so the result may be smaller than the requested id
     * set — or empty. Roles are returned in their short form without linked skills.
     *
     * @param ids Ids of the project roles to resolve.
     * @return The matching project roles, mapped to [ProjectRoleShortDto]s.
     */
    fun getProjectRolesByIds(ids: Set<UUID>): Set<ProjectRoleShortDto>

    /**
     * The whole role catalogue, which is organisation-wide rather than per project.
     *
     * Published so a caller can offer a choice of roles without reaching for the repository. It is
     * a read: creating and deleting roles changes every project at once and stays an admin surface.
     */
    fun getAllProjectRoles(): List<ProjectRoleDetailDto>

    /**
     * The roles somebody holds on one project.
     *
     * Roles are scoped to the membership, so the project is part of the question rather than a
     * filter applied afterwards.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when they are not on it,
     * which is deliberately distinguishable from holding no role there.
     */
    fun getRolesOnProject(userId: UUID, projectId: UUID): List<ProjectRoleShortDto>

    /**
     * Gives somebody a role on one project, and only there.
     *
     * The project-scoped form on purpose: the projectless one applies a role across every project
     * somebody belongs to, which is never what a caller holding a single project means.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when they are not on the
     * project, or no such role exists.
     */
    fun assignRoleOnProject(userId: UUID, projectId: UUID, roleId: UUID)

    /**
     * Takes a role off somebody on one project, leaving the same role on their other projects.
     *
     * Removing a role they do not hold is not an error, so a caller that means to report a change
     * has to check what they hold first.
     */
    fun unassignRoleOnProject(userId: UUID, projectId: UUID, roleId: UUID)
}

/**
 * A role with the text that explains it — what a caller offering a choice between roles needs.
 *
 * Separate from [ProjectRoleShortDto] rather than widening it: most callers resolve a role they
 * already chose and have no use for the description.
 */
data class ProjectRoleDetailDto(
    val id: UUID,
    val name: String,
    val description: String,
)
