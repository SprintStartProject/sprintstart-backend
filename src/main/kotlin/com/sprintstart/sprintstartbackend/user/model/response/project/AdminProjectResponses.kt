package com.sprintstart.sprintstartbackend.user.model.response.project

import com.sprintstart.sprintstartbackend.user.external.enums.Role
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
    val projectRoles: List<String>,
    val enabled: Boolean,
)

data class DeleteProjectResponse(
    val id: UUID,
    val deleted: Boolean = true,
)
