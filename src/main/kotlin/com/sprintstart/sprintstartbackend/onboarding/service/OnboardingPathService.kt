package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.CurrentPhaseDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.CurrentStepDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.SkillDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.SkipRequestDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.TeamOverviewUserDto
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Provides onboarding path read and delete operations.
 *
 * User-scoped operations resolve the current user through [UserApi] before accessing
 * the owning onboarding path. Admin-scoped operations address a user directly by UUID.
 */
@Service
class OnboardingPathService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val userApi: UserApi,
) {
//  ========================== Methods for users ==========================

    /**
     * Returns the onboarding path for the authenticated user.
     *
     * The user is resolved from the external auth ID before the path lookup is performed.
     *
     * @param authId External authentication identifier.
     * @return The authenticated user's onboarding path.
     * @throws ResponseStatusException When the user or onboarding path does not exist.
     */
    @Transactional(readOnly = true)
    fun getOnboardingPathForMe(authId: String): GetOnboardingPathForUserResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

        return onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No path found for user with id: $userId") }
            .toGetForUserResponse()
    }

    /**
     * Deletes the onboarding path owned by the authenticated user.
     *
     * @param authId External authentication identifier.
     * @throws ResponseStatusException When the user does not exist.
     */
    fun deleteOnboardingPathForMe(authId: String) {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

        onboardingPathRepository.deleteByUserId(userId)
    }

//  ========================== Methods for admins ==========================

    /**
     * Returns the onboarding path for a specific user.
     *
     * The target user must exist before the path lookup is attempted.
     *
     * @param userId Identifier of the user whose path should be loaded.
     * @return The user's onboarding path.
     * @throws ResponseStatusException When the user or onboarding path does not exist.
     */
    fun getOnboardingPathByUserId(userId: UUID): GetOnboardingPathResponse {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }

        return onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No onboarding path found with for: $userId") }
            .toGetResponse()
    }

    /**
     * Deletes the onboarding path associated with a specific user.
     *
     * @param userId The ID of the user whose path should be deleted.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no user exists with [userId].
     */
    fun deleteOnboardingPathByUserId(userId: UUID) {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }
        onboardingPathRepository.deleteByUserId(userId)
    }

    /**
     * Returns a paginated overview of the team's onboarding paths.
     *
     * @param search Optional search string to filter users.
     * @param roleIds Optional list of project role UUIDs to filter users.
     * @param projectIds Optional list of project UUIDs to filter users.
     * @param sortBy The sorting criteria.
     * @param pageable Pagination parameters.
     * @return A paginated list of team overview user DTOs.
     */
    @Transactional(readOnly = true)
    fun getTeamOverview(
        search: String?,
        roleIds: List<UUID>?,
        projectIds: List<UUID>?,
        sortBy: String,
        pageable: Pageable,
    ): Page<TeamOverviewUserDto> {
        val allUsersPage = userApi.searchUsers(search, roleIds, projectIds, Pageable.unpaged())
        val users = allUsersPage.content
        if (users.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }

        val userIds = users.map { it.id }
        val paths = onboardingPathRepository.findByUserIdIn(userIds).associateBy { it.userId }

        val dtos = users.map { user ->
            buildTeamOverviewUserDto(user, paths[user.id])
        }

        val sortedDtos = when (sortBy) {
            "HIGHEST_PROGRESS" -> dtos.sortedWith(
                compareByDescending<TeamOverviewUserDto> { it.progressPercentage }
                    .thenBy { it.lastname }
                    .thenBy { it.firstname },
            )
            "LOWEST_PROGRESS" -> dtos.sortedWith(
                compareBy<TeamOverviewUserDto> { it.progressPercentage }
                    .thenBy { it.lastname }
                    .thenBy { it.firstname },
            )
            else -> dtos.sortedWith(compareBy<TeamOverviewUserDto> { it.lastname }.thenBy { it.firstname })
        }

        val fromIndex = pageable.offset.toInt().coerceAtMost(sortedDtos.size)
        val toIndex = (fromIndex + pageable.pageSize).coerceAtMost(sortedDtos.size)
        val pagedList = sortedDtos.subList(fromIndex, toIndex)

        return PageImpl(pagedList, pageable, sortedDtos.size.toLong())
    }

    /**
     * Returns the team overview for the authenticated user.
     *
     * @param authId External authentication identifier.
     * @return The authenticated user's team overview DTO.
     */
    @Transactional(readOnly = true)
    fun getTeamOverviewForMe(authId: String): TeamOverviewUserDto {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
        val userDto = userApi.getUsersByIds(listOf(userId)).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found")
        val path = onboardingPathRepository.findOnboardingPathByUserId(userDto.id).orElse(null)

        return buildTeamOverviewUserDto(userDto, path)
    }

    /**
     * Builds a TeamOverviewUserDto from a UserDto and an optional OnboardingPath.
     *
     * @param userDto The user DTO.
     * @param path The user's onboarding path.
     * @return The built TeamOverviewUserDto.
     */
    private fun buildTeamOverviewUserDto(
        userDto: UserDto,
        path: OnboardingPath?,
    ): TeamOverviewUserDto {
        var progressPercentage = 0.0
        var currentPhase: String? = null
        var currentStepDto: CurrentStepDto? = null

        if (path != null) {
            val totalSteps = path.phases.sumOf { it.steps.size }
            val completedSteps = path.phases.sumOf { phase ->
                phase.steps.count {
                    it.status == StepStatus.FINISHED ||
                        it.status == StepStatus.SKIPPED
                }
            }
            if (totalSteps > 0) {
                progressPercentage = completedSteps.toDouble() / totalSteps.toDouble()
            }

            val sortedPhases = path.phases.sortedBy { it.position }
            for (phase in sortedPhases) {
                val sortedSteps = phase.steps.sortedBy { it.position }
                val activeStep = sortedSteps.firstOrNull {
                    it.status == StepStatus.WAITING ||
                        it.status == StepStatus.IN_PROGRESS
                }
                if (activeStep != null) {
                    currentPhase = phase.title

                    val skipReq = activeStep.skips.lastOrNull()?.let { req ->
                        SkipRequestDto(
                            id = req.id.toString(),
                            stepId = req.step.id.toString(),
                            reason = req.reason,
                            status = req.status.name,
                            reviewComment = req.reviewComment,
                            reviewedAt = req.resolvedAt,
                        )
                    }

                    currentStepDto = CurrentStepDto(
                        id = activeStep.id.toString(),
                        title = activeStep.title,
                        startedAt = activeStep.startedAt,
                        skip = skipReq,
                    )
                    break
                }
            }
        }

        val currentPhaseDto = currentPhase?.let {
            CurrentPhaseDto(title = it)
        }

        val userSkills = userDto.skills.map { skill ->
            SkillDto(
                id = skill.skillId.toString(),
                name = skill.name,
                roleId = null,
                level = skill.level,
            )
        }

        return TeamOverviewUserDto(
            userId = userDto.id.toString(),
            firstname = userDto.firstname,
            lastname = userDto.lastname,
            project = userDto.project,
            roles = userDto.projectRoles,
            skills = userSkills,
            progressPercentage = progressPercentage,
            currentPhase = currentPhaseDto,
            currentStep = currentStepDto,
            hasFeedback = false,
        )
    }
}
