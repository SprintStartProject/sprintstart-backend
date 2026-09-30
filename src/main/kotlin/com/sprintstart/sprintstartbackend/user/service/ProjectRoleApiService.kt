package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.ProjectRoleApi
import com.sprintstart.sprintstartbackend.user.external.ProjectRoleDetailDto
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * What other modules may do with project roles, and nothing else.
 *
 * Split out of [ProjectRoleService] the same way [ProjectMembershipApiService] is split out of its
 * own service: the published surface is a deliberate subset, and keeping it in its own type is what
 * makes the subset visible. Creating and deleting roles, and editing their skills, change every
 * project at once and are therefore absent here — a caller outside this module cannot reach them
 * even by accident.
 *
 * Only the project-scoped role operations are published. The projectless forms apply a role across
 * every project somebody belongs to, which no caller holding a single project ever means.
 */
@Service
internal class ProjectRoleApiService(
    private val projectRoleService: ProjectRoleService,
) : ProjectRoleApi {
    @Transactional(readOnly = true)
    override fun getProjectRolesByIds(ids: Set<UUID>): Set<ProjectRoleShortDto> =
        projectRoleService.getProjectRolesByIds(ids)

    @Transactional(readOnly = true)
    override fun getAllProjectRoles(): List<ProjectRoleDetailDto> =
        projectRoleService.getAllRoles().map {
            ProjectRoleDetailDto(id = it.id, name = it.name, description = it.description)
        }

    @Transactional(readOnly = true)
    override fun getRolesOnProject(userId: UUID, projectId: UUID): List<ProjectRoleShortDto> =
        projectRoleService.getRolesForUserOnProject(userId, projectId).map {
            ProjectRoleShortDto(id = it.id, name = it.name)
        }

    override fun assignRoleOnProject(userId: UUID, projectId: UUID, roleId: UUID) =
        projectRoleService.assignRoleToUser(userId, projectId, roleId)

    override fun unassignRoleOnProject(userId: UUID, projectId: UUID, roleId: UUID) =
        projectRoleService.unassignRoleFromUser(userId, projectId, roleId)
}
