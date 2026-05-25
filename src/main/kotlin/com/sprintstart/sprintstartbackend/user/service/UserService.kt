package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.user.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class UserService (
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createUser(request: CreateUserRequest): CreateUserResponse {
        val user: User = User(
            username = request.username,
            firstname = request.firstname,
            lastname = request.lastname,
            workingArea = request.workingArea
        )

        return userRepository.save(user).toCreateResponse()
    }

    @Transactional
    fun getAllUsers(): List<GetUserResponse> {
        return userRepository.findAll().map {
            it.toGetResponse()
        }
    }

    @Transactional
    fun getUserById(id: UUID): GetUserResponse {
        return userRepository.findById(id)
            .orElseThrow{ ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }
            .toGetResponse()
    }

    @Transactional
    fun updateUserById(id: UUID, request: UpdateUserRequest): UpdateUserResponse {
        val user: User = userRepository.findById(id).orElseThrow{ ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }

        user.username = request.username
        user.firstname = request.firstname
        user.lastname = request.lastname
        user.primaryRole = request.primaryRole
        user.secondaryRole = request.secondaryRole
        user.workingArea = request.workingArea

        return userRepository.save(user).toUpdateResponse()
    }

    @Transactional
    fun deleteUserById(id: UUID) {
        val user: User = userRepository.findById(id)
            .orElseThrow{ ResponseStatusException(HttpStatus.NOT_FOUND, "User with id: $id not found") }

        userRepository.delete(user)
    }
}