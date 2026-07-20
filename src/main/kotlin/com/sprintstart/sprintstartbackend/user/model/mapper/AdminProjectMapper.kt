package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceDto
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectListResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectSourceResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserSummaryResponse

fun Project.toAdminListResponse(
    sources: List<ProjectSourceDto>,
    assignments: List<ProjectUserAssignment>,
): AdminProjectListResponse {
    return AdminProjectListResponse(
        id = id,
        name = name,
        description = description,
        sources = sources.map { it.toResponse() },
        users = assignments.map { it.user.toSummaryResponse() },
    )
}

fun Project.toAdminDetailResponse(
    sources: List<ProjectSourceDto>,
    assignments: List<ProjectUserAssignment>,
): AdminProjectDetailResponse {
    return AdminProjectDetailResponse(
        id = id,
        name = name,
        description = description,
        sources = sources.map { it.toResponse() },
        users = assignments.map { it.toProjectUserResponse() },
    )
}

fun ProjectSourceDto.toResponse(): ProjectSourceResponse {
    return ProjectSourceResponse(
        id = id,
        name = name,
        type = type,
        status = status,
    )
}

fun ProjectUserAssignment.toProjectUserResponse(): ProjectUserResponse {
    return ProjectUserResponse(
        id = user.id,
        username = user.username,
        email = user.email,
        firstName = user.firstname,
        lastName = user.lastname,
        roles = user.roles.toSet(),
        projectRoles = projectRoles.map { it.name }.sorted(),
        enabled = user.enabled,
    )
}

private fun User.toSummaryResponse(): ProjectUserSummaryResponse {
    return ProjectUserSummaryResponse(
        id = id,
        username = username,
        email = email,
    )
}
