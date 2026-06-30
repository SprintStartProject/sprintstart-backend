package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.events.UserCreatedEvent
import com.sprintstart.sprintstartbackend.user.model.dto.DeleteUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserEnabledRequest
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
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
    private val eventPublisher: ApplicationEventPublisher,
    private val keycloakAdminClient: KeycloakAdminClient,
) {
    /**
     * Returns all persisted users.
     *
     * @return All users mapped to controller response DTOs.
     */
    @Transactional(readOnly = true)
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
    fun getMe(jwt: Jwt): GetUserResponse {
        val user = userRepository.findByAuthId(jwt.subject).orElseGet {
            val newUser = User(
                authId = jwt.subject,
                username = jwt.getClaimAsString("preferred_username") ?: jwt.subject,
                email = jwt.getClaimAsString("email"),
                firstname = jwt.getClaimAsString("given_name") ?: "Unknown",
                lastname = jwt.getClaimAsString("family_name") ?: "User",
                roles = mutableSetOf(Role.USER),
            )
            val savedUser = userRepository.save(newUser)
            eventPublisher.publishEvent(UserCreatedEvent(savedUser.id))
            savedUser
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
    fun patchMe(authId: String, request: PatchMeRequest): GetUserResponse {
        val user = findByAuthId(authId)

        keycloakAdminClient.updateUserProfile(
            authId = user.authId,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
        )

        request.email?.let { user.email = it }
        request.firstName?.let { user.firstname = it }
        request.lastName?.let { user.lastname = it }
        request.profileIcon?.let { user.profileIcon = it }

        return userRepository.save(user).toGetResponse()
    }

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
    fun patchAdminUserById(id: UUID, request: PatchUserRequest): GetUserResponse {
        val user = findById(id)

        keycloakAdminClient.updateUserProfile(
            authId = user.authId,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
        )
        request.permissionGroup?.let { keycloakAdminClient.setPermissionGroup(user.authId, it) }

        request.email?.let { user.email = it }
        request.firstName?.let { user.firstname = it }
        request.lastName?.let { user.lastname = it }

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
     * @param id Identifier of the user to delete.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional
    fun deleteUserById(id: UUID) {
        val authId = userRepository
            .findAuthIdById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }

        keycloakAdminClient.deleteUser(authId)
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
