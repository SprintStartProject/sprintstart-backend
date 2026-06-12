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

@Service
class KeycloakEventService(
    private val userRepository: UserRepository,
) {
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

    fun deleteUser(request: KeycloakEventRequest) {
        val user = userRepository
            .findByAuthId(request.authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "username not found") }

        userRepository.delete(user)
    }

    fun updateUserRoles(request: KeycloakEventRequest) {
        val user = userRepository
            .findByAuthId(request.authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "username not found") }

        user.roles.addAll(filteredMappedRoles(request.realmRoles))
    }

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
