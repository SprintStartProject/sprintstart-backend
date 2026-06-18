package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
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
 * REST API for user self-service and user administration operations.
 *
 * The `/me` endpoints operate on the authenticated user resolved from the JWT subject.
 * The `/{id}` endpoints operate on an explicitly selected user.
 *
 * Business logic is delegated to [UserService].
 */
@Tag(
    name = "Users",
    description = "Endpoints for user profile lookup, update, patch, and deletion.",
)
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    /**
     * Returns all users.
     *
     * @return A list of all user profiles.
     */
    @Operation(
        summary = "Get all users",
        description = "Returns all user profiles.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Users returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access all users"),
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getAllUsers(): List<GetUserResponse> {
        return userService.getAllUsers()
    }

    /**
     * Returns the authenticated user's profile.
     *
     * @param jwt The authenticated JWT containing the caller subject.
     * @return The current user's profile.
     */
    @Operation(
        summary = "Get current user",
        description = "Returns the profile of the authenticated user identified by the JWT subject.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User found"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access own profile"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('USER')")
    fun getMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GetUserResponse {
        return userService.getMe(jwt.subject)
    }

    /**
     * Partially updates the authenticated user's profile.
     *
     * Only fields present in [request] are changed; omitted fields remain unchanged.
     *
     * @param request The partial update payload for the authenticated user.
     * @param jwt The authenticated JWT containing the caller subject.
     * @return The updated current user profile.
     */
    @Operation(
        summary = "Patch current user",
        description = "Partially updates the authenticated user's profile. Only provided fields are changed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update own profile"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PatchMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('USER')")
    fun patchMe(
        @Valid @RequestBody request: PatchMeRequest,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GetUserResponse =
        userService.patchMe(jwt.subject, request)

    /**
     * Returns a single user by UUID.
     *
     * @param id The UUID of the user to retrieve.
     * @return The matching user profile.
     */
    @Operation(
        summary = "Get user by id",
        description = "Returns a single user profile by UUID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User found"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this user"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getUserById(
        @Parameter(description = "UUID of the user to retrieve")
        @PathVariable id: UUID,
    ): GetUserResponse {
        return userService.getUserById(id)
    }

    /**
     * Replaces the editable fields of a user.
     *
     * All fields in [request] are applied as the new state for the targeted user.
     *
     * @param id The UUID of the user to update.
     * @param request The full update payload.
     * @return The updated user profile.
     */
    @Operation(
        summary = "Update user by id",
        description = "Replaces the editable fields of the specified user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update this user"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun updateUserById(
        @Parameter(description = "UUID of the user to update")
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserRequest,
    ): UpdateUserResponse {
        return userService.updateUserById(id, request)
    }

    /**
     * Partially updates a user by UUID.
     *
     * Only fields present in [request] are changed; omitted fields remain unchanged.
     *
     * @param id The UUID of the user to patch.
     * @param request The partial update payload.
     * @return The patched user profile.
     */
    @Operation(
        summary = "Patch user by id",
        description = "Partially updates the specified user. Only provided fields are changed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to patch this user"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun patchUserById(
        @Parameter(description = "UUID of the user to patch")
        @PathVariable id: UUID,
        @RequestBody request: PatchUserRequest,
    ): PatchUserResponse {
        return userService.patchUserById(id, request)
    }

    /**
     * Deletes a user by UUID.
     *
     * @param id The UUID of the user to delete.
     */
    @Operation(
        summary = "Delete user by id",
        description = "Deletes the specified user by UUID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "User deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete this user"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteUserById(
        @Parameter(description = "UUID of the user to delete")
        @PathVariable id: UUID,
    ) {
        userService.deleteUserById(id)
    }
}
