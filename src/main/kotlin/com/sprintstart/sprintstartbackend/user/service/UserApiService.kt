package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.User
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
) : UserApi {
    /**
     * Checks whether a user with the given identifier exists.
     *
     * @param id The unique identifier of the user to check.
     * @return `true` if a user with the given identifier exists, otherwise `false`.
     */
    @Transactional(readOnly = true)
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
    override fun getUserIdByAuthId(authId: String): Optional<UUID> {
        return userRepository.findIdByAuthId(authId)
    }

    @Transactional(readOnly = true)
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
                val projectJoin = root.join<User, Project>("project", JoinType.INNER)
                predicates.add(projectJoin.get<UUID>("id").`in`(projectIds))
            }

            // query.distinct(true) because join might return multiple rows for the same user
            query.distinct(true)

            if (predicates.isEmpty()) null else cb.and(*predicates.toTypedArray())
        }

        return userRepository.findAll(spec, pageable).map { user ->
            UserDto(
                id = user.id,
                username = user.username,
                firstname = user.firstname,
                lastname = user.lastname,
                avatarUrl = user.avatarUrl,
                workingArea = user.workingArea.name,
                project = user.project?.let {
                    ProjectDto(
                        projectId = it.id,
                        name = it.name,
                        description = it.description,
                    )
                },
                skills = user.skillAssessments.map { assessment ->
                    UserSkillDto(
                        skillId = assessment.skill.id,
                        name = assessment.skill.name,
                        level = assessment.level.name,
                    )
                },
                projectRoles = user.projectRoles.map { role ->
                    ProjectRoleDto(
                        roleId = role.id,
                        name = role.name,
                        description = role.description,
                    )
                },
            )
        }
    }

    @Transactional(readOnly = true)
    override fun getUsersByIds(ids: List<UUID>): List<UserDto> {
        return userRepository.findAllById(ids).map { user ->
            UserDto(
                id = user.id,
                username = user.username,
                firstname = user.firstname,
                lastname = user.lastname,
                avatarUrl = user.avatarUrl,
                workingArea = user.workingArea.name,
                project = user.project?.let {
                    ProjectDto(
                        projectId = it.id,
                        name = it.name,
                        description = it.description,
                    )
                },
                skills = user.skillAssessments.map { assessment ->
                    UserSkillDto(
                        skillId = assessment.skill.id,
                        name = assessment.skill.name,
                        level = assessment.level.name,
                    )
                },
                projectRoles = user.projectRoles.map { role ->
                    ProjectRoleDto(
                        roleId = role.id,
                        name = role.name,
                        description = role.description,
                    )
                },
            )
        }
    }
}
