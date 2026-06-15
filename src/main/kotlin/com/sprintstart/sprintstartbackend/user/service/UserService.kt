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

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getAllUsers(): List<GetUserResponse> =
        userRepository.findAll().map { it.toGetResponse() }

    @Transactional(readOnly = true)
    fun getMe(authId: String): GetUserResponse =
        findByAuthId(authId).toGetResponse()

    @Transactional
    fun patchMe(authId: String, request: PatchMeRequest): GetUserResponse {
        val user = findByAuthId(authId)
        request.workingArea?.let { user.workingArea = it }
        return userRepository.save(user).toGetResponse()
    }

    @Transactional(readOnly = true)
    fun getUserById(id: UUID): GetUserResponse =
        findById(id).toGetResponse()

    @Transactional
    fun updateUserById(id: UUID, request: UpdateUserRequest): UpdateUserResponse {
        val user = findById(id)
        user.workingArea = request.workingArea
        return userRepository.save(user).toUpdateResponse()
    }

    @Transactional
    fun patchUserById(id: UUID, request: PatchUserRequest): PatchUserResponse {
        val user = findById(id)
        request.workingArea?.let { user.workingArea = it }
        return userRepository.save(user).toPatchResponse()
    }

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

// TODO: add doc
