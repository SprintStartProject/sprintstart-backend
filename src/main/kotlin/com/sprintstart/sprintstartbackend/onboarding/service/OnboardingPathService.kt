package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.CurrentPhaseDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.CurrentStepDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.SkillDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.SkipRequestDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.TeamOverviewUserDto
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
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
    private val questionAttemptRepository: QuestionAttemptRepository,
    private val userApi: UserApi,
    private val onboardingPositionReader: OnboardingPositionReader,
) {
//  ========================== Methods for users ==========================

    /**
     * Returns the onboarding path for the authenticated user.
     *
     * The user is resolved from the external auth ID before the path lookup is performed.
     * The response is enriched with the user's question attempt history: passed and
     * attempted question IDs are loaded from [QuestionAttemptRepository] and drive the
     * per-phase lock state and per-question status of the returned path.
     *
     * @param authId External authentication identifier.
     * @return The authenticated user's onboarding path, annotated with the user's attempt state.
     * @throws ResponseStatusException When the user or onboarding path does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding path for user")
    fun getOnboardingPathForMe(authId: String): GetOnboardingPathForUserResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

        val path = onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No path found for user with id: $userId") }

        return path.toGetForUserResponse(
            passedQuestionIds = questionAttemptRepository.findPassedQuestionIdsByUserId(userId).toSet(),
            attemptedQuestionIds = questionAttemptRepository.findAttemptedQuestionIdsByUserId(userId).toSet(),
        )
    }

    /**
     * Deletes the onboarding path owned by the authenticated user.
     *
     * @param authId External authentication identifier.
     * @throws ResponseStatusException When the user does not exist.
     */
    @Tracked("Deleting onboarding path for user")
    fun deleteOnboardingPathForMe(authId: String) {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

        onboardingPathRepository.deleteByUserId(userId)
    }

    /**
     * A user's path as they see it, by user id, or `null` when they have none.
     *
     * The same read as [getOnboardingPathForMe] -- question attempts included, so the statuses are
     * the ones on their screen -- reached by user id, for a reviewer looking at somebody's path.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding path by user id")
    fun findPathForUserId(userId: UUID): GetOnboardingPathForUserResponse? =
        onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .map { path ->
                path.toGetForUserResponse(
                    passedQuestionIds = questionAttemptRepository.findPassedQuestionIdsByUserId(userId).toSet(),
                    attemptedQuestionIds = questionAttemptRepository.findAttemptedQuestionIdsByUserId(userId).toSet(),
                )
            }.orElse(null)

//  ========================== Methods for admins ==========================

    /**
     * Returns one user's onboarding path, for a PM, HR or admin looking at it.
     *
     * ### Why this is the hire-shaped response
     *
     * It used to answer with the summary shape: phases and nothing inside them. Every reviewer
     * screen then rebuilt the path client-side — one request per phase for its steps — and none of
     * them could get the questions at all, because no endpoint hands out a *user's* questions with
     * their status. So the team page crashed the moment questions became first-class members of a
     * phase: it read `phase.questions` on phases that had never carried any.
     *
     * A PM opening somebody's onboarding wants the path *as that person has it* — the same lock
     * states, the same step statuses, the same questions with the same passed/retry marks. That is
     * exactly [toGetForUserResponse], and computing it here rather than in three clients is what
     * makes the reviewer's view and the hire's view incapable of disagreeing.
     *
     * One consequence worth naming: phases whose generation produced nothing are reported in
     * `generationIssues` rather than listed, here as well. A reviewer sees what the hire sees, which
     * includes seeing that something came back empty.
     *
     * @param userId Identifier of the user whose path should be loaded.
     * @return The user's onboarding path, annotated with that user's own attempt state.
     * @throws ResponseStatusException When the user or onboarding path does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding path for user")
    fun getOnboardingPathByUserId(userId: UUID): GetOnboardingPathForUserResponse {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }

        return findPathForUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No onboarding path found for: $userId")
    }

    /**
     * Deletes the onboarding path associated with a specific user.
     *
     * @param userId The ID of the user whose path should be deleted.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no user exists with [userId].
     */
    @Tracked("Deleting onboarding path for user")
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
    @Tracked("Listing onboarding paths of team")
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
    @Tracked("Retrieving team overview for user")
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
            // Both derivations come from [OnboardingPositionReader] rather than living here: the
            // escalation inbox needs the same two answers, and "which step is this person on" told
            // two different ways is worse than not telling it at all.
            progressPercentage = onboardingPositionReader.progressOf(path)

            onboardingPositionReader.activeStepIn(path)?.let { active ->
                currentPhase = active.phase.title

                // Stays here: reviewing a skip needs the step entity, which is this service's
                // business rather than the reader's.
                val skipReq = active.step.skips.lastOrNull()?.let { req ->
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
                    id = active.step.id.toString(),
                    title = active.step.title,
                    startedAt = active.step.startedAt,
                    skip = skipReq,
                )
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
            profileIcon = userDto.profileIcon,
            projectIds = userDto.projects,
            roles = userDto.projectRoles,
            skills = userSkills,
            progressPercentage = progressPercentage,
            currentPhase = currentPhaseDto,
            currentStep = currentStepDto,
            hasFeedback = false,
        )
    }
}
