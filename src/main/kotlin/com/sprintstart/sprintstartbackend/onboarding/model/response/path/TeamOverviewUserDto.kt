package com.sprintstart.sprintstartbackend.onboarding.model.response.path

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto

data class TeamOverviewUserDto(
    val userId: String,
    val firstname: String,
    val lastname: String,
    val project: ProjectDto?,
    val roles: List<ProjectRoleDto>,
    val skills: List<SkillDto>,
    val progressPercentage: Double,
    val currentPhase: CurrentPhaseDto?,
    val currentStep: CurrentStepDto?,
    val hasFeedback: Boolean,
)

data class SkillDto(
    val id: String,
    val name: String,
    val roleId: String?,
    val level: String,
)

data class CurrentPhaseDto(
    val title: String,
)

data class SkipRequestDto(
    val id: String,
    val stepId: String,
    val reason: String,
    val status: String,
    val reviewComment: String?,
    val reviewedAt: java.time.Instant?,
)

data class CurrentStepDto(
    val id: String,
    val title: String,
    val startedAt: java.time.Instant?,
    val skip: SkipRequestDto?,
)
