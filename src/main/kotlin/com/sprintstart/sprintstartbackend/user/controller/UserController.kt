package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for managing users.
 *
 * Provides endpoints for creating, reading, updating, partially updating, and deleting users.
 * All endpoints are exposed under the `/api/v1/users` base path.
 *
 * @property userService Service used to handle user-related business logic.
 */
@Tag(
    name = "Users",
    description = "Endpoints for creating, reading, updating and deleting users.",
)
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    @Operation(
        summary = "Create a user",
        description = "Creates a new user and returns the created user data.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "User created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@Valid @RequestBody request: CreateUserRequest): CreateUserResponse {
        return userService.createUser(request)
    }

    @Operation(
        summary = "Get all users",
        description = "Returns a list of all users.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Users returned successfully"),
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getAllUsers(): List<GetUserResponse> {
        return userService.getAllUsers()
    }

    @Operation(
        summary = "Get user by ID",
        description = "Returns a single user by their unique ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User found"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @GetMapping("/{userId}")
    @ResponseStatus(HttpStatus.OK)
    fun getUserById(@PathVariable userId: UUID): GetUserResponse {
        return userService.getUserById(userId)
    }

    @Operation(
        summary = "Patch user by ID",
        description = "Partially updates the user with the given ID. Only provided fields are changed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.OK)
    fun updateUserById(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: UpdateUserRequest,
    ): UpdateUserResponse {
        return userService.updateUserById(userId, request)
    }

    @Operation(
        summary = "Patch user by ID",
        description = "Partially updates the user with the given ID. Only provided fields are changed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PatchMapping("/{userId}")
    @ResponseStatus(HttpStatus.OK)
    fun patchUserById(
        @PathVariable userId: UUID,
        @RequestBody request: PatchUserRequest,
    ): PatchUserResponse {
        return userService.patchUserById(userId, request)
    }

    @Operation(
        summary = "Delete user by ID",
        description = "Deletes the user with the given ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "User deleted successfully"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserById(@PathVariable userId: UUID) {
        userService.deleteUserById(userId)
    }
}
