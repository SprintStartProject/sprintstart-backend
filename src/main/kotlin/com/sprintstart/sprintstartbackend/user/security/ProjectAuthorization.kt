package com.sprintstart.sprintstartbackend.user.security

import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Project-scoped authorization predicates for use in `@PreAuthorize` expressions.
 *
 * Registered under the bean name `projectAuth` so routes can declare their access rule inline, for
 * example `@PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")`. This relies
 * on `Authentication.name` being the Keycloak subject, which is also `User.authId`.
 *
 * The manager foreign key on the project — not the global `PM` realm role — is the authority for
 * "may manage this project". A user holding the `PM` role manages exactly those projects they are
 * assigned to as manager, and nothing else.
 *
 * Note that every `@WebMvcTest` slice covering a route that uses one of these expressions must
 * provide a mock for this bean, otherwise the expression fails to resolve at request time.
 */
@Component("projectAuth")
class ProjectAuthorization(
    private val projectRepository: ProjectRepository,
    private val assignmentRepository: ProjectUserAssignmentRepository,
    private val userRepository: UserRepository,
) {
    /**
     * Checks whether the authenticated principal holds the global admin role.
     *
     * @param authentication The current authentication.
     * @return `true` when the principal has `ROLE_ADMIN`.
     */
    fun isAdmin(authentication: Authentication): Boolean {
        return authentication.authorities.any { it.authority == ROLE_ADMIN }
    }

    /**
     * Checks whether the authenticated principal is the assigned manager of a project.
     *
     * @param authentication The current authentication.
     * @param projectId Project identifier.
     * @return `true` when the project exists and its manager is the principal.
     */
    @Transactional(readOnly = true)
    fun isManager(authentication: Authentication, projectId: UUID): Boolean {
        return projectRepository
            .findManagerAuthId(projectId)
            .map { it == authentication.name }
            .orElse(false)
    }

    /**
     * Checks whether the authenticated principal may manage a project.
     *
     * Admins may manage every project; everyone else only the projects they are assigned to as
     * manager.
     *
     * @param authentication The current authentication.
     * @param projectId Project identifier.
     * @return `true` when the principal may manage the project.
     */
    @Transactional(readOnly = true)
    fun canManageProject(authentication: Authentication, projectId: UUID): Boolean {
        return isAdmin(authentication) || isManager(authentication, projectId)
    }

    /**
     * Checks whether the authenticated principal may read a project's data.
     *
     * Managers implicitly have read access, so access never depends on a membership row being kept
     * in sync with the manager assignment.
     *
     * @param authentication The current authentication.
     * @param projectId Project identifier.
     * @return `true` when the principal may access the project.
     */
    @Transactional(readOnly = true)
    fun canAccessProject(authentication: Authentication, projectId: UUID): Boolean {
        return canManageProject(authentication, projectId) || isMember(authentication, projectId)
    }

    /**
     * Checks whether the authenticated principal is assigned to a project as a member.
     *
     * @param authentication The current authentication.
     * @param projectId Project identifier.
     * @return `true` when a project assignment exists for the principal.
     */
    private fun isMember(authentication: Authentication, projectId: UUID): Boolean {
        return userRepository
            .findByAuthId(authentication.name)
            .map { user -> assignmentRepository.findByProjectIdAndUserId(projectId, user.id) != null }
            .orElse(false)
    }

    private companion object {
        const val ROLE_ADMIN = "ROLE_ADMIN"
    }
}
