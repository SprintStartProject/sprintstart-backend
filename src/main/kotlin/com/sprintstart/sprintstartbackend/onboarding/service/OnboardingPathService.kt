package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
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

    @Transactional(readOnly = true)
    fun getTeamOverview(
        search: String?,
        roleIds: List<UUID>?,
        projectIds: List<UUID>?,
        sortBy: String,
        pageable: org.springframework.data.domain.Pageable,
    ): org.springframework.data.domain.Page<
        com.sprintstart.sprintstartbackend.onboarding.model.response.path.TeamOverviewUserDto,
    > {
        val userIdPage = onboardingPathRepository.findUserIdsForOverview(search, roleIds, projectIds, sortBy, pageable)
        val userDtos = userApi.getUsersByIds(userIdPage.content).associateBy { it.id }

        return userIdPage.map { userId ->
            val userDto =
                userDtos[userId] ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found")
            val path = onboardingPathRepository.findOnboardingPathByUserId(userDto.id).orElse(null)

            buildTeamOverviewUserDto(userDto, path)
        }
    }

    @Transactional(readOnly = true)
    fun getTeamOverviewForMe(authId: String):
        com.sprintstart.sprintstartbackend.onboarding.model.response.path.TeamOverviewUserDto {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
        val userDto = userApi.getUsersByIds(listOf(userId)).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found")
        val path = onboardingPathRepository.findOnboardingPathByUserId(userDto.id).orElse(null)

        return buildTeamOverviewUserDto(userDto, path)
    }

    private fun buildTeamOverviewUserDto(
        userDto: com.sprintstart.sprintstartbackend.user.external.dto.UserDto,
        path: com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath?,
    ): com.sprintstart.sprintstartbackend.onboarding.model.response.path.TeamOverviewUserDto {
        var progressPercentage = 0.0
        var currentPhase: String? = null
        var currentStepDto: com.sprintstart.sprintstartbackend.onboarding.model.response.path.CurrentStepDto? = null

        if (path != null) {
            val totalSteps = path.phases.sumOf { it.steps.size }
            val completedSteps = path.phases.sumOf { phase ->
                phase.steps.count {
                    it.status == com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus.FINISHED ||
                        it.status == com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus.SKIPPED
                }
            }
            if (totalSteps > 0) {
                progressPercentage = completedSteps.toDouble() / totalSteps.toDouble()
            }

            val sortedPhases = path.phases.sortedBy { it.position }
            for (phase in sortedPhases) {
                val sortedSteps = phase.steps.sortedBy { it.position }
                val waitingStep = sortedSteps.firstOrNull {
                    it.status == com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus.WAITING
                }
                if (waitingStep != null) {
                    currentPhase = phase.title

                    val skipReq = waitingStep.skipRequest?.let { req ->
                        com.sprintstart.sprintstartbackend.onboarding.model.response.path.SkipRequestDto(
                            id = req.id.toString(),
                            stepId = req.stepId.toString(),
                            reason = req.reason,
                            status = req.status.name,
                            reviewComment = req.reviewComment,
                            reviewedAt = req.reviewedAt,
                        )
                    }

                    currentStepDto = com.sprintstart.sprintstartbackend.onboarding.model.response.path.CurrentStepDto(
                        id = waitingStep.id.toString(),
                        title = waitingStep.title,
                        startedAt = waitingStep.startedAt,
                        skip = skipReq,
                    )
                    break
                }
            }
        }

        val currentPhaseDto = currentPhase?.let {
            com.sprintstart.sprintstartbackend.onboarding.model.response.path
                .CurrentPhaseDto(title = it)
        }

        val userSkills = userDto.skills.map { skill ->
            com.sprintstart.sprintstartbackend.onboarding.model.response.path.SkillDto(
                id = skill.skillId.toString(),
                name = skill.name,
                roleId = null,
                level = skill.level,
            )
        }

        return com.sprintstart.sprintstartbackend.onboarding.model.response.path.TeamOverviewUserDto(
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
