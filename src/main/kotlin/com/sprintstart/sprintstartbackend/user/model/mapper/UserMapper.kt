package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.response.user.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.response.user.ProjectRoleSummary

fun User.toGetResponse(): GetUserResponse =
    GetUserResponse(
        id = this.id,
        authId = this.authId,
        username = this.username,
        email = this.email,
        firstName = this.firstname,
        lastName = this.lastname,
        projectIds = this.projects.map { it.id }.toSet(),
        roles = this.roles.toSet(),
        projectRoles = this.projectRoles.map { ProjectRoleSummary(id = it.id, name = it.name) },
        permissionGroup = this.effectivePermissionGroup(),
        enabled = this.enabled,
        profileIcon = this.profileIcon,
        hasCompletedOnboarding = this.hasCompletedOnboarding,
    )

fun User.effectivePermissionGroup(): Role {
    val priority = listOf(Role.ADMIN, Role.HR, Role.PM, Role.USER)

    return priority.firstOrNull { it in roles }
        ?: Role.USER
}
