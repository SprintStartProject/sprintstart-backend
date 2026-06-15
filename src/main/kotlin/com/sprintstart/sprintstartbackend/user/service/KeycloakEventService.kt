package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.KeycloakEventRequest
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
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
    /**
     * Dispatches an incoming Keycloak event to the matching user synchronization flow.
     *
     * Unsupported event types are ignored after being logged to standard output.
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
                    updateUser(request) // This comes from an admin event
                }

                "UPDATE_PROFILE" -> {
                    updateUser(request) // This comes from a user event
                }

                "DELETE" -> {
                    deleteUser(request)
                }

                else -> {
                    println("Unknown event type: ${request.eventType}")
                }
            }
        }

        if (request.resourceType == "REALM_ROLE_MAPPING") {
            when (request.eventType) {
                "CREATE", "DELETE" -> {
                    updateUserRoles(request)
                }

                else -> {
                    println("Unknown event type: ${request.eventType}")
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
            workingArea = WorkingArea.NO_WORKING_AREA,
        )

        newUser.roles.addAll(filteredMappedRoles(request.realmRoles))

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
        val user = userRepository
            .findByAuthId(request.authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "username not found") }

        request.username?.let { user.username = it }
        request.email?.let { user.email = it }
        request.firstName?.let { user.firstname = it }
        request.lastName?.let { user.lastname = it }

        userRepository.save(user)
    }

    /**
     * Deletes the user identified by the Keycloak auth ID.
     *
     * @param request Delete event payload.
     * @throws ResponseStatusException When no user exists for the given auth ID.
     */
    fun deleteUser(request: KeycloakEventRequest) {
        val user = userRepository
            .findByAuthId(request.authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "username not found") }

        userRepository.delete(user)
    }

    /**
     * Adds mapped Keycloak realm roles to the existing user.
     *
     * Roles not recognized by this module are ignored.
     *
     * @param request Role mapping event payload.
     * @throws ResponseStatusException When no user exists for the given auth ID.
     */
    fun updateUserRoles(request: KeycloakEventRequest) {
        val user = userRepository
            .findByAuthId(request.authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "username not found") }

        user.roles.addAll(filteredMappedRoles(request.realmRoles))
    }

    /**
     * Maps Keycloak realm role names to roles known by the user module.
     *
     * Unknown realm roles are ignored.
     *
     * @param realmRoles Raw role names from Keycloak.
     * @return The subset of mapped roles recognized by this service.
     */
    fun filteredMappedRoles(realmRoles: Set<String>): Set<Role> {
        val roles = mutableSetOf<Role>()

        for (realmRole in realmRoles) {
            when (realmRole) {
                "user" -> roles.add(Role.USER)
                "project-manager" -> roles.add(Role.PM)
                "human-resources" -> roles.add(Role.HR)
                "admin" -> roles.add(Role.ADMIN)
            }
        }

        return roles
    }
}
