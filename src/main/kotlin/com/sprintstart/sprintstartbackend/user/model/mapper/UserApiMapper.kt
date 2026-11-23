package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.entity.UserSkillAssessment

/**
 * Every role this person holds, across all their project memberships.
 *
 * Roles are scoped to a project assignment, so the person-wide view surfaces — a profile, a team
 * overview — read the union. Distinct by id: the same role held on two projects appears once.
 */
fun User.allProjectRoles(): List<ProjectRole> =
    projectAssignments
        .flatMap { it.projectRoles }
        .distinctBy { it.id }

fun User.toUserApiDto(): UserDto {
    return UserDto(
        id = this.id,
        username = this.username,
        firstname = this.firstname,
        lastname = this.lastname,
        avatarUrl = this.avatarUrl,
        profileIcon = this.profileIcon,
        projects = this.projects.map { it.toApiDto() }.toSet(),
        projectRoles = this.allProjectRoles().map { it.toApiDto() },
        skills = this.skillAssessments.map { it.toApiDto() },
    )
}

private fun Project.toApiDto() = ProjectDto(projectId = id, name = name, description = description)

private fun ProjectRole.toApiDto() = ProjectRoleDto(roleId = id, name = name, description = description)

private fun UserSkillAssessment.toApiDto() = UserSkillDto(skillId = skill.id, name = skill.name, level = level.name)
