package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import com.sprintstart.sprintstartbackend.user.model.entity.User

fun User.toUserApiDto(): UserDto {
    return UserDto(
        id = this.id,
        username = this.username,
        firstname = this.firstname,
        lastname = this.lastname,
        avatarUrl = this.avatarUrl,
        profileIcon = this.profileIcon,
        projects = this.projects
            .map {
                ProjectDto(
                    projectId = it.id,
                    name = it.name,
                    description = it.description,
                )
            }.toSet(),
        skills = this.skillAssessments.map { assessment ->
            UserSkillDto(
                skillId = assessment.skill.id,
                name = assessment.skill.name,
                level = assessment.level.name,
            )
        },
        projectRoles = this.projectRoles.map { role ->
            ProjectRoleDto(
                roleId = role.id,
                name = role.name,
                description = role.description,
            )
        },
    )
}
