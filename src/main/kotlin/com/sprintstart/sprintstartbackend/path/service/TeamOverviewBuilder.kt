package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.path.model.response.path.CurrentPhaseDto
import com.sprintstart.sprintstartbackend.path.model.response.path.CurrentStepDto
import com.sprintstart.sprintstartbackend.path.model.response.path.SkillDto
import com.sprintstart.sprintstartbackend.path.model.response.path.SkipRequestDto
import com.sprintstart.sprintstartbackend.path.model.response.path.TeamOverviewUserDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto

/** Builds the team overview's row for one person: who they are and where they are on their path. */
internal class TeamOverviewBuilder(
    private val onboardingPositionReader: OnboardingPositionReader,
) {
    /**
     * Builds a TeamOverviewUserDto from a UserDto and an optional OnboardingPath.
     *
     * Progress and the active step come from [OnboardingPositionReader] rather than living here: the
     * escalation inbox needs the same two answers, and "which step is this person on" told two
     * different ways is worse than not telling it at all.
     *
     * @param userDto The user DTO.
     * @param path The user's onboarding path.
     * @return The built TeamOverviewUserDto.
     */
    fun build(userDto: UserDto, path: OnboardingPath?): TeamOverviewUserDto {
        val active = path?.let(onboardingPositionReader::activeStepIn)

        return TeamOverviewUserDto(
            userId = userDto.id.toString(),
            firstname = userDto.firstname,
            lastname = userDto.lastname,
            profileIcon = userDto.profileIcon,
            projectIds = userDto.projects,
            roles = userDto.projectRoles,
            skills = userDto.skills.map { skillOf(it) },
            progressPercentage = path?.let(onboardingPositionReader::progressOf) ?: 0.0,
            currentPhase = active?.let { CurrentPhaseDto(title = it.phase.title) },
            currentStep = active?.let { currentStepOf(it) },
            hasFeedback = false,
        )
    }

    private fun skillOf(skill: UserSkillDto): SkillDto =
        SkillDto(id = skill.skillId.toString(), name = skill.name, roleId = null, level = skill.level)

    /**
     * The step a person is on. The skip stays here rather than with the reader: reviewing a skip needs
     * the step entity, which is this overview's business rather than the reader's.
     */
    private fun currentStepOf(active: ActiveStep): CurrentStepDto {
        val skip = active.step.skips.lastOrNull()?.let { request ->
            SkipRequestDto(
                id = request.id.toString(),
                stepId = request.step.id.toString(),
                reason = request.reason,
                status = request.status.name,
                reviewComment = request.reviewComment,
                reviewedAt = request.resolvedAt,
            )
        }

        return CurrentStepDto(
            id = active.step.id.toString(),
            title = active.step.title,
            startedAt = active.step.startedAt,
            skip = skip,
        )
    }
}
