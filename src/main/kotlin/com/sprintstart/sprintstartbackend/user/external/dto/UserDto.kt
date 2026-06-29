package com.sprintstart.sprintstartbackend.user.external.dto

import java.util.UUID

data class UserDto(
    val id: UUID,
    val username: String,
    val firstname: String,
    val lastname: String,
    val avatarUrl: String?,
    val workingArea: String,
    val project: ProjectDto?,
    val skills: List<UserSkillDto>,
    val projectRoles: List<ProjectRoleDto>,
)

data class ProjectDto(
    val projectId: UUID,
    val name: String,
    val description: String?,
)

data class UserSkillDto(
    val skillId: UUID,
    val name: String,
    val level: String,
)

data class ProjectRoleDto(
    val roleId: UUID,
    val name: String,
    val description: String,
)
