package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController (
    private val userService: UserService
) {

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody request: CreateUserRequest): CreateUserResponse {
        return userService.createUser(request)
    }

    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    fun getAllUsers(): List<GetUserResponse> {
        return userService.getAllUsers()
    }

    @GetMapping("/{userId}")
    @ResponseStatus(HttpStatus.OK)
    fun getUserById(@PathVariable userId: UUID): GetUserResponse {
        return userService.getUserById(userId)
    }

    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.OK)
    fun updateUserById(
        @PathVariable userId:UUID,
        @RequestBody request : UpdateUserRequest
    ): UpdateUserResponse {
        return userService.updateUserById(userId, request);
    }

//    Todo: add Patch for role

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserById(@PathVariable userId: UUID) {
        userService.deleteUserById(userId)
    }
}