package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.config.KeycloakRoleMapper
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginSource
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.events.UserCreatedEvent
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.model.request.user.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.request.user.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.request.user.UpdateUserEnabledRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.MyProjectResponse
import com.sprintstart.sprintstartbackend.user.model.response.user.DeleteUserResponse
import com.sprintstart.sprintstartbackend.user.model.response.user.GetUserResponse
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Application service for user profile reads and updates.
 *
 * This service owns user-facing operations within the user module and maps persisted
 * [User] entities to response DTOs for controllers.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val keycloakAdminClient: KeycloakAdminClient,
    private val githubLoginService: GithubLoginService,
    private val jiraDisplayNameService: JiraDisplayNameService,
) {
    /**
     * Returns all persisted users.
     *
     * @return All users mapped to controller response DTOs.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all users")
    fun getAllUsers(): List<GetUserResponse> =
        userRepository.findAll().map { it.toGetResponse() }

    /**
     * Returns the user identified by the authentication subject. If the user does not exist,
     * they are provisioned Just-In-Time (JIT) using claims from the JWT.
     *
     * @param jwt The authenticated JWT containing the caller subject and claims.
     * @return The matching user.
     */
    @Transactional
    @Tracked("Retrieving authenticated user")
    fun getMe(jwt: Jwt): GetUserResponse {
        val tokenRoles = jwt.realmRoles()

        val user = userRepository.findByAuthId(jwt.subject).orElseGet {
            val newUser = User(
                authId = jwt.subject,
                username = jwt.getClaimAsString("preferred_username") ?: jwt.subject,
                email = jwt.getClaimAsString("email"),
                firstname = jwt.getClaimAsString("given_name") ?: "Unknown",
                lastname = jwt.getClaimAsString("family_name") ?: "User",
                roles = tokenRoles.ifEmpty { mutableSetOf(Role.USER) }.toMutableSet(),
            )
            val savedUser = userRepository.save(newUser)
            eventPublisher.publishEvent(UserCreatedEvent(savedUser.id))
            savedUser
        }

        // Keycloak is authoritative for permission groups. Without this the local
        // projection only ever tracks roles that arrived via a REALM_ROLE_MAPPING
        // event, so a user created any other way (realm import, admin console while
        // the backend was down) stays USER forever and loses every PM/HR/admin route.
        if (tokenRoles.isNotEmpty() && tokenRoles != user.roles) {
            user.roles.retainAll(tokenRoles)
            user.roles.addAll(tokenRoles)
            userRepository.save(user)
        }

        return user.toGetResponse()
    }

    /**
     * Partially updates the authenticated user's editable fields.
     *
     * Omitted fields remain unchanged.
     *
     * @param authId External authentication identifier from the JWT subject.
     * @param request Partial update payload.
     * @return The updated user.
     * @throws ResponseStatusException When no user exists for the given auth ID.
     */
    @Transactional
    @Tracked("Updating authenticated user")
    fun patchMe(authId: String, request: PatchMeRequest): GetUserResponse {
        val user = findByAuthId(authId)

        keycloakAdminClient.updateUserProfile(
            authId = user.authId,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
            projectIds = request.projectsId,
        )

        request.email?.let { user.email = it }
        request.firstName?.let { user.firstname = it }
        request.lastName?.let { user.lastname = it }
        request.profileIcon?.let { user.profileIcon = it }
        request.githubLogin?.let { githubLoginService.apply(user, it, GithubLoginSource.SELF_DECLARED) }
        request.jiraDisplayName?.let { jiraDisplayNameService.apply(user, it) }

        return userRepository.save(user).toGetResponse()
    }

    /**
     * Returns the projects the authenticated user is assigned to.
     *
     * Used to scope actions that require a project (e.g. connecting a GitHub
     * repository) to users who aren't administrators and therefore can't use
     * the admin project listing.
     *
     * @param authId External authentication identifier from the JWT subject.
     * @return The user's assigned projects.
     * @throws ResponseStatusException When no user exists for the given auth ID.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving authenticated user's projects")
    fun getMyProjects(authId: String): List<MyProjectResponse> =
        findByAuthId(authId).projects.map { MyProjectResponse(id = it.id, name = it.name) }

    /**
     * Partially updates an administrator-selected user.
     *
     * Profile fields and permission group changes are first forwarded to Keycloak.
     * The local projection is then updated for fields owned by this backend. Permission
     * groups are not mutated locally here because Keycloak role events synchronize the
     * local role snapshot asynchronously.
     *
     * @param id Identifier of the user to update.
     * @param request Partial administrative update payload.
     * @return The updated user projection, including the requested permission group when changed.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional
    @Tracked("Updating user")
    fun patchAdminUserById(id: UUID, request: PatchUserRequest): GetUserResponse {
        val user = findById(id)

        keycloakAdminClient.updateUserProfile(
            authId = user.authId,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
            projectIds = request.projectsId,
        )
        request.permissionGroup?.let { keycloakAdminClient.setPermissionGroup(user.authId, it) }

        request.email?.let { user.email = it }
        request.firstName?.let { user.firstname = it }
        request.lastName?.let { user.lastname = it }
        request.githubLogin?.let { githubLoginService.apply(user, it, GithubLoginSource.PM_CONFIRMED) }
        request.jiraDisplayName?.let { jiraDisplayNameService.apply(user, it) }

        // Todo: map this to PatchResponse
        val response = userRepository.save(user).toGetResponse()
        return request.permissionGroup?.let { response.copy(permissionGroup = it) } ?: response
    }

    /**
     * Returns a single user by UUID.
     *
     * @param id Identifier of the user to load.
     * @return The matching user.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving user by id")
    fun getUserById(id: UUID): GetUserResponse =
        findById(id).toGetResponse()

    /**
     * Enables or disables a user account through Keycloak and mirrors the result locally.
     *
     * @param id Identifier of the user whose enabled state should change.
     * @param request Target enabled state.
     * @return The updated user projection.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional
    @Tracked("Updating user enabled state")
    fun updateUserEnabledById(id: UUID, request: UpdateUserEnabledRequest): GetUserResponse {
        val user = findById(id)
        keycloakAdminClient.setUserEnabled(user.authId, request.enabled)
        user.enabled = request.enabled
        // Todo: map this to updateUserEnabledResponse or return null
        return userRepository.save(user).toGetResponse()
    }

    /**
     * Deletes a user by UUID.
     *
     * Projects managed by the user are left without a manager rather than being deleted. Clearing
     * the manager foreign key first is required because the user row is removed with a native
     * delete, which no cascade or `ON DELETE SET NULL` rule applies to.
     *
     * @param id Identifier of the user to delete.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional
    @Tracked("Deleting user by id")
    fun deleteUserById(id: UUID) {
        val authId = userRepository
            .findAuthIdById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }

        keycloakAdminClient.deleteUser(authId)
        projectRepository.clearManagerForUser(id)
        userRepository.deleteRolesByUserId(id)
        userRepository.deleteProjectionById(id)
    }

    /**
     * Deletes a user and returns the API response used by the admin controller.
     *
     * The deletion itself is handled by [deleteUserById] so the Keycloak deletion and
     * local projection cleanup stay in one place.
     *
     * @param id Identifier of the user to delete.
     * @return Confirmation DTO for the deleted user.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional
    @Tracked("Deleting user by id")
    fun deleteAdminUserById(id: UUID): DeleteUserResponse {
        deleteUserById(id)
        // Todo: Remove return
        return DeleteUserResponse(id = id)
    }

    private fun findById(id: UUID): User =
        userRepository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }

    private fun findByAuthId(authId: String): User =
        userRepository
            .findByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId: $authId not found") }
}

private fun Jwt.realmRoles(): Set<Role> {
    val realmAccess = claims["realm_access"] as? Map<*, *> ?: return emptySet()
    val roles = realmAccess["roles"] as? Collection<*> ?: return emptySet()

    return KeycloakRoleMapper.mapRealmRoles(roles.filterIsInstance<String>())
}
