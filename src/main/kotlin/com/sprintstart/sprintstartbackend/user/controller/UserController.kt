package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for managing users.
 *
 * Provides endpoints for reading, updating and partially updating users.
 * All endpoints are exposed under the `/api/v1/users` base path.
 *
 * @property userService Service used to handle user-related business logic.
 */
@Tag(
    name = "Users",
    description = "Endpoints for reading and updating users.",
)
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
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
        summary = "Get current user",
        description = "Returns the authenticated user based on JWT token.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User found"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMe(@AuthenticationPrincipal jwt: Jwt): GetUserResponse {
        return userService.getMe(jwt.subject)
    }

    @Operation(
        summary = "Patch current user",
        description = "Partially updates the authenticated user. Only provided fields are changed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PatchMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun patchMe(
        @Valid @RequestBody request: PatchMeRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): GetUserResponse =
        userService.patchMe(jwt.subject, request)

    @Operation(
        summary = "Get user by id",
        description = "Returns a single user by their UUID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User found"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getUserById(@PathVariable id: UUID): GetUserResponse {
        return userService.getUserById(id)
    }

    @Operation(
        summary = "Update user by id",
        description = "Updates the user with the given UUID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun updateUserById(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserRequest,
    ): UpdateUserResponse {
        return userService.updateUserById(id, request)
    }

    @Operation(
        summary = "Patch user by id",
        description = "Partially updates the user with the given UUID. Only provided fields are changed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun patchUserById(
        @PathVariable id: UUID,
        @RequestBody request: PatchUserRequest,
    ): PatchUserResponse {
        return userService.patchUserById(id, request)
    }

    @Operation(
        summary = "Delete user by id",
        description = "Deletes the user with the given UUID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "User deleted successfully"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserById(@PathVariable id: UUID) {
        userService.deleteUserById(id)
    }
}

// TODO: update doc
