package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toPatchResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
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
     * Returns the user identified by the authentication subject.
     *
     * @param authId External authentication identifier from the JWT subject.
     * @return The matching user.
     * @throws ResponseStatusException When no user exists for the given auth ID.
     */
    @Transactional(readOnly = true)
    fun getMe(authId: String): GetUserResponse =
        findByAuthId(authId).toGetResponse()

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
        request.workingArea?.let { user.workingArea = it }
        return userRepository.save(user).toGetResponse()
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
     * Replaces the editable fields of a user.
     *
     * @param id Identifier of the user to update.
     * @param request Full update payload.
     * @return The updated user.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional
    fun updateUserById(id: UUID, request: UpdateUserRequest): UpdateUserResponse {
        val user = findById(id)
        user.workingArea = request.workingArea
        return userRepository.save(user).toUpdateResponse()
    }

    /**
     * Partially updates a user's editable fields.
     *
     * Omitted fields remain unchanged.
     *
     * @param id Identifier of the user to patch.
     * @param request Partial update payload.
     * @return The patched user.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional
    fun patchUserById(id: UUID, request: PatchUserRequest): PatchUserResponse {
        val user = findById(id)
        request.workingArea?.let { user.workingArea = it }
        return userRepository.save(user).toPatchResponse()
    }

    /**
     * Deletes a user by UUID.
     *
     * @param id Identifier of the user to delete.
     * @throws ResponseStatusException When no user exists for the given ID.
     */
    @Transactional
    fun deleteUserById(id: UUID) {
        val user = findById(id)
        userRepository.delete(user)
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
