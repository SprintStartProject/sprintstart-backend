package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.SyncUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toPatchResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

/**
 * Service responsible for handling user-related business logic.
 *
 * Provides operations for creating, retrieving, updating, partially updating,
 * and deleting users. The service acts as the application layer between the
 * REST controller and the user repository.
 *
 * @property userRepository Repository used to access and persist user entities.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
) {
    /**
     * Creates a new user from the provided request data.
     *
     * The new user is persisted in the database and then converted into a
     * response object for the API layer.
     *
     * @param request The data required to create the user.
     * @return The response containing the created user's data.
     */
    @Transactional
    fun createUser(request: CreateUserRequest): CreateUserResponse {
        validateAuthIdAvailability(request.authId)

        val user: User =
            User(
                authId = request.authId,
                username = request.username,
                firstname = request.firstname,
                lastname = request.lastname,
                workingArea = request.workingArea,
            )

        return userRepository.save(user).toCreateResponse()
    }

    /**
     * Retrieves all existing users.
     *
     * @return A list containing the response data of all users.
     */
    @Transactional(readOnly = true)
    fun getAllUsers(): List<GetUserResponse> {
        return userRepository.findAll().map {
            it.toGetResponse()
        }
    }

    /**
     * Retrieves a user by their unique identifier.
     *
     * @param id The unique identifier of the user to retrieve.
     * @return The response data of the found user.
     * @throws ResponseStatusException If no user with the given identifier exists.
     */
    @Transactional(readOnly = true)
    fun getUserById(id: UUID): GetUserResponse {
        return userRepository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }
            .toGetResponse()
    }

    @Transactional(readOnly = true)
    fun getUserByAuthId(authId: String): GetUserResponse {
        return userRepository
            .findByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId: $authId not found") }
            .toGetResponse()
    }

    /**
     * Updates an existing user with the provided data.
     *
     * This method replaces all editable user fields with the values from the
     * given update request before the updated user is persisted.
     *
     * @param id The unique identifier of the user to update.
     * @param request The complete data used to update the user.
     * @return The response data of the updated user.
     * @throws ResponseStatusException If no user with the given identifier exists.
     */
    @Transactional
    fun updateUserById(id: UUID, request: UpdateUserRequest): UpdateUserResponse {
        val user: User = userRepository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }

        user.username = request.username
        user.firstname = request.firstname
        user.lastname = request.lastname
        user.workingArea = request.workingArea

        return userRepository.save(user).toUpdateResponse()
    }

    /**
     * Partially updates an existing user with the provided data.
     *
     * Only fields that are present in the patch request are applied to the
     * existing user. Fields with `null` values are left unchanged before the
     * patched user is persisted.
     *
     * @param id The unique identifier of the user to patch.
     * @param request The partial data used to update the user.
     * @return The response data of the patched user.
     * @throws ResponseStatusException If no user with the given identifier exists.
     */
    @Transactional
    fun patchUserById(id: UUID, request: PatchUserRequest): PatchUserResponse {
        val user: User = userRepository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }

        request.username?.let { user.username = it }
        request.firstname?.let { user.firstname = it }
        request.lastname?.let { user.lastname = it }
        request.workingArea?.let { user.workingArea = it }

        return userRepository.save(user).toPatchResponse()
    }

    @Transactional
    fun syncUser(request: SyncUserRequest): GetUserResponse {
        val existingUser = userRepository.findByAuthId(request.authId).getOrNull()

        if (existingUser != null) {
            existingUser.username = request.username
            existingUser.firstname = request.firstname
            existingUser.lastname = request.lastname

            return userRepository.save(existingUser).toGetResponse()
        }

        val user = User(
            authId = request.authId,
            username = request.username,
            firstname = request.firstname,
            lastname = request.lastname,
            workingArea = com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea.NO_WORKING_AREA,
        )

        return userRepository.save(user).toGetResponse()
    }

    /**
     * Deletes a user by their unique identifier.
     *
     * @param id The unique identifier of the user to delete.
     * @throws ResponseStatusException If no user with the given identifier exists.
     */
    @Transactional
    fun deleteUserById(id: UUID) {
        val user: User = userRepository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }

        userRepository.delete(user)
    }

    private fun validateAuthIdAvailability(authId: String) {
        if (userRepository.existsByAuthId(authId)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "User with authId: $authId already exists",
            )
        }
    }
}
