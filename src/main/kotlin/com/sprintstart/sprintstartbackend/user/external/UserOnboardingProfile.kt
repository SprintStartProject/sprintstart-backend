package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import java.util.UUID

/**
 * @property id the user's onboarding profile id.
 * @property projectIds Every project the user is assigned to. Path generation is project-scoped:
 * the caller picks one of these; the service rejects a project the user is not assigned to.
 * @property projectRoles The roles the user holds, keyed by the project they hold them on. Roles
 * are scoped to the membership, so the role relevant to a given project's onboarding path is the
 * one under that project's key — not a person-wide list that would mix roles from other projects.
 * @property skills The skills this user has.
 */
data class UserOnboardingProfile(
    val id: UUID,
    val projectIds: Set<UUID>,
    val projectRoles: Map<UUID, List<ProjectRoleDto>>,
    val skills: List<UserSkillDto> = emptyList(),
)
