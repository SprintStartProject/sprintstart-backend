package com.sprintstart.sprintstartbackend.user.model.response.project

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.response.user.ProjectRoleSummary
import java.util.UUID

data class AdminProjectListResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val manager: ProjectManagerResponse?,
    val sources: List<ProjectSourceResponse>,
    val users: List<ProjectUserSummaryResponse>,
)

data class AdminProjectDetailResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val manager: ProjectManagerResponse?,
    val sources: List<ProjectSourceResponse>,
    val users: List<ProjectUserResponse>,
)

/**
 * The single project manager responsible for a project, or a candidate for that assignment.
 */
data class ProjectManagerResponse(
    val id: UUID,
    val username: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
)

/**
 * Compact project representation for the projects a user is allowed to manage.
 */
data class ManagedProjectResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val memberCount: Int,
)

data class ProjectSourceResponse(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
)

data class ProjectUserSummaryResponse(
    val id: UUID,
    val username: String,
    val email: String?,
)

data class ProjectUserResponse(
    val id: UUID,
    val username: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val roles: Set<Role>,
    /** The names of the roles this person holds. Unchanged wire shape; clients may keep reading it. */
    val projectRoles: List<String>,
    /**
     * The same roles, carrying ids.
     *
     * Added alongside [projectRoles] rather than replacing it: the list is editable from the project
     * surface, and removing a role by name would take the wrong one off whenever two roles share a
     * name. Serving both means neither client has to ship in lockstep with this change.
     */
    val projectRoleRefs: List<ProjectRoleSummary> = emptyList(),
    val enabled: Boolean,
)

data class DeleteProjectResponse(
    val id: UUID,
    val deleted: Boolean = true,
)
