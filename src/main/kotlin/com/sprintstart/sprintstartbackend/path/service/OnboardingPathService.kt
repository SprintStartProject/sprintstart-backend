package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintApi
import com.sprintstart.sprintstartbackend.path.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.path.TeamOverviewUserDto
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.path.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.path.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.ids.BlueprintId
import com.sprintstart.sprintstartbackend.user.external.UserApi
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
@Suppress("TooManyFunctions")
class OnboardingPathService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val questionAttemptRepository: QuestionAttemptRepository,
    private val userApi: UserApi,
    private val onboardingPositionReader: OnboardingPositionReader,
    private val blueprintApi: BlueprintApi,
) {
    private val teamOverview = TeamOverviewBuilder(onboardingPositionReader)

    /**
     * Where an existing path came from.
     *
     * @property builtFromProjectId The project whose blueprint the path was copied from; `null` for
     * a path built from a system-wide blueprint, or one whose blueprint no longer exists.
     */
    data class PathOrigin(
        val builtFromProjectId: UUID?,
    )

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
     * The origin of [userId]'s onboarding path, or `null` when they have none.
     *
     * Rebuilding or deleting a path is decided by who manages the project it was built from, so the
     * rules about replacing a path need this rather than the path itself.
     *
     * @param userId Identifier of the path's owner.
     * @return Where the path came from, or `null` without a path.
     */
    @Transactional(readOnly = true)
    @Tracked("Resolving the origin of an onboarding path")
    fun findPathOrigin(userId: UUID): PathOrigin? =
        onboardingPathRepository
            .findByUserId(userId)
            .map { path ->
                PathOrigin(
                    builtFromProjectId = path.blueprintId?.let { blueprintApi.projectIdOf(BlueprintId(it))?.value },
                )
            }.orElse(null)

    /**
     * Refuses [callerAuthId] replacing or deleting [userId]'s path when that is not theirs to decide.
     *
     * A user's first path is theirs to build, so without a path there is nothing to check. An
     * existing one holds the user's progress, so throwing it away is a manager's call:
     *
     * - **Rebuilding** from [projectId] needs the caller to manage [projectId] and the owner to be
     *   assigned to it. Where the old path came from does not matter: a user has one path across
     *   all their projects, and a member who joins project B with a path from A must be able to get
     *   one for B from B's manager -- otherwise only an admin could ever move them.
     * - **Deleting** replaces the path with nothing, so it is decided by the project the path was
     *   built from: the caller must manage that project. A path whose origin is unknown (its
     *   blueprint was deleted, or it came from a system-wide one) has no manager to vouch for it,
     *   so only an admin may delete it.
     *
     * @param callerAuthId The acting user's auth ID.
     * @param userId The path's owner.
     * @param projectId The project a rebuild builds from, or `null` for a deletion.
     * @throws ResponseStatusException `403` when the caller may not.
     */
    @Transactional(readOnly = true)
    @Tracked("Checking whether an onboarding path may be replaced")
    fun requireMayReplacePath(callerAuthId: String, userId: UUID, projectId: UUID?) {
        val origin = findPathOrigin(userId) ?: return
        if (projectId != null) {
            requireMayRebuildFrom(callerAuthId, userId, projectId)
            return
        }
        val builtFrom = origin.builtFromProjectId
        val mayDelete = if (builtFrom == null) {
            userApi.isAdmin(callerAuthId)
        } else {
            userApi.canManageProject(callerAuthId, builtFrom)
        }
        if (!mayDelete) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                if (builtFrom == null) {
                    "Only an admin can delete an onboarding path whose project is unknown"
                } else {
                    "The onboarding path was built from a project the caller does not manage"
                },
            )
        }
    }

    private fun requireMayRebuildFrom(callerAuthId: String, userId: UUID, projectId: UUID) {
        if (!userApi.canManageProject(callerAuthId, projectId)) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the project manager can rebuild an existing onboarding path",
            )
        }
        val ownerAssigned = userApi
            .getAuthIdByUserId(userId)
            .flatMap { authId -> userApi.getOnboardingProfileByAuthId(authId) }
            .map { profile -> projectId in profile.projectIds }
            .orElse(false)
        if (!ownerAssigned) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "The path's owner is not assigned to project: $projectId",
            )
        }
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
     * Whether this user has an onboarding path at all.
     *
     * A row count rather than a read: the buddy asks this once per turn to decide whether to mount
     * its path tool, and loading every phase and step to answer "is there one" would put the
     * heaviest read in the module behind a question about its own existence.
     */
    fun hasPath(userId: UUID): Boolean = onboardingPathRepository.existsByUserId(userId)

    /**
     * A user's path as they see it, by user id, or `null` when they have none.
     *
     * The same read as [getOnboardingPathForMe] -- question attempts included, so the statuses are
     * the ones on their screen -- reached by user id and without the 404, for the buddy and for a
     * reviewer looking at somebody's path. Having no path yet is an ordinary state for a caller that
     * is deciding what to say about it, not an error to catch: the buddy needs "there is nothing
     * here" as an answer, and a thrown 404 would make every reader wrap this in a try.
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

    /**
     * One phase of the user's own path that generation left empty (or that failed), in the hire's
     * form, or `null`.
     *
     * [findPathForUserId] leaves these out of `phases` -- a hire should not be walked through a phase
     * with nothing in it -- and reports them only as `generationIssues`. They are exactly what the
     * buddy's `add_path_step` exists to repair, though, so it needs them by id: resolved through the
     * owner's own path like every other lookup, so an id from anywhere else is not found.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving a hidden onboarding phase by user id")
    fun findHiddenPhaseForUserId(userId: UUID, phaseId: UUID): GetOnboardingPhaseForUserResponse? =
        onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .map { path ->
                path.phases
                    .firstOrNull { it.id == phaseId && it.generationStatus.isHiddenFromUser() }
                    ?.toGetForUserResponse()
            }.orElse(null)

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
            teamOverview.build(user, paths[user.id])
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

        return teamOverview.build(userDto, path)
    }
}
