package com.sprintstart.sprintstartbackend.user.external.dto

import java.util.UUID

data class UserDto(
    val id: UUID,
    val username: String,
    val firstname: String,
    val lastname: String,
    val avatarUrl: String?,
    val profileIcon: String?,
    val projects: Set<ProjectDto>,
    val projectRoles: List<ProjectRoleDto>,
    // Defaulted so callers written against the newer model need not restate it; the real mapper
    // always populates it from the user's assessments.
    val skills: List<UserSkillDto> = emptyList(),
)

data class ProjectDto(
    val projectId: UUID,
    val name: String,
    val description: String?,
)

data class ProjectRoleDto(
    val roleId: UUID,
    val name: String,
    val description: String,
)

data class UserSkillDto(
    val skillId: UUID,
    val name: String,
    val level: String,
)

/**
 * Derives the AI blueprint scope slug for this role, e.g. "Backend Developer" -> "backend-developer".
 * Must be the same slug format used everywhere a role's blueprint scope is looked up or stored.
 */
fun ProjectRoleDto.toAiScope(): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-")
