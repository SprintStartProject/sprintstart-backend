package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import java.util.UUID

/**
 * @property projectIds Every project the user is assigned to. Path generation is project-scoped,
 * so it needs exactly one; the onboarding service rejects anything else rather than guessing.
 */
data class UserOnboardingProfile(
    val id: UUID,
    val projectIds: Set<UUID>,
    val projectRoles: List<ProjectRoleDto>,
    val skills: List<UserSkillDto> = emptyList(),
)
