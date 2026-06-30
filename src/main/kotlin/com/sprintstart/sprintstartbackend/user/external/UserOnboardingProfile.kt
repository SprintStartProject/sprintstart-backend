package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import java.util.UUID

data class UserOnboardingProfile(
    val id: UUID,
    val projectRoles: List<ProjectRoleDto>,
    val skills: List<UserSkillDto> = emptyList(),
)
