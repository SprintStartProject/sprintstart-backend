package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.config.KeycloakRoleMapper
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.KeycloakEventRequest
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Synchronizes user data in response to Keycloak events.
 *
 * The service translates Keycloak webhook payloads into user module updates such as
 * user creation, profile changes, deletion, and role synchronization.
 */
@Suppress("ThrowsCount")
@Service
class KeycloakEventService(
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Dispatches an incoming Keycloak event to the matching user synchronization flow.
     *
     * Unsupported event types are ignored after being logged through the application logger.
     *
     * @param request Incoming Keycloak event payload.
     */
    @Transactional
    fun handleEvent(request: KeycloakEventRequest) {
        if (request.resourceType == "USER") {
            when (request.eventType) {
                "REGISTER" -> {
                    registerUser(request)
                }

                "UPDATE" -> {
                    updateUser(request)
                }

                "UPDATE_PROFILE" -> {
                    updateUser(request)
                }

                "DELETE" -> {
                    deleteUser(request)
                }

                else -> {
                    logger.warn("Unknown Keycloak USER event type: {}", request.eventType)
                }
            }
        }

        if (request.resourceType == "REALM_ROLE_MAPPING") {
            when (request.eventType) {
                "CREATE" -> {
                    syncPermissionGroups(request)
                }

                "DELETE" -> {
                    syncPermissionGroups(request)
                }

                else -> {
                    logger.warn("Unknown Keycloak REALM_ROLE_MAPPING event type: {}", request.eventType)
                }
            }
        }
    }

    /**
     * Creates a new user from a Keycloak registration event.
     *
     * The user starts with [WorkingArea.NO_WORKING_AREA] and receives the subset of
     * realm roles that map to roles known by this module.
     *
     * @param request Registration event payload.
     * @throws ResponseStatusException When required user fields are missing.
     */
    fun registerUser(request: KeycloakEventRequest) {
        val newUser = User(
            authId = request.authId,
            username = request.username
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "username must not be null"),
            email = request.email,
            firstname = request.firstName
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "firstName must not be null"),
            lastname = request.lastName
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "lastName must not be null"),
            enabled = request.enabled ?: true,
            workingArea = WorkingArea.NO_WORKING_AREA,
        )

        newUser.roles.addAll(mappedRoles(request.realmRoles))

        userRepository.save(newUser)
    }

    /**
     * Updates an existing user from a Keycloak profile or admin update event.
     *
     * Only fields present in the payload are changed.
     *
     * @param request Update event payload.
     * @throws ResponseStatusException When no user exists for the given auth ID.
     */
    fun updateUser(request: KeycloakEventRequest) {
        val user = findLockedByAuthId(request.authId)

        request.username?.let { user.username = it }
        request.email?.let { user.email = it }
        request.firstName?.let { user.firstname = it }
        request.lastName?.let { user.lastname = it }
        request.enabled?.let { user.enabled = it }
        if (request.realmRoles.isNotEmpty()) {
            syncPermissionGroups(user, mappedRoles(request.realmRoles))
        }

        userRepository.save(user)
    }

    /**
     * Deletes the user identified by the Keycloak auth ID.
     *
     * @param request Delete event payload.
     */
    fun deleteUser(request: KeycloakEventRequest) {
        val user = userRepository.findLockedByAuthId(request.authId).orElse(null) ?: return

        userRepository.delete(user)
    }

    /**
     * Replaces local permission groups with the Keycloak event role snapshot.
     *
     * Roles not recognized by this module are ignored.
     *
     * @param request Role mapping event payload.
     * @throws ResponseStatusException When no user exists for the given auth ID.
     */
    fun syncPermissionGroups(request: KeycloakEventRequest) {
        syncPermissionGroups(findLockedByAuthId(request.authId), mappedRoles(request.realmRoles))
    }

    private fun syncPermissionGroups(user: User, roles: Set<Role>) {
        user.roles.removeAll { it !in roles }
        roles
            .filter { it !in user.roles }
            .forEach { user.roles.add(it) }
    }

    private fun mappedRoles(realmRoles: Set<String>): Set<Role> =
        KeycloakRoleMapper.mapRealmRoles(realmRoles)

    private fun findLockedByAuthId(authId: String): User =
        userRepository
            .findLockedByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "username not found") }
}
