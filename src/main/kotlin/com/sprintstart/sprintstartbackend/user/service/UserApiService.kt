package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.UserOnboardingProfile
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.mapper.toUserApiDto
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

/**
 * Service implementation of the user API used by other modules.
 *
 * Provides a small module-facing adapter over the user repository without exposing
 * controller DTOs or internal user service workflows.
 */
@Service
class UserApiService(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
) : UserApi {
    /**
     * Checks whether a user with the given identifier exists.
     *
     * @param id The unique identifier of the user to check.
     * @return `true` if a user with the given identifier exists, otherwise `false`.
     */
    @Transactional(readOnly = true)
    @Tracked("Checking if user exists")
    override fun exists(id: UUID): Boolean {
        return userRepository.existsById(id)
    }

    /**
     * Resolves the internal user ID for an external authentication identifier.
     *
     * @param authId External authentication identifier.
     * @return The matching user ID when present.
     */
    @Transactional
    @Tracked("Resolving user ID by auth ID")
    override fun getUserIdByAuthId(authId: String): Optional<UUID> {
        return userRepository.findIdByAuthId(authId)
    }

    /**
     * Retrieves a user by their external authentication identifier.
     *
     * @param authId The external authentication identifier of the user.
     * @return A data transfer object (DTO) representing the user.
     * @throws NoSuchElementException if no user is found with the provided authentication identifier.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving user by auth ID")
    override fun getUserByAuthId(authId: String): UserDto {
        val user = userRepository.findByAuthId(authId).orElseThrow {
            NoSuchElementException("User with id $authId not found")
        }
        return user.toUserApiDto()
    }

    /**
     * Searches for users based on the provided filters and pagination information.
     *
     * @param search An optional search string used to filter users by username, first name, or last name.
     *               If null or blank, this filter is ignored.
     * @param roleIds An optional list of role IDs used to filter users who are associated with specific project roles.
     *                If null or empty, this filter is ignored.
     * @param projectIds An optional list of project IDs used to filter users who are associated with specific projects.
     *                   If null or empty, this filter is ignored.
     * @param pageable The pagination information used to control the page size and number for the results.
     * @return A paginated list of users matching the provided filters, represented as a page of UserDto objects.
     */
    @Transactional(readOnly = true)
    @Tracked("Searching for users")
    override fun searchUsers(
        search: String?,
        roleIds: List<UUID>?,
        projectIds: List<UUID>?,
        pageable: Pageable,
    ): Page<UserDto> {
        val spec = Specification<User> { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            if (!search.isNullOrBlank()) {
                val searchPattern = "%${search.lowercase()}%"
                val usernameMatch = cb.like(cb.lower(root.get("username")), searchPattern)
                val firstnameMatch = cb.like(cb.lower(root.get("firstname")), searchPattern)
                val lastnameMatch = cb.like(cb.lower(root.get("lastname")), searchPattern)
                predicates.add(cb.or(usernameMatch, firstnameMatch, lastnameMatch))
            }

            if (!roleIds.isNullOrEmpty()) {
                val projectRolesJoin = root.join<User, ProjectRole>("projectRoles", JoinType.INNER)
                predicates.add(projectRolesJoin.get<UUID>("id").`in`(roleIds))
            }

            if (!projectIds.isNullOrEmpty()) {
                val projectJoin = root.join<User, Project>("projects", JoinType.INNER)
                predicates.add(projectJoin.get<UUID>("id").`in`(projectIds))
            }

            // query.distinct(true) because join might return multiple rows for the same user
            query.distinct(true)

            if (predicates.isEmpty()) null else cb.and(*predicates.toTypedArray())
        }

        return userRepository.findAll(spec, pageable).map { it.toUserApiDto() }
    }

    /**
     * Retrieves a list of users based on their unique identifiers.
     *
     * @param ids A list of unique identifiers (UUIDs) representing the users to be fetched.
     * @return A list of UserDto objects corresponding to the provided identifiers.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving users by IDs")
    override fun getUsersByIds(ids: List<UUID>): List<UserDto> {
        return userRepository.findAllById(ids).map { it.toUserApiDto() }
    }

    /**
     * Returns the onboarding-relevant profile for a user identified by auth ID.
     *
     * @param authId External authentication identifier.
     * @return The user's onboarding profile when present.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding profile by auth ID")
    override fun getOnboardingProfileByAuthId(authId: String): Optional<UserOnboardingProfile> =
        userRepository.findByAuthId(authId).map { user ->
            UserOnboardingProfile(
                id = user.id,
                projectRoles = user.projectRoles.map { role ->
                    ProjectRoleDto(
                        roleId = role.id,
                        name = role.name,
                        description = role.description,
                    )
                },
                skills = user.skillAssessments.map { assessment ->
                    UserSkillDto(
                        skillId = assessment.skill.id,
                        name = assessment.skill.name,
                        level = assessment.level.name,
                    )
                },
            )
        }

    /**
     * Marks the given user's onboarding as completed.
     *
     * Idempotent: when the user is already flagged nothing is written. The change is
     * flushed by the surrounding transaction on the managed entity.
     *
     * @param userId Internal SprintStart user identifier.
     * @throws NoSuchElementException if no user is found with the provided identifier.
     */
    @Transactional
    @Tracked("Marking user onboarding as completed")
    override fun markOnboardingCompleted(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow {
            NoSuchElementException("User with id $userId not found")
        }
        if (!user.hasCompletedOnboarding) {
            user.hasCompletedOnboarding = true
        }
    }

    /**
     * Determines whether a user with the given authentication identifier has access to the specified project.
     *
     * Access is granted to admins, to members of the project, and to the project's assigned manager.
     * Managers are covered explicitly rather than relying on their membership row, so a manager
     * never loses access to a project they are responsible for.
     *
     * @param authId The external authentication identifier of the user.
     * @param projectId The unique identifier of the project to check access for.
     * @return `true` if the user has access to the project, otherwise `false`.
     */
    @Transactional(readOnly = true)
    @Tracked("Checking if user has access to project")
    override fun userHasAccessToProject(authId: String, projectId: UUID): Boolean {
        val user = userRepository.findByAuthId(authId).orElse(null)
            ?: return false
        if (Role.ADMIN in user.roles) {
            return true
        }
        if (projectId in user.projects.map { it.id }) {
            return true
        }

        return projectRepository
            .findManagerAuthId(projectId)
            .map { it == authId }
            .orElse(false)
    }
}
